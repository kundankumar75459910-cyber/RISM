"""Command line: python -m rims_ml --demo   |   python -m rims_ml --api URL --user U --password P"""
from __future__ import annotations

import argparse
import sys

import pandas as pd

from . import snapshot as snapshot_mod
from .client import RimsApiError, RimsClient
from .demo import make_demo_snapshot
from .recommend import Params, recommend, summarize

COLUMNS = ["sku", "name", "stock", "pendingQty", "avgDailyDemand", "daysOfCover", "currentReorderLevel",
           "recommendedReorderLevel", "suggestedOrderQty", "status", "confidence", "model"]


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(prog="rims_ml", description="RIMS demand forecast and reorder recommendations")
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--demo", action="store_true", help="use synthetic data (no backend needed)")
    src.add_argument("--api", help="RIMS API base URL, e.g. http://localhost:8080/api")
    ap.add_argument("--user")
    ap.add_argument("--password")
    ap.add_argument("--horizon", type=int, default=14)
    ap.add_argument("--service-level", type=float, default=0.95)
    ap.add_argument("--lead-time", type=float, default=7.0, help="days, used when a supplier has no received orders")
    ap.add_argument("--status", help="only show this status, e.g. REORDER_NOW")
    ap.add_argument("--csv", help="also write the full result to this CSV file")
    a = ap.parse_args(argv)

    try:
        if a.demo:
            snap = make_demo_snapshot()
        else:
            if not (a.user and a.password):
                ap.error("--user and --password are required with --api")
            snap = snapshot_mod.from_api(RimsClient.login(a.api, a.user, a.password))
        items = recommend(snap, Params(horizon=a.horizon, service_level=a.service_level, default_lead_time=a.lead_time))
    except (RimsApiError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    df = pd.DataFrame(items)
    if a.csv:
        df.to_csv(a.csv, index=False)
    shown = df if not a.status else df[df["status"] == a.status]
    print(shown[COLUMNS].to_string(index=False) if len(shown) else "No products match.")
    s = summarize(items)
    print("\n" + ", ".join(f"{k}: {v}" for k, v in s["counts"].items() if v) + f" | units to order: {s['unitsToOrder']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
