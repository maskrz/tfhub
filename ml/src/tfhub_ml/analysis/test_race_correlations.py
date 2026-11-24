"""Descriptive associations between prior tests and future 100 m results."""

from datetime import date

import numpy as np
import pandas as pd

from tfhub_ml.analysis.near_term_100m import build_examples
from tfhub_ml.data.extract import AuditData, CompetitionData


def correlate_tests(
    data: AuditData, competition: CompetitionData, event_id: int, as_of: date,
    confirmed_ids: frozenset[int], horizon: int,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    examples = build_examples(data, competition, event_id, None, as_of, confirmed_ids, horizon)
    observed = examples.loc[examples["status"] == "observed"]
    tests = data.results.copy()
    tests["result_date"] = pd.to_datetime(tests["result_date"])
    tests["value"] = pd.to_numeric(tests["value"], errors="coerce")
    coverage = pd.DataFrame([{
        "complete_window_dates": len(examples),
        "observed_dates": len(observed),
        "observed_athletes": observed["athlete_id"].nunique(),
        "no_observed_race": int((examples["status"] == "no_observed_race").sum()),
        "needs_review": int((examples["status"] == "review").sum()),
    }])
    output = []
    for metadata in data.tests.sort_values("id").to_dict("records"):
        matching = tests.loc[tests["test_id"] == metadata["id"]]
        pairs = []
        invalid_latest = 0
        for example in observed.itertuples(index=False):
            history = matching.loc[
                (matching["athlete_id"] == example.athlete_id)
                & (matching["result_date"] < example.prediction_date)
            ]
            if history.empty:
                continue
            latest = history["result_date"].max()
            attempts = history.loc[history["result_date"] == latest]
            values = attempts["value"]
            if values.isna().any() or not np.isfinite(values).all() or (values <= 0).any():
                invalid_latest += 1
                continue
            choice = attempts.sort_values(
                ["value", "id"], ascending=[not bool(metadata["higher_is_better"]), True]
            ).iloc[0]
            pairs.append({
                "athlete_id": example.athlete_id, "test_result_id": choice["id"],
                "target_result_id": example.target_result_id,
                "test_value": choice["value"], "target": example.target,
                "age_days": (example.prediction_date - latest).days,
            })
        pair = pd.DataFrame(pairs, columns=[
            "athlete_id", "test_result_id", "target_result_id", "test_value", "target", "age_days"
        ])
        for method in ("pearson", "spearman"):
            correlation = None
            # Avoid a scipy dependency; Spearman is Pearson on average ranks.
            if len(pair) >= 2 and pair["test_value"].nunique() > 1 and pair["target"].nunique() > 1:
                x, y = pair["test_value"], pair["target"]
                if method == "spearman":
                    x, y = x.rank(method="average"), y.rank(method="average")
                correlation = x.corr(y, method="pearson")
            output.append({
                "test_id": metadata["id"], "test_name": metadata["name"], "unit": metadata["unit"],
                "higher_is_better": metadata["higher_is_better"], "method": method,
                "window_days": horizon, "paired_dates": len(pair),
                "distinct_athletes": pair["athlete_id"].nunique(),
                "distinct_test_results": pair["test_result_id"].nunique(),
                "distinct_target_races": pair["target_result_id"].nunique(),
                "missing_or_unusable_test_dates": len(observed) - len(pair),
                "unusable_latest_test_dates": invalid_latest,
                "median_test_age_days": pair["age_days"].median() if not pair.empty else None,
                "max_test_age_days": pair["age_days"].max() if not pair.empty else None,
                "correlation": correlation,
                "warning": "Exploratory; repeated observations are not independent; sample may be too small for reliable conclusions.",
            })
    columns = ["test_id", "test_name", "unit", "higher_is_better", "method", "window_days", "paired_dates",
               "distinct_athletes", "distinct_test_results", "distinct_target_races", "missing_or_unusable_test_dates",
               "unusable_latest_test_dates", "median_test_age_days", "max_test_age_days", "correlation", "warning"]
    return coverage, pd.DataFrame(output, columns=columns)
