import { useState } from 'react';
import { api, downloadFile } from '../api';
import { useToast } from '../context/ToastContext';
import { useAuth } from '../context/AuthContext';
import { useClampPage, useLocations, useQuery } from '../hooks';
import LocationSelect from '../components/LocationSelect';
import { Badge, DataState, EmptyState, PageHeader, Pagination, Tabs } from '../components/ui';
import { cents, dateTime, downloadCsv, fromCents, money, num, TX_LABELS } from '../utils';

const TABS = [
  { key: 'inventory', label: 'Current inventory' },
  { key: 'low', label: 'Low stock' },
  { key: 'sales', label: 'Sales history' },
  { key: 'purchases', label: 'Purchase history' },
  { key: 'transactions', label: 'Inventory transactions' },
];
const ACCOUNTING_TAB = { key: 'accounting', label: 'Accounting / ERP export' };
const PAGE_SIZE = 10;
const EXPORT_PAGE = 1000;     // server maximum page size
const EXPORT_MAX_ROWS = 20000;

/** Fetches every page of a paged endpoint (for CSV export). Array endpoints return as-is. */
async function fetchAllRows(path, params) {
  const first = await api.get(path, { ...params, page: 0, size: EXPORT_PAGE });
  if (Array.isArray(first)) return first;
  const rows = [...first.content];
  for (let p = 1; p < first.totalPages && rows.length < EXPORT_MAX_ROWS; p++) {
    const next = await api.get(path, { ...params, page: p, size: EXPORT_PAGE });
    rows.push(...next.content);
  }
  return rows;
}

export default function Reports() {
  const { isAdmin } = useAuth();
  const [tab, setTab] = useState('inventory');
  const tabs = isAdmin ? [...TABS, ACCOUNTING_TAB] : TABS;
  return (
    <>
      <PageHeader title="Reports" subtitle="Filter, review and export stock, sales, purchases and inventory movements." />
      <Tabs tabs={tabs} active={tab} onChange={setTab} />
      <div className="report-panel">
        {tab === 'inventory' && <InventoryReport />}
        {tab === 'low' && <LowStockReport />}
        {tab === 'sales' && <SalesReport />}
        {tab === 'purchases' && <PurchasesReport />}
        {tab === 'transactions' && <TransactionsReport />}
        {tab === 'accounting' && isAdmin && <AccountingExport />}
      </div>
    </>
  );
}

/** Shared export button: fetches all matching rows and downloads them as CSV. */
function ExportButton({ filename, headers, load, toRow, disabled }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  async function run() {
    setBusy(true);
    try {
      const rows = (await load()).map(toRow);
      if (rows.length === 0) { toast.error('Nothing to export for these filters'); return; }
      downloadCsv(filename, headers, rows);
      toast.success(`Exported ${num(rows.length)} rows`);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }
  return <button className="btn" onClick={run} disabled={busy || disabled}>{busy ? 'Exporting…' : 'Export CSV'}</button>;
}

function DateFilters({ from, to, setFrom, setTo, onAny }) {
  return (
    <>
      <label className="inline-field">From
        <input type="date" value={from} onChange={(e) => { setFrom(e.target.value); onAny(); }} aria-label="From date" />
      </label>
      <label className="inline-field">To
        <input type="date" value={to} onChange={(e) => { setTo(e.target.value); onAny(); }} aria-label="To date" />
      </label>
    </>
  );
}

function CategoryFilter({ value, onChange }) {
  const categories = useQuery(() => api.get('/categories'), []);
  return (
    <select aria-label="Filter by category" value={value} onChange={(e) => onChange(e.target.value)}>
      <option value="">All categories</option>
      {(categories.data || []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
    </select>
  );
}

function InventoryReport() {
  const [categoryId, setCategoryId] = useState('');
  const [page, setPage] = useState(0);
  const query = useQuery(() => api.get('/inventory', { categoryId, page, size: PAGE_SIZE }), [categoryId, page]);
  useClampPage(query, page, setPage);
  const params = { categoryId };
  return (
    <section className="card flush">
      <div className="toolbar report-toolbar">
        <CategoryFilter value={categoryId} onChange={(v) => { setCategoryId(v); setPage(0); }} />
        <div className="grow" />
        <ExportButton filename="current-inventory.csv"
          headers={['SKU', 'Product', 'Category', 'Price', 'In stock', 'Reorder level', 'Stock value']}
          load={() => fetchAllRows('/inventory', params)}
          toRow={(p) => [p.sku, p.name, p.categoryName, p.price, p.quantity, p.reorderLevel, fromCents(cents(p.price) * p.quantity)]} />
      </div>
      <DataState query={query} empty={<EmptyState title="No products in this category" />}>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Product</th><th>Category</th><th className="num">Price</th><th className="num">In stock</th><th className="num">Reorder at</th><th className="num">Stock value</th></tr></thead>
            <tbody>
              {query.data?.content.map((p) => (
                <tr key={p.id}>
                  <td><strong>{p.name}</strong><small className="sub">{p.sku}</small></td>
                  <td>{p.categoryName}</td>
                  <td className="num">{money(p.price)}</td>
                  <td className="num">{num(p.quantity)} {p.quantity === 0 ? <Badge tone="red">Out</Badge> : p.lowStock ? <Badge tone="amber">Low</Badge> : null}</td>
                  <td className="num">{num(p.reorderLevel)}</td>
                  <td className="num">{money(fromCents(cents(p.price) * p.quantity))}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </DataState>
      <Pagination data={query.data} onChange={setPage} />
    </section>
  );
}

function LowStockReport() {
  const query = useQuery(() => api.get('/inventory/low-stock'), []);
  return (
    <section className="card flush">
      <div className="toolbar report-toolbar">
        <p className="muted grow">Products at or below their own reorder level, most urgent first.</p>
        <ExportButton filename="low-stock.csv"
          headers={['SKU', 'Product', 'Category', 'In stock', 'Reorder level', 'Shortfall']}
          load={() => fetchAllRows('/inventory/low-stock', {})}
          toRow={(p) => [p.sku, p.name, p.categoryName, p.quantity, p.reorderLevel, Math.max(p.reorderLevel - p.quantity, 0)]} />
      </div>
      <DataState query={query} empty={<EmptyState title="All stock levels are healthy" hint="No product is at or below its reorder level." />}>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Product</th><th>Category</th><th className="num">In stock</th><th className="num">Reorder at</th><th className="num">Shortfall</th></tr></thead>
            <tbody>
              {query.data?.map((p) => (
                <tr key={p.id}>
                  <td><strong>{p.name}</strong><small className="sub">{p.sku}</small></td>
                  <td>{p.categoryName}</td>
                  <td className="num">{num(p.quantity)} {p.quantity === 0 ? <Badge tone="red">Out</Badge> : null}</td>
                  <td className="num">{num(p.reorderLevel)}</td>
                  <td className="num">{num(Math.max(p.reorderLevel - p.quantity, 0))}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </DataState>
    </section>
  );
}

function SalesReport() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const query = useQuery(() => api.get('/sales', { from, to, page, size: PAGE_SIZE }), [from, to, page]);
  useClampPage(query, page, setPage);
  const params = { from, to };
  return (
    <section className="card flush">
      <div className="toolbar report-toolbar">
        <DateFilters from={from} to={to} setFrom={setFrom} setTo={setTo} onAny={() => setPage(0)} />
        <div className="grow" />
        <ExportButton filename="sales-history.csv"
          headers={['Sale number', 'Date', 'Customer', 'Location', 'Items', 'Total']}
          load={() => fetchAllRows('/sales', params)}
          toRow={(s) => [s.saleNumber, s.saleDate, s.customerName || 'Walk-in', s.locationName, s.items.reduce((n, i) => n + i.quantity, 0), s.totalAmount]} />
      </div>
      <DataState query={query} empty={<EmptyState title="No sales in this period" />}>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Sale number</th><th>Date</th><th>Customer</th><th>Location</th><th className="num">Items</th><th className="num">Total</th></tr></thead>
            <tbody>
              {query.data?.content.map((s) => (
                <tr key={s.id}>
                  <td><strong>{s.saleNumber}</strong></td>
                  <td>{dateTime(s.saleDate)}</td>
                  <td>{s.customerName || <span className="muted">Walk-in</span>}</td>
                  <td>{s.locationName}</td>
                  <td className="num">{num(s.items.reduce((n, i) => n + i.quantity, 0))}</td>
                  <td className="num">{money(s.totalAmount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </DataState>
      <Pagination data={query.data} onChange={setPage} />
    </section>
  );
}

function PurchasesReport() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const query = useQuery(() => api.get('/purchases', { from, to, status, page, size: PAGE_SIZE }), [from, to, status, page]);
  useClampPage(query, page, setPage);
  const params = { from, to, status };
  const reset = () => setPage(0);
  return (
    <section className="card flush">
      <div className="toolbar report-toolbar">
        <DateFilters from={from} to={to} setFrom={setFrom} setTo={setTo} onAny={reset} />
        <select aria-label="Filter by status" value={status} onChange={(e) => { setStatus(e.target.value); reset(); }}>
          <option value="">All statuses</option>
          <option value="PENDING">Pending</option>
          <option value="RECEIVED">Received</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
        <div className="grow" />
        <ExportButton filename="purchase-history.csv"
          headers={['PO number', 'Supplier', 'Deliver to', 'Ordered', 'Received', 'Status', 'Total']}
          load={() => fetchAllRows('/purchases', params)}
          toRow={(po) => [po.poNumber, po.supplierName, po.locationName, po.orderDate, po.receivedDate || '', po.status, po.totalAmount]} />
      </div>
      <DataState query={query} empty={<EmptyState title="No purchase orders match these filters" />}>
        <div className="table-wrap">
          <table>
            <thead><tr><th>PO number</th><th>Supplier</th><th>Deliver to</th><th>Ordered</th><th>Received</th><th>Status</th><th className="num">Total</th></tr></thead>
            <tbody>
              {query.data?.content.map((po) => (
                <tr key={po.id}>
                  <td><strong>{po.poNumber}</strong></td>
                  <td>{po.supplierName}</td>
                  <td>{po.locationName}</td>
                  <td>{dateTime(po.orderDate)}</td>
                  <td>{dateTime(po.receivedDate)}</td>
                  <td>{po.status.charAt(0) + po.status.slice(1).toLowerCase()}</td>
                  <td className="num">{money(po.totalAmount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </DataState>
      <Pagination data={query.data} onChange={setPage} />
    </section>
  );
}

function TransactionsReport() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [type, setType] = useState('');
  const [locationId, setLocationId] = useState('');
  const locations = useLocations();
  const [page, setPage] = useState(0);
  const query = useQuery(
    () => api.get('/inventory/transactions', { from, to, categoryId, locationId, type, page, size: PAGE_SIZE }),
    [from, to, categoryId, locationId, type, page]);
  useClampPage(query, page, setPage);
  const params = { from, to, categoryId, locationId, type };
  const reset = () => setPage(0);
  return (
    <section className="card flush">
      <div className="toolbar report-toolbar">
        <DateFilters from={from} to={to} setFrom={setFrom} setTo={setTo} onAny={reset} />
        <CategoryFilter value={categoryId} onChange={(v) => { setCategoryId(v); reset(); }} />
        <LocationSelect aria-label="Filter by location" locations={locations.data || []} value={locationId}
                        allLabel="All locations" onChange={(v) => { setLocationId(v); reset(); }} />
        <select aria-label="Filter by type" value={type} onChange={(e) => { setType(e.target.value); reset(); }}>
          <option value="">All movement types</option>
          {Object.entries(TX_LABELS).map(([k, label]) => <option key={k} value={k}>{label}</option>)}
        </select>
        <div className="grow" />
        <ExportButton filename="inventory-transactions.csv"
          headers={['Date', 'Product', 'SKU', 'Location', 'Type', 'Change', 'Balance after', 'Reference', 'Note', 'By']}
          load={() => fetchAllRows('/inventory/transactions', params)}
          toRow={(t) => [t.createdAt, t.productName, t.sku, t.locationName, TX_LABELS[t.type] || t.type, t.quantityChange,
            t.quantityAfter, t.referenceType ? `${t.referenceType} ${t.referenceId ?? ''}`.trim() : '', t.note || '', t.createdBy || '']} />
      </div>
      <DataState query={query} empty={<EmptyState title="No movements match these filters" />}>
        <div className="table-wrap">
          <table>
            <thead><tr><th>When</th><th>Product</th><th>Location</th><th>Type</th><th className="num">Change</th><th className="num">Balance</th><th>Note</th></tr></thead>
            <tbody>
              {query.data?.content.map((t) => (
                <tr key={t.id}>
                  <td>{dateTime(t.createdAt)}</td>
                  <td><strong>{t.productName}</strong><small className="sub">{t.sku}</small></td>
                  <td>{t.locationName}</td>
                  <td>{TX_LABELS[t.type] || t.type}</td>
                  <td className={`num ${t.quantityChange >= 0 ? 'pos' : 'neg'}`}>{t.quantityChange > 0 ? '+' : ''}{num(t.quantityChange)}</td>
                  <td className="num">{num(t.quantityAfter)}</td>
                  <td className="muted">{t.note || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </DataState>
      <Pagination data={query.data} onChange={setPage} />
    </section>
  );
}

/** Admin-only CSV feeds for an accounting package or ERP import. The server formats them (and defuses spreadsheet formulas). */
function AccountingExport() {
  const toast = useToast();
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [busy, setBusy] = useState('');
  const FEEDS = [
    { key: 'sales', file: 'sales', title: 'Sales ledger', hint: 'One row per sold line: sale number, date, location, product, quantity, price, line total. Uses the date range.', dated: true },
    { key: 'purchases', file: 'purchases', title: 'Purchases ledger', hint: 'One row per ordered line with supplier, status and cost. Uses the date range.', dated: true },
    { key: 'products', file: 'products', title: 'Product master', hint: 'Every product with SKU, barcode, price and total stock. Ignores the date range.', dated: false },
  ];
  async function run(feed) {
    setBusy(feed.key);
    try {
      await downloadFile(`/integration/${feed.key}.csv`, feed.dated ? { from, to } : {}, `rims-${feed.file}.csv`);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy('');
    }
  }
  return (
    <section className="card">
      <p className="muted">
        CSV files in a fixed column layout, ready to import into accounting software or an ERP.
        This is a file export, not a live connector.
      </p>
      <div className="toolbar">
        <DateFilters from={from} to={to} setFrom={setFrom} setTo={setTo} onAny={() => {}} />
      </div>
      <ul className="plain-list">
        {FEEDS.map((f) => (
          <li key={f.key}>
            <div><strong>{f.title}</strong><small className="sub">{f.hint}</small></div>
            <button className="btn" onClick={() => run(f)} disabled={busy !== ''}>{busy === f.key ? 'Preparing…' : 'Download CSV'}</button>
          </li>
        ))}
      </ul>
    </section>
  );
}
