import numpy as np

from rims_ml.forecasting import forecast_demand

WD = lambda n, start=0: (np.arange(n) + start) % 7   # weekday numbers, Monday = 0


def test_constant_demand_forecasts_the_constant_with_no_uncertainty():
    n = 40
    r = forecast_demand(np.full(n, 5.0), WD(n), WD(14, n))
    assert np.allclose(r.forecast, 5.0)
    assert r.sigma == 0.0
    assert r.model != "weekday_seasonal"          # nothing to gain from seasonality on flat data


def test_weekday_pattern_is_detected_and_forecast_follows_it():
    n = 56                                         # 8 whole weeks, day 0 is a Monday
    wd = WD(n)
    y = np.where(wd >= 5, 10.0, 2.0)
    fut = WD(14, n)                                # continues the cycle: Monday first
    r = forecast_demand(y, wd, fut)
    assert r.model == "weekday_seasonal"
    saturday, monday = r.forecast[5], r.forecast[0]
    assert saturday > 3 * monday
    # shrinkage deliberately under-reacts a little (protects noisy products), so allow ~20% on a clean pattern
    assert abs(saturday - 10) < 2.0 and abs(monday - 2) < 1.0


def test_no_history_and_no_sales_are_handled():
    assert forecast_demand([], [], WD(7)).model == "no_history"
    r = forecast_demand(np.zeros(30), WD(30), WD(7, 30))
    assert r.model == "no_sales" and not r.forecast.any()


def test_short_history_gets_low_confidence_and_a_plain_average():
    r = forecast_demand([3, 4, 5, 4, 3], WD(5), WD(7, 5))
    assert r.confidence == "LOW" and r.mae is None
    assert np.allclose(r.forecast, 3.8)


def test_forecast_is_never_negative():
    y = np.maximum(np.linspace(20, 0, 40), 0)      # demand dying out
    r = forecast_demand(y, WD(40), WD(30, 40))
    assert (r.forecast >= 0).all()


def test_noisy_demand_has_positive_uncertainty_and_sensible_level():
    rng = np.random.default_rng(1)
    y = rng.poisson(6, 60).astype(float)
    r = forecast_demand(y, WD(60), WD(14, 60))
    assert 4 < r.forecast.mean() < 8
    assert r.sigma > 1 and r.mae is not None and r.confidence in {"MEDIUM", "HIGH"}
