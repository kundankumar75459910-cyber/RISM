"""Synthetic data so the forecaster can be tried and tested without the Spring Boot backend."""
from __future__ import annotations

from datetime import date, timedelta

import numpy as np
import pandas as pd

from .snapshot import PRODUCT_COLUMNS, Snapshot

# id, name, units/day, weekend multiplier, stock, on order, supplier id, configured reorder level
_SPECS = [
    (1, "Steady Seller", 5.0, 2.5, 30, 0, 1, 10),
    (2, "Fast Mover (out of stock)", 8.0, 1.0, 0, 0, 1, 15),
    (3, "On-order Item", 4.0, 1.0, 12, 100, 2, 10),
    (4, "Overstocked Item", 1.0, 1.0, 500, 0, 2, 20),
    (5, "Healthy Item", 3.0, 1.0, 120, 0, 1, 25),
    (6, "Dead Stock", 0.0, 1.0, 40, 0, 2, 5),
]


def make_demo_snapshot(days: int = 60, seed: int = 7, today: date | None = None) -> Snapshot:
    rng = np.random.default_rng(seed)
    today = today or date.today()
    rows, products = [], []
    for pid, name, rate, weekend, stock, pending, supplier, reorder in _SPECS:
        products.append({"product_id": pid, "sku": f"DEMO-{pid:03d}", "name": name, "category": "Demo",
                         "supplier_id": supplier, "stock": stock, "reorder_level": reorder})
        for d in range(days, 0, -1):
            day = today - timedelta(days=d)
            lam = rate * (weekend if day.weekday() >= 5 else 1.0)
            qty = int(rng.poisson(lam)) if lam > 0 else 0
            if qty:
                rows.append({"date": pd.Timestamp(day), "product_id": pid, "quantity": qty})
    return Snapshot(
        pd.DataFrame(products, columns=PRODUCT_COLUMNS),
        pd.DataFrame(rows, columns=["date", "product_id", "quantity"]),
        pending={3: 100}, lead_times={1: 5.0, 2: 6.0})
