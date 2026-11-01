# RIMS demand forecasting (ML part)

A small Python service that learns from RIMS sales history and tells you **which products to reorder,
when, and how many**. It reads data through the existing Spring Boot API, so it needs no database
access of its own, and it never changes anything in RIMS.

## What it does

For every product it:

1. **Builds a daily demand series** from sales (zero on days with no sales), using up to the last 90
   completed days. Today is excluded because it is incomplete.
2. **Forecasts demand** for the next days with the model that backtests best for that product:
   moving average (7 days), exponential smoothing (three speeds), or a weekday-seasonal model
   (needs 28+ days; it must beat the others by 5% to be chosen, so it is not picked on noise).
   Selection uses one-step-ahead backtesting on that product's own history.
3. **Measures uncertainty** as the spread of the backtest errors.
4. **Learns the supplier lead time** from received purchase orders (median days from order to
   receipt; default 7 days when a supplier has none).
5. **Recommends** using standard inventory maths:

```
safety stock   = z x sigma x sqrt(lead time)          z from the service level (95% -> 1.645)
reorder point  = forecast demand over lead time + safety stock
order up to    = forecast demand over (lead time + review period) + safety stock
suggested qty  = order up to - (stock on hand + units on open purchase orders)
```

Each product gets a status: `STOCKOUT`, `REORDER_NOW`, `ON_ORDER`, `OVERSTOCK` (more than 90 days of
cover), `OK`, or `SLOW_MOVER` (no recent sales), plus a plain-language reason and a confidence level
(`LOW` / `MEDIUM` / `HIGH`, based on how much history exists).

This is statistical forecasting with automatic model selection, not a neural network. With the small
amounts of history a retail shop has, simple models that are backtested usually beat complex ones.

## Run it

Requires Python 3.10+.

```bash
cd ml
pip install -r requirements.txt

# 1. Try it with built-in synthetic data (no backend needed)
python -m rims_ml --demo

# 2. Against your running RIMS backend (command line)
python -m rims_ml --api http://localhost:8080/api --user admin --password admin123 --csv recs.csv

# 3. As a service for the React "Forecast" page
python -m rims_ml.server        # http://localhost:8000
```

Service settings (environment variables):

| Variable | Default | Purpose |
|---|---|---|
| `RIMS_API_URL` | `http://localhost:8080/api` | Spring Boot API |
| `ML_PORT` | `8000` | Port to listen on |
| `ML_CORS_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Allowed browser origins |

The React app calls `GET /api/recommendations` and forwards the user's RIMS login token, so the
service has no credentials of its own. Optional query parameters: `serviceLevel` (0.5-0.999),
`horizon`, `leadTime`, `reviewDays`, `overstockDays`, `status`.

Run the tests: `python -m pytest` (29 tests: forecasting maths, recommendation rules, API client,
HTTP service).

## Limits to know about

- **Stockouts hide demand.** Days when an item was out of stock record zero sales, so its demand
  is underestimated. Treat `STOCKOUT` items as needing more than the suggestion.
- **New products look slower than they are.** History starts at the first sale in the shop, so a
  product added recently has leading zero days. Confidence is shown, but it is not corrected.
- **Short history means low confidence.** Under 14 days there is no backtest; the service falls back
  to a plain 7-day average and labels it `LOW`.
- **Not tuned on real data.** The seasonal shrinkage constant was chosen on synthetic data. Re-check
  it against your own sales.
- **Advice only.** Nothing is ordered or edited automatically.
