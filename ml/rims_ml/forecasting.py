"""Per-product daily demand forecasting.

Models (all pure numpy, no training service needed):
  * moving_average_7      - mean of the last 7 days
  * exp_smoothing_<alpha> - simple exponential smoothing, alpha in {0.1, 0.3, 0.5}
  * weekday_seasonal      - recent level x shrunken day-of-week factors (needs >= 28 days)

For every product the model with the lowest one-step-ahead backtest MAE is used. The seasonal
model must beat the best non-seasonal model by SEASONAL_MARGIN to be chosen, so it is not picked
on noise. The standard deviation of the backtest residuals is the demand uncertainty that
drives safety stock.
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np

SES_ALPHAS = (0.1, 0.3, 0.5)
SEASONAL_MIN_DAYS = 28
SEASONAL_SHRINK = 2.0       # pseudo-observations pulling each weekday factor toward 1 (see README)
SEASONAL_MARGIN = 0.05      # seasonal must be at least 5% better than the best flat model
MIN_BACKTEST_DAYS = 14      # below this there is no honest backtest


@dataclass(frozen=True)
class ForecastResult:
    model: str
    forecast: np.ndarray    # non-negative expected units per future day
    mae: float | None       # backtest mean absolute error (None if not enough history)
    sigma: float            # std-dev of daily forecast error
    n_days: int             # days of history used
    total_units: float      # units sold in that history
    confidence: str         # LOW | MEDIUM | HIGH


# ---------------------------------------------------------------- seasonal helpers
def weekday_factors(y: np.ndarray, wd: np.ndarray) -> np.ndarray:
    """Day-of-week multipliers (mean ~1), shrunk toward 1 when a weekday has little data."""
    f = np.ones(7)
    overall = y.mean() if len(y) else 0.0
    if overall <= 0:
        return f
    for w in range(7):
        mask = wd == w
        c = int(mask.sum())
        if c:
            weight = c / (c + SEASONAL_SHRINK)
            f[w] = weight * (y[mask].mean() / overall) + (1 - weight)
    f = np.clip(f, 0.2, 4.0)
    return f / f.mean()


def _seasonal_level_and_factors(y: np.ndarray, wd: np.ndarray) -> tuple[float, np.ndarray]:
    f = weekday_factors(y, wd)
    tail = slice(-28, None)
    level = float(np.mean(y[tail] / f[wd[tail]]))
    return level, f


# ---------------------------------------------------------------- one-step-ahead predictions
def _predictions(name: str, y: np.ndarray, wd: np.ndarray, start: int) -> np.ndarray:
    """Prediction for each day t in [start, n) using only data before t."""
    n = len(y)
    preds = np.empty(n - start)
    if name == "moving_average_7":
        for i, t in enumerate(range(start, n)):
            preds[i] = y[max(0, t - 7):t].mean()
    elif name.startswith("exp_smoothing_"):
        alpha = float(name.rsplit("_", 1)[1])
        level = np.empty(n)
        level[0] = y[0]
        for i in range(1, n):
            level[i] = alpha * y[i] + (1 - alpha) * level[i - 1]
        for i, t in enumerate(range(start, n)):
            preds[i] = level[t - 1]
    elif name == "weekday_seasonal":
        for i, t in enumerate(range(start, n)):
            level, f = _seasonal_level_and_factors(y[:t], wd[:t])
            preds[i] = level * f[wd[t]]
    else:  # pragma: no cover
        raise ValueError(name)
    return np.maximum(preds, 0.0)


def _future(name: str, y: np.ndarray, wd: np.ndarray, future_wd: np.ndarray) -> np.ndarray:
    h = len(future_wd)
    if name == "moving_average_7":
        out = np.full(h, y[-7:].mean())
    elif name.startswith("exp_smoothing_"):
        alpha = float(name.rsplit("_", 1)[1])
        level = y[0]
        for v in y[1:]:
            level = alpha * v + (1 - alpha) * level
        out = np.full(h, level)
    else:
        level, f = _seasonal_level_and_factors(y, wd)
        out = level * f[future_wd]
    return np.maximum(out, 0.0)


def _confidence(n_days: int, total: float) -> str:
    if n_days < MIN_BACKTEST_DAYS or total < 10:
        return "LOW"
    if n_days >= 60 and total >= 100:
        return "HIGH"
    return "MEDIUM"


def forecast_demand(y, weekdays, future_weekdays) -> ForecastResult:
    """Forecast len(future_weekdays) days ahead from daily units `y` (oldest first)."""
    y = np.asarray(y, dtype=float)
    wd = np.asarray(weekdays, dtype=int)
    fwd = np.asarray(future_weekdays, dtype=int)
    n = len(y)
    h = len(fwd)
    total = float(y.sum())

    if n == 0:
        return ForecastResult("no_history", np.zeros(h), None, 0.0, 0, 0.0, "LOW")
    if total == 0:
        return ForecastResult("no_sales", np.zeros(h), 0.0, 0.0, n, 0.0, _confidence(n, 0.0))
    if n < MIN_BACKTEST_DAYS:
        # too short to backtest: use the plain recent average and the spread of the data itself
        fc = _future("moving_average_7", y, wd, fwd)
        sigma = float(y.std(ddof=1)) if n > 1 else float(y.mean())
        return ForecastResult("moving_average_7", fc, None, sigma, n, total, "LOW")

    start = max(7, n // 2)
    candidates = ["moving_average_7"] + [f"exp_smoothing_{a}" for a in SES_ALPHAS]
    if n >= SEASONAL_MIN_DAYS:
        candidates.append("weekday_seasonal")

    scores: dict[str, tuple[float, float]] = {}
    for name in candidates:
        err = y[start:] - _predictions(name, y, wd, start)
        scores[name] = (float(np.abs(err).mean()), float(err.std(ddof=1)) if len(err) > 1 else float(abs(err).mean()))

    flat = {k: v for k, v in scores.items() if k != "weekday_seasonal"}
    best = min(flat, key=lambda k: flat[k][0])
    if "weekday_seasonal" in scores and scores["weekday_seasonal"][0] < flat[best][0] * (1 - SEASONAL_MARGIN):
        best = "weekday_seasonal"

    mae, sigma = scores[best]
    return ForecastResult(best, _future(best, y, wd, fwd), mae, sigma, n, total, _confidence(n, total))
