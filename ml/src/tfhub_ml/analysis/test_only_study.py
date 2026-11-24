"""Chronological 100 m evaluation using subsets of available athletic tests."""

from dataclasses import dataclass
from itertools import combinations

import numpy as np
import pandas as pd

from tfhub_ml.analysis.multi_test_study import TESTS
from tfhub_ml.analysis.near_term_100m import _metrics


@dataclass(frozen=True)
class EvaluationResult:
    coverage: pd.DataFrame
    metrics: pd.DataFrame


def evaluate_test_only(examples: pd.DataFrame) -> EvaluationResult:
    """Evaluate every nonempty subset without using past competition as a feature."""
    observed = examples.loc[examples["status"] == "observed"].copy()
    coverage, metrics = [], []
    names = list(TESTS)
    for size in range(1, len(names) + 1):
        for feature_tuple in combinations(names, size):
            features = list(feature_tuple)
            label = "+".join(features)
            eligible = observed.dropna(subset=features)
            forecasts: dict[object, tuple[float, float]] = {}
            insufficient = rank_deficient = 0
            for index, row in eligible.iterrows():
                train = eligible.loc[
                    (eligible["window_end"] < row["prediction_date"])
                    & (eligible["athlete_id"] != row["athlete_id"])
                ]
                if train["athlete_id"].nunique() <= len(features) + 1:
                    insufficient += 1
                    continue
                x = train[features].to_numpy(dtype=float)
                center, scale = x.mean(axis=0), x.std(axis=0)
                if (scale == 0).any():
                    rank_deficient += 1
                    continue
                design = np.column_stack((np.ones(len(x)), (x - center) / scale))
                coef, _, rank, _ = np.linalg.lstsq(design, train["target"].to_numpy(dtype=float), rcond=None)
                if rank != design.shape[1]:
                    rank_deficient += 1
                    continue
                prediction = float(np.dot(np.r_[1.0, (row[features].to_numpy(dtype=float) - center) / scale], coef))
                mean = float(train["target"].mean())
                if not np.isfinite(prediction) or not np.isfinite(mean):
                    raise ValueError("A model produced a non-finite prediction.")
                forecasts[index] = (mean, prediction)

            evaluated = eligible.loc[list(forecasts)]
            without_history = evaluated.loc[evaluated["previous_time"].isna()]
            coverage.append({
                "features": label, "eligible_dates": len(eligible),
                "eligible_athletes": eligible["athlete_id"].nunique(),
                "eligible_without_prior_race": int(eligible["previous_time"].isna().sum()),
                "evaluated_dates": len(evaluated), "evaluated_athletes": evaluated["athlete_id"].nunique(),
                "evaluated_without_prior_race": len(without_history),
                "evaluated_athletes_without_prior_race": without_history["athlete_id"].nunique(),
                "insufficient_training_athletes": insufficient, "constant_or_rank_deficient_training": rank_deficient,
            })
            for cohort, rows in (("all", evaluated), ("no_previous_100m", without_history)):
                if rows.empty:
                    continue
                for method, position in (("training_mean", 0), ("test_linear", 1)):
                    errors = [forecasts[index][position] - target for index, target in rows["target"].items()]
                    metric = _metrics(method, cohort, rows, errors)
                    metric["features"] = label
                    metrics.append(metric)
    coverage_columns = [
        "features", "eligible_dates", "eligible_athletes", "eligible_without_prior_race",
        "evaluated_dates", "evaluated_athletes", "evaluated_without_prior_race",
        "evaluated_athletes_without_prior_race", "insufficient_training_athletes",
        "constant_or_rank_deficient_training",
    ]
    metric_columns = [
        "features", "cohort", "method", "examples", "athletes", "mae_seconds",
        "athlete_weighted_mae_seconds", "median_absolute_error_seconds", "max_absolute_error_seconds",
    ]
    return EvaluationResult(pd.DataFrame(coverage, columns=coverage_columns), pd.DataFrame(metrics, columns=metric_columns))
