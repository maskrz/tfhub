from datetime import date

import pandas as pd
import pytest

from tfhub_ml.analysis.near_term_100m import baseline_review, build_examples, run_study
from tfhub_ml.data.extract import AuditData, CompetitionData
from test_sprint_readiness import fixture


def study_data():
    data, competition = fixture()
    tests = data.tests.copy()
    tests.loc[tests["id"] == 10, ["name", "unit", "higher_is_better"]] = ["60m w kolcach", "s", False]
    return AuditData(data.athletes, tests, data.results, 0), competition


def test_anchor_future_window_and_same_date_attempts():
    data, competition = study_data()
    rows = pd.DataFrame([
        {"id": 20, "athlete_id": 1, "event_id": 20, "result_date": "2025-01-02", "numeric_value": 7, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 21, "athlete_id": 1, "event_id": 20, "result_date": "2025-02-01", "numeric_value": 11, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 22, "athlete_id": 1, "event_id": 20, "result_date": "2025-02-02", "numeric_value": 6, "unit": "s", "season_type": "OUTDOOR"},
    ])
    competition = CompetitionData(competition.events, pd.concat([competition.results, rows]))
    examples = build_examples(data, competition, 20, 10, date(2025, 12, 31), frozenset({13}), 30)
    row = examples.loc[(examples["athlete_id"] == 1) & (examples["prediction_date"] == "2025-01-02")].iloc[0]
    assert row["test_time"] == 2
    assert row["previous_time"] == 13
    assert row["target"] == 11
    assert row["previous_result_id"] == 10
    assert row["target_result_id"] == 21
    assert row["window_end"] == pd.Timestamp("2025-02-01")
    assert row["status"] == "observed"
    assert len(examples.loc[(examples["athlete_id"] == 1) & (examples["prediction_date"] == "2025-01-02")]) == 1
    truncated = build_examples(data, competition, 20, 10, date(2025, 1, 31), frozenset({13}), 30)
    assert not (truncated["prediction_date"] == "2025-01-02").any()


def test_baseline_review_is_ordered_and_contains_only_requested_columns():
    data, competition = study_data()
    examples = build_examples(data, competition, 20, 10, date(2025, 12, 31), frozenset({13}), 30)
    review = baseline_review(examples)
    assert list(review.columns) == [
        "athlete_id", "prediction_date", "window_end", "previous_result_id", "previous_result_date",
        "previous_time", "target_result_id", "target_result_date", "target", "absolute_error_seconds",
    ]
    assert len(review) == len(examples.loc[(examples["status"] == "observed") & examples["previous_time"].notna()])
    assert review["absolute_error_seconds"].is_monotonic_decreasing
    assert review["target_result_id"].notna().all()
    assert review["previous_result_id"].notna().all()


def test_unknown_and_unusable_future_marks_require_review():
    data, competition = study_data()
    examples = build_examples(data, competition, 20, 10, date(2025, 12, 31), frozenset(), 30)
    row = examples.loc[(examples["athlete_id"] == 2) & (examples["prediction_date"] == "2025-01-02")].iloc[0]
    assert row["status"] == "review"
    assert pd.isna(row["target"])
    with pytest.raises(ValueError, match="Confirmed IDs"):
        build_examples(data, competition, 20, 10, date(2025, 12, 31), frozenset({999}), 30)


def test_model_requires_matured_labels_and_holds_out_athlete(monkeypatch):
    examples = pd.DataFrame([
        {"athlete_id": athlete, "prediction_date": pd.Timestamp("2025-01-01"), "window_end": pd.Timestamp("2025-01-31"),
         "status": "observed", "target": x * 2 + 1, "previous_time": x * 2 + 2, "test_time": x}
        for athlete, x in [(1, 1), (2, 2), (3, 3), (4, 4)]
    ] + [
        {"athlete_id": 1, "prediction_date": pd.Timestamp("2025-02-01"), "window_end": pd.Timestamp("2025-03-03"),
         "status": "observed", "target": 9, "previous_time": 10, "test_time": 4},
    ])
    monkeypatch.setattr("tfhub_ml.analysis.near_term_100m.build_examples", lambda *args: examples)
    result = run_study(*study_data(), 20, 10, date(2025, 12, 31), frozenset(), 30)
    comparison = result.metrics.loc[result.metrics["cohort"] == "chronological_test_model_comparison"].set_index("method")
    assert comparison.loc["linear_60m_test", "mae_seconds"] == pytest.approx(0)
    assert comparison.loc["latest_100m", "mae_seconds"] == pytest.approx(1)
    assert comparison.loc["linear_60m_test", "athletes"] == 1
    examples.loc[2, "window_end"] = pd.Timestamp("2025-02-01")
    result = run_study(*study_data(), 20, 10, date(2025, 12, 31), frozenset(), 30)
    assert "linear_60m_test" not in set(result.metrics["method"])


def test_empty_cohort_reports_without_fabricated_metrics():
    data, competition = study_data()
    result = run_study(data, competition, 20, 10, date(2024, 1, 1), frozenset(), 30)
    assert result.counts.loc[0, "observed_target_dates"] == 0
    assert result.metrics.empty


def test_confirmed_result_may_now_be_recorded_outdoor():
    data, competition = study_data()
    competition.results.loc[competition.results["id"] == 13, "season_type"] = "OUTDOOR"
    examples = build_examples(data, competition, 20, 10, date(2025, 12, 31), frozenset({13}), 30)
    assert not examples.empty
    competition.results.loc[competition.results["id"] == 13, "season_type"] = "INDOOR"
    with pytest.raises(ValueError, match="Confirmed IDs"):
        build_examples(data, competition, 20, 10, date(2025, 12, 31), frozenset({13}), 30)
