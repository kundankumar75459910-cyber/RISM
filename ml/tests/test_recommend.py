import math
from datetime import timedelta

import numpy as np
import pytest

from rims_ml.recommend import Params, recommend, summarize
from rims_ml.snapshot import _lead_times
from helpers import TODAY, make_snapshot, steady


def one(items, pid=1):
    return next(i for i in items if i["productId"] == pid)


def test_reorder_point_and_order_quantity_for_constant_demand():
    # 5 units/day, 5 day lead time, no noise -> safety stock 0, reorder point 25
    items = recommend(make_snapshot([{"id": 1, "daily": steady(5), "stock": 20}]), today=TODAY)
    i = one(items)
    assert i["status"] == "REORDER_NOW"
    assert i["recommendedReorderLevel"] == 25
    assert i["suggestedOrderQty"] == math.ceil(5 * (5 + 14) - 20)      # cover lead time + review period
    assert i["daysOfCover"] == pytest.approx(4.0, abs=0.01)


def test_stock_already_covered_by_open_purchase_order():
    snap = make_snapshot([{"id": 1, "daily": steady(5), "stock": 20}], pending={1: 100})
    i = one(recommend(snap, today=TODAY))
    assert i["status"] == "ON_ORDER" and i["suggestedOrderQty"] == 0 and i["pendingQty"] == 100


def test_pending_order_that_is_too_small_still_triggers_reorder():
    snap = make_snapshot([{"id": 1, "daily": steady(5), "stock": 5}], pending={1: 5})
    i = one(recommend(snap, today=TODAY))
    assert i["status"] == "REORDER_NOW"
    assert i["suggestedOrderQty"] == math.ceil(5 * 19 - 10)


def test_stockout_overstock_ok_and_slow_mover():
    snap = make_snapshot([
        {"id": 1, "daily": steady(5), "stock": 0},
        {"id": 2, "daily": steady(5), "stock": 1000},
        {"id": 3, "daily": steady(5), "stock": 120},
        {"id": 4, "daily": [0] * 40, "stock": 50},
    ])
    items = recommend(snap, today=TODAY)
    assert one(items, 1)["status"] == "STOCKOUT" and one(items, 1)["suggestedOrderQty"] > 0
    assert one(items, 2)["status"] == "OVERSTOCK"
    assert one(items, 3)["status"] == "OK"
    s = one(items, 4)
    assert s["status"] == "SLOW_MOVER" and s["suggestedOrderQty"] == 0 and s["daysOfCover"] is None
    assert [i["status"] for i in items][0] == "STOCKOUT"             # most urgent first


def test_higher_service_level_means_more_safety_stock():
    rng = np.random.default_rng(3)
    snap = make_snapshot([{"id": 1, "daily": rng.poisson(8, 60).tolist(), "stock": 30}])
    lo = one(recommend(snap, Params(service_level=0.90), today=TODAY))["recommendedReorderLevel"]
    hi = one(recommend(snap, Params(service_level=0.99), today=TODAY))["recommendedReorderLevel"]
    assert hi > lo


def test_todays_incomplete_sales_are_ignored_by_default():
    spike = [(1, TODAY, 500)]
    base = one(recommend(make_snapshot([{"id": 1, "daily": steady(5), "stock": 20}]), today=TODAY))
    with_today = one(recommend(make_snapshot([{"id": 1, "daily": steady(5), "stock": 20}], extra_sales=spike), today=TODAY))
    assert with_today["avgDailyDemand"] == base["avgDailyDemand"]
    included = one(recommend(make_snapshot([{"id": 1, "daily": steady(5), "stock": 20}], extra_sales=spike),
                             Params(include_today=True), today=TODAY))
    assert included["avgDailyDemand"] > base["avgDailyDemand"]


def test_longer_supplier_lead_time_raises_the_reorder_point():
    short = one(recommend(make_snapshot([{"id": 1, "daily": steady(5), "stock": 20}], lead_times={1: 3}), today=TODAY))
    long = one(recommend(make_snapshot([{"id": 1, "daily": steady(5), "stock": 20}], lead_times={1: 10}), today=TODAY))
    assert long["recommendedReorderLevel"] > short["recommendedReorderLevel"]
    assert long["leadTimeDays"] == 10


def test_default_lead_time_used_when_supplier_has_no_history():
    snap = make_snapshot([{"id": 1, "daily": steady(5), "stock": 20, "supplier": 99}])
    assert one(recommend(snap, Params(default_lead_time=4), today=TODAY))["leadTimeDays"] == 4


def test_no_sales_at_all_does_not_crash():
    snap = make_snapshot([{"id": 1, "daily": [], "stock": 10}])
    i = one(recommend(snap, today=TODAY))
    assert i["status"] == "SLOW_MOVER" and i["historyDays"] == 0


def test_summary_counts_and_units():
    snap = make_snapshot([{"id": 1, "daily": steady(5), "stock": 0}, {"id": 2, "daily": steady(5), "stock": 120}])
    s = summarize(recommend(snap, today=TODAY))
    assert s["counts"]["STOCKOUT"] == 1 and s["counts"]["OK"] == 1 and s["unitsToOrder"] > 0


def test_lead_times_use_received_orders_only_and_median():
    po = lambda sid, days, status="RECEIVED": {"supplierId": sid, "status": status, "orderDate": "2026-10-01T10:00:00",
        "receivedDate": (f"2026-10-{1 + days:02d}T10:00:00" if status == "RECEIVED" else None)}
    lt = _lead_times([po(1, 3), po(1, 5), po(1, 9), po(2, 4, "PENDING"), po(1, 0)])
    assert lt == {1: 4.0}                                  # median of 3, 5, 9 and a same-day order clamped to 1


def test_invalid_parameters_are_rejected():
    for bad in (Params(service_level=1.0), Params(horizon=0), Params(default_lead_time=0)):
        with pytest.raises(ValueError):
            bad.validate()
