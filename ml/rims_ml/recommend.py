"""Reorder recommendations from demand forecasts.

For each product:
  lead_demand  = forecast units over the supplier lead time L
  safety       = z * sigma * sqrt(L)           (z from the target service level)
  reorder_pt   = lead_demand + safety
  position     = stock on hand + units on open purchase orders
  order_up_to  = forecast units over (L + review period) + safety
  suggested    = ceil(order_up_to - position)  when the position is at or below the reorder point
"""
from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import date, timedelta
from statistics import NormalDist

import numpy as np
import pandas as pd

from .forecasting import forecast_demand
from .snapshot import DEFAULT_LEAD_TIME_DAYS, Snapshot

STATUS_ORDER = ["STOCKOUT", "REORDER_NOW", "ON_ORDER", "OVERSTOCK", "OK", "SLOW_MOVER"]


@dataclass(frozen=True)
class Params:
    horizon: int = 14                    # days used for the headline demand figures
    service_level: float = 0.95
    default_lead_time: float = DEFAULT_LEAD_TIME_DAYS
    review_days: int = 14                # how many days one order should cover beyond the lead time
    overstock_days: int = 90             # days of cover above which stock is flagged as excess
    window_days: int = 90                # maximum days of sales history to learn from
    include_today: bool = False          # today is usually incomplete, so excluded by default

    def validate(self) -> "Params":
        if not 0.5 <= self.service_level < 1:
            raise ValueError("service_level must be between 0.5 and 0.999")
        if self.horizon < 1 or self.review_days < 0 or self.window_days < 7 or self.default_lead_time < 1:
            raise ValueError("horizon >= 1, review_days >= 0, window_days >= 7, default_lead_time >= 1 required")
        return self


def _daily_matrix(sales: pd.DataFrame, product_ids, end: date, window_days: int) -> pd.DataFrame:
    """Rows = days (oldest first), columns = product ids, values = units sold. Zero-filled."""
    if sales.empty:
        return pd.DataFrame(index=pd.DatetimeIndex([]), columns=list(product_ids), dtype=float)
    s = sales[sales["date"] <= pd.Timestamp(end)]
    if s.empty:
        return pd.DataFrame(index=pd.DatetimeIndex([]), columns=list(product_ids), dtype=float)
    start = max(s["date"].min(), pd.Timestamp(end) - pd.Timedelta(days=window_days - 1))
    s = s[s["date"] >= start]
    pivot = s.groupby(["date", "product_id"])["quantity"].sum().unstack(fill_value=0)
    idx = pd.date_range(start, pd.Timestamp(end), freq="D")
    return pivot.reindex(index=idx, columns=list(product_ids), fill_value=0).astype(float)


def _clean(v):
    if isinstance(v, (np.floating, float)):
        return None if (math.isnan(v) or math.isinf(v)) else round(float(v), 3)
    if isinstance(v, np.integer):
        return int(v)
    return v


def recommend(snapshot: Snapshot, params: Params | None = None, today: date | None = None) -> list[dict]:
    p = (params or Params()).validate()
    today = today or date.today()
    end = today if p.include_today else today - timedelta(days=1)
    z = NormalDist().inv_cdf(p.service_level)
    matrix = _daily_matrix(snapshot.sales, snapshot.products["product_id"].tolist(), end, p.window_days)

    items: list[dict] = []
    for row in snapshot.products.itertuples(index=False):
        pid = int(row.product_id)
        y = matrix[pid].to_numpy() if pid in matrix.columns and len(matrix) else np.array([])
        wd = matrix.index.dayofweek.to_numpy() if len(matrix) else np.array([], dtype=int)

        supplier = None if pd.isna(row.supplier_id) else int(row.supplier_id)
        lead = float(snapshot.lead_times.get(supplier, p.default_lead_time)) if supplier is not None else p.default_lead_time
        lead_days = max(1, math.ceil(lead))
        span = max(p.horizon, lead_days + p.review_days)
        future = pd.date_range(pd.Timestamp(end) + pd.Timedelta(days=1), periods=span, freq="D")
        res = forecast_demand(y, wd, future.dayofweek.to_numpy())

        fc = res.forecast
        daily = float(fc[:p.horizon].mean()) if len(fc) else 0.0
        lead_demand = float(fc[:lead_days].sum())
        safety = z * res.sigma * math.sqrt(lead_days)
        reorder_point = lead_demand + safety
        order_up_to = float(fc[:lead_days + p.review_days].sum()) + safety

        stock = int(row.stock)
        pending = int(snapshot.pending.get(pid, 0))
        position = stock + pending
        cover = stock / daily if daily > 0 else None

        if daily <= 0:
            status, qty = "SLOW_MOVER", 0
            reason = f"No sales in the last {res.n_days} days of history." if res.n_days else "No sales history yet."
        elif stock == 0:
            status = "STOCKOUT"
            qty = max(math.ceil(order_up_to - position), 0)
            reason = f"Out of stock with about {daily:.1f} units/day of demand."
            if pending:
                reason += f" {pending} units already on order."
        elif position <= reorder_point:
            status = "REORDER_NOW"
            qty = max(math.ceil(order_up_to - position), 1)
            reason = (f"Stock {stock} + {pending} on order is at or below the reorder point "
                      f"{math.ceil(reorder_point)} ({lead_days}-day lead time, ~{daily:.1f} units/day).")
        elif stock <= reorder_point:
            status, qty = "ON_ORDER", 0
            reason = f"Stock {stock} is low but {pending} units are already on order."
        elif cover is not None and cover > p.overstock_days:
            status, qty = "OVERSTOCK", 0
            reason = f"{cover:.0f} days of cover at ~{daily:.1f} units/day (limit {p.overstock_days})."
        else:
            status, qty = "OK", 0
            reason = f"About {cover:.0f} days of cover; reorder point {math.ceil(reorder_point)}."

        items.append({k: _clean(v) for k, v in {
            "productId": pid, "sku": row.sku, "name": row.name, "category": row.category,
            "stock": stock, "pendingQty": pending, "currentReorderLevel": int(row.reorder_level),
            "recommendedReorderLevel": math.ceil(reorder_point) if daily > 0 else int(row.reorder_level),
            "avgDailyDemand": daily, "forecastHorizonUnits": float(fc[:p.horizon].sum()),
            "leadTimeDays": lead_days, "daysOfCover": cover, "status": status,
            "suggestedOrderQty": int(qty), "reason": reason, "model": res.model,
            "backtestMae": res.mae, "confidence": res.confidence, "historyDays": res.n_days,
            "unitsInHistory": res.total_units,
        }.items()})

    items.sort(key=lambda i: (STATUS_ORDER.index(i["status"]), i["daysOfCover"] if i["daysOfCover"] is not None else 1e9, i["name"]))
    return items


def summarize(items: list[dict]) -> dict:
    counts = {s: 0 for s in STATUS_ORDER}
    for i in items:
        counts[i["status"]] += 1
    return {"counts": counts, "totalProducts": len(items),
            "unitsToOrder": sum(i["suggestedOrderQty"] for i in items)}
