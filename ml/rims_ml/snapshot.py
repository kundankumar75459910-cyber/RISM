"""Turns RIMS data into the frames the forecaster needs."""
from __future__ import annotations

from dataclasses import dataclass, field

import pandas as pd

from .client import RimsClient

DEFAULT_LEAD_TIME_DAYS = 7.0


@dataclass
class Snapshot:
    products: pd.DataFrame            # product_id, sku, name, category, supplier_id, stock, reorder_level
    sales: pd.DataFrame               # date (midnight), product_id, quantity
    pending: dict[int, int] = field(default_factory=dict)         # product_id -> units on open purchase orders
    lead_times: dict[int, float] = field(default_factory=dict)    # supplier_id -> median days order -> received


PRODUCT_COLUMNS = ["product_id", "sku", "name", "category", "supplier_id", "stock", "reorder_level"]


def _lead_times(purchases: list[dict]) -> dict[int, float]:
    rows = []
    for po in purchases:
        if po.get("status") == "RECEIVED" and po.get("receivedDate") and po.get("orderDate"):
            days = (pd.Timestamp(po["receivedDate"]) - pd.Timestamp(po["orderDate"])).total_seconds() / 86400
            rows.append((po["supplierId"], max(days, 1.0)))
    if not rows:
        return {}
    df = pd.DataFrame(rows, columns=["supplier_id", "days"])
    return {int(k): float(v) for k, v in df.groupby("supplier_id")["days"].median().items()}


def from_api(client: RimsClient) -> Snapshot:
    products = client.get_all("/products")
    sales = client.get_all("/sales")
    purchases = client.get_all("/purchases")

    prod_df = pd.DataFrame([{
        "product_id": p["id"], "sku": p["sku"], "name": p["name"], "category": p.get("categoryName"),
        "supplier_id": p.get("supplierId"), "stock": p["quantity"], "reorder_level": p["reorderLevel"],
    } for p in products], columns=PRODUCT_COLUMNS)

    lines = [{"date": pd.Timestamp(s["saleDate"]).normalize(), "product_id": i["productId"], "quantity": i["quantity"]}
             for s in sales for i in s["items"]]
    sales_df = pd.DataFrame(lines, columns=["date", "product_id", "quantity"])
    if sales_df.empty:
        sales_df = sales_df.astype({"date": "datetime64[ns]", "product_id": "int64", "quantity": "int64"})

    pending: dict[int, int] = {}
    for po in purchases:
        if po["status"] == "PENDING":
            for i in po["items"]:
                pending[i["productId"]] = pending.get(i["productId"], 0) + i["quantity"]

    return Snapshot(prod_df, sales_df, pending, _lead_times(purchases))
