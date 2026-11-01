from datetime import date, timedelta

import numpy as np
import pandas as pd

from rims_ml.snapshot import PRODUCT_COLUMNS, Snapshot

TODAY = date(2026, 10, 28)


def make_snapshot(specs, pending=None, lead_times=None, today=TODAY, extra_sales=()):
    """specs: list of dicts with id, daily (list of units, oldest first, ending yesterday),
    stock, optional supplier and reorder."""
    products, rows = [], []
    for s in specs:
        products.append({"product_id": s["id"], "sku": f"SKU-{s['id']}", "name": s.get("name", f"P{s['id']}"),
                         "category": "Test", "supplier_id": s.get("supplier", 1), "stock": s["stock"],
                         "reorder_level": s.get("reorder", 10)})
        n = len(s["daily"])
        for i, q in enumerate(s["daily"]):
            if q:
                rows.append({"date": pd.Timestamp(today - timedelta(days=n - i)), "product_id": s["id"], "quantity": int(q)})
    for pid, day, q in extra_sales:
        rows.append({"date": pd.Timestamp(day), "product_id": pid, "quantity": q})
    return Snapshot(pd.DataFrame(products, columns=PRODUCT_COLUMNS),
                    pd.DataFrame(rows, columns=["date", "product_id", "quantity"]),
                    pending or {}, lead_times if lead_times is not None else {1: 5.0})


def steady(units=5, days=40):
    return [units] * days
