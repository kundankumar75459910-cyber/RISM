# Retail Inventory Management System (RIMS)

A web application for a small retail business to manage products, stock levels, suppliers,
purchase orders, sales, and inventory movements from one place.

- **Frontend:** React 19 + Vite (plain CSS, no UI framework)
- **Backend:** Java 17+ / Spring Boot 3 (Spring Data JPA, Spring Security with JWT, Bean Validation)
- **Database:** MySQL 8 (schema in `backend/src/main/resources/schema.sql`, sample data loaded on first start)
- **Forecasting:** Python 3.10+ (numpy, pandas, Flask), in `ml/`
- **Build tools:** Maven (backend), npm (frontend)

> **Verification status.** This codebase was reviewed statically and its frontend bundle was
> checked for syntax and unresolved imports. It has **not** yet been compiled against Maven
> dependencies, run against a live MySQL database, or exercised in a browser. Run the steps
> below and the test suite before relying on it. See [Status and known gaps](#status-and-known-gaps).

---

## Features

| Area | What it does |
|---|---|
| Dashboard | Totals from the database: products, units in stock, low-stock count, suppliers, sales count and value, inventory value. Charts for stock by category and 14-day sales trend. Recent movements and low-stock list. |
| Products | Add, edit, search by name or SKU, filter by category or low stock. SKUs and barcodes are unique (case-insensitive). Optional product image (an http(s) link). Delete is a soft delete, so history is preserved. |
| Locations | Admin: add warehouses and stores, close an empty one. Everyone can see them. Stock, purchases and sales each belong to a location. |
| Inventory | Stock per product and per location, low-stock alerts (per product reorder level), manual adjustments with a reason, transfers between locations, and a full transaction history. |
| Suppliers | Add, view, edit, and delete suppliers (name, contact number, email, address). |
| Purchases | Create orders from one or more products for a delivery location, track Pending / Received / Cancelled, receive an order to add stock at that location, cancel a pending order (Admin). |
| Sales | Record sales from a chosen location, with barcode scanning (a USB/Bluetooth scanner types the code and presses Enter). Stock is validated against that location, totals are computed on the server. |
| Reports | Current inventory, low stock, sales history, purchase history, and inventory transactions. Each supports filters and CSV export. Admins also get an Accounting / ERP export tab (CSV feeds for sales, purchases and products). |
| Forecast | Demand forecast and reorder recommendations per product, from sales history (separate Python service in `ml/`). Optional: the rest of the app works without it. |
| Users, categories | Admin only: manage users (Admin / Staff) and product categories. |
| Auth | JWT login. Admin has full access. Staff can run daily operations but cannot delete products, manage suppliers or categories, cancel purchases, or manage users. Enforced in the backend. |

### Business rules and where they are enforced

| # | Rule | Implementation |
|---|---|---|
| 1 | SKUs are unique | Service check plus unique index `uk_products_sku` |
| 2 | Stock cannot go negative | `InventoryService.changeStock` plus `CHECK (quantity >= 0)` |
| 3 | A sale cannot exceed available stock | `SaleService.create` checks each product under a row lock |
| 4 | Receiving increases stock exactly once | `PurchaseService.receive` locks the order and requires `PENDING` |
| 5 | Cancelling an unreceived order does not change stock | `PurchaseService.cancel` only changes status |
| 6 | Every stock change writes a transaction | All stock changes go through `InventoryService.changeStock` |
| 7 | Failed operations leave no partial changes | Purchases, sales, and product creation are single transactions |
| 8 | Deleting a product keeps history | Soft delete (`products.active = false`); foreign keys are `RESTRICT` |
| 9 | Low-stock uses each product's reorder level | `quantity <= reorder_level` per product |
| 10 | Money uses decimal arithmetic | `BigDecimal` on the server; `DECIMAL` columns; integer cents on the client |
| 11 | Total stock always equals the sum over locations | Only `InventoryService.changeStock` changes either figure, in one transaction |
| 12 | A sale or transfer cannot take more than the location holds | Row locks on product then stock level; `CHECK (quantity >= 0)` on `stock_levels` |
| 13 | A location that still holds stock cannot be closed | `LocationService.update` |
| 14 | Barcodes are unique | Service check plus unique index `uk_products_barcode` |

---

## Prerequisites

- **JDK 17 or newer** (JDK 21 works)
- **Maven 3.9+**
- **Node.js 18.18+** (or 20+) and npm
- **MySQL 8.0+** running locally (or a reachable server)

---

## 1. Database

The application creates the `rims_db` database automatically if the MySQL user is allowed to
(`createDatabaseIfNotExist=true`). Otherwise create it yourself:

```sql
CREATE DATABASE rims_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'rims'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON rims_db.* TO 'rims'@'localhost';
```

On first start the backend:

1. runs `schema.sql` (idempotent `CREATE TABLE IF NOT EXISTS`), and
2. if the `users` table is empty, loads sample data: 2 users, 6 categories, 6 suppliers, 3 locations,
   18 products with barcodes, 8 purchase orders (4 received, 3 pending, 1 cancelled), some stock
   transfers, and about two weeks of sales.

> **Upgrading from an earlier copy of this project:** the schema now has locations, per-location
> stock and barcode/image columns. `CREATE TABLE IF NOT EXISTS` does not alter old tables, so for a
> development database created earlier run `DROP DATABASE rims_db;` and start again. There is no
> migration script.

To run the schema by hand instead, use `backend/src/main/resources/schema.sql`. To turn off the
sample data, set `SEED_ENABLED=false`.

### Database configuration

All settings can be overridden with environment variables. Defaults are in
`backend/src/main/resources/application.properties`.

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/rims_db?...` | JDBC URL |
| `DB_USERNAME` | `root` | Database user |
| `DB_PASSWORD` | `root` | Database password |
| `JWT_SECRET` | dev value | Signing key, at least 32 characters. **Change it in any real deployment.** |
| `JWT_EXPIRATION_MINUTES` | `480` | Token lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Allowed frontend origins |
| `SEED_ENABLED` | `true` | Load sample data into an empty database |
| `SERVER_PORT` | `8080` | Backend port |

---

## 2. Run the backend

```bash
cd backend
DB_USERNAME=rims DB_PASSWORD=change-me mvn spring-boot:run
```

The API is served at `http://localhost:8080/api`.

Run the automated tests (business-rule tests use an in-memory H2 database in MySQL mode):

```bash
cd backend
mvn test
```

To run the application without MySQL, use the in-memory H2 profile (data is lost on restart):

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

The H2 profile has not been verified; the schema uses MySQL-specific clauses such as `ENGINE=InnoDB`,
which H2 may not accept. If it fails at startup, use MySQL.

To build a runnable jar:

```bash
cd backend
mvn -DskipTests package
java -jar target/rims-backend-1.0.0.jar
```

---

## 3. Run the frontend

```bash
cd frontend
cp .env.example .env        # optional: change VITE_API_URL, VITE_ML_URL, VITE_CURRENCY, VITE_LOCALE
npm install
npm run dev
npm test                    # unit tests for the money and CSV helpers
```

Open **http://localhost:5173**. For a production build, run `npm run build`; the output is in
`frontend/dist/`.

---

## 4. Run the forecasting service (optional)

The **Forecast** page needs the Python service. The rest of the app works without it.

```bash
cd ml
pip install -r requirements.txt
python -m rims_ml.server          # http://localhost:8000
```

See [`ml/README.md`](ml/README.md) for how it works and its limits. To try it without the backend:
`python -m rims_ml --demo`.

---

## 5. Sample logins

| Role | Username | Password | Can do |
|---|---|---|---|
| Admin | `admin` | `admin123` | Everything |
| Staff | `staff` | `staff123` | Products, inventory, purchases (create and receive), sales, reports |

Change these passwords (or delete the accounts) before any real use. The sample passwords are
written into the seeder in `backend/src/main/java/com/rims/seed/DataSeeder.java`.

---

## API overview

All endpoints are under `/api` and require `Authorization: Bearer <token>` except login.
List endpoints accept `page` (0-based) and `size` (max 1000). Errors return JSON with a `message`
and, for validation failures, `fieldErrors`.

| Resource | Endpoints |
|---|---|
| Auth | `POST /auth/login`, `GET /auth/me` |
| Dashboard | `GET /dashboard` |
| Products | `GET /products`, `GET /products/lookup/{barcode-or-sku}`, `GET /products/{id}`, `POST /products`, `PUT /products/{id}`, `DELETE /products/{id}` (Admin) |
| Categories | `GET /categories`, `POST`, `PUT /{id}`, `DELETE /{id}` (Admin for writes) |
| Suppliers | `GET /suppliers`, `GET /suppliers/{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` (Admin for writes) |
| Locations | `GET /locations`, `POST /locations`, `PUT /locations/{id}` (Admin for writes) |
| Inventory | `GET /inventory`, `GET /inventory/low-stock`, `GET /inventory/transactions` (filter by `locationId`), `GET /inventory/stock/{productId}`, `GET /inventory/locations/{id}/stock`, `POST /inventory/adjust`, `POST /inventory/transfer` |
| Integration | `GET /integration/sales.csv`, `/purchases.csv`, `/products.csv` (Admin only; `from`/`to` dates) |
| Purchases | `GET /purchases`, `GET /purchases/{id}`, `POST /purchases` (with `locationId`), `POST /purchases/{id}/receive`, `POST /purchases/{id}/cancel` (Admin) |
| Sales | `GET /sales`, `GET /sales/{id}`, `POST /sales` (with `locationId`) |
| Users | `GET /users`, `POST`, `PUT /{id}` (Admin only) |

---

## Project layout

```
rims/
├── backend/                     Spring Boot application (Maven)
│   ├── pom.xml
│   └── src/main/java/com/rims/
│       ├── controller/          REST endpoints (no business logic)
│       ├── service/             Business rules and transactions
│       ├── repository/          Spring Data JPA repositories
│       ├── entity/              JPA entities and enums
│       ├── dto/                 Request and response records with validation
│       ├── security/            JWT filter and token service
│       ├── config/              Security and CORS configuration
│       ├── exception/           Centralised error handling
│       └── seed/                Sample data loader
│   └── src/main/resources/      application.properties, schema.sql
├── ml/                          Python forecasting service and tests
├── frontend/                    React application (Vite)
│   └── src/
│       ├── pages/               One file per screen
│       ├── components/          Layout, forms, modals, charts
│       ├── context/             Auth and toast state
│       ├── api.js               HTTP client with token handling
│       └── styles.css
└── README.md
```

---

## Status and known gaps

**Implemented as specified in the build brief**, with the caveats above (not yet compiled or run).

**Verified:** the Python forecasting service (`ml/`) has 29 passing tests and was run on synthetic data, but not against the real RIMS API. The frontend helper tests (`npm test`) pass and the frontend bundles without errors. The Java sources parse cleanly. The backend tests (`mvn test`, including the new location, transfer, barcode and export tests) were written but **never run**, because dependencies could not be downloaded in the build environment.

**Charter items and how far they go:**

- **Multiple warehouses or stores:** implemented (locations, per-location stock, transfers, per-location sales and receiving).
- **Barcodes:** a keyboard-style scanner works in the sale form; products also have a lookup endpoint. There is no camera scanning.
- **RFID:** not implemented.
- **ERP and accounting integration:** CSV export files only (Admin). There is no live connector or API push to any specific product.
- **Product images:** stored as a link to an image hosted elsewhere. There is no file upload.

**Other limits to know about:**

- Sample sales are generated with random data for demonstration. Their dates are back-dated, so
  reports look realistic on a fresh database.
- Tokens are stateless JWTs. There is no refresh token and no server-side revocation; logging out
  only clears the token in the browser.
- The frontend hides buttons a user may not use, but the backend enforces every rule.
