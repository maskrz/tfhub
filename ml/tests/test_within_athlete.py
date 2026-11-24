from datetime import date

import pandas as pd
import pytest

from tfhub_ml.analysis.within_athlete import analyze, build_changes, build_season_examples, selected_test_anchors
from tfhub_ml.data.extract import AuditData, CompetitionData


def fixture():
    tests = pd.DataFrame([{"id": 1, "name": "Jump", "unit": "m", "higher_is_better": True}])
    results = pd.DataFrame([
        {"id": 1, "athlete_id": 1, "test_id": 1, "result_date": "2024-04-01", "value": 2.0},
        {"id": 2, "athlete_id": 1, "test_id": 1, "result_date": "2024-04-01", "value": 2.1},
        {"id": 3, "athlete_id": 1, "test_id": 1, "result_date": "2025-04-01", "value": 2.2},
        {"id": 4, "athlete_id": 1, "test_id": 1, "result_date": "2025-09-01", "value": 2.3},
    ])
    races = pd.DataFrame([
        {"id": 11, "athlete_id": 1, "event_id": 7, "result_date": "2024-05-01", "numeric_value": 15., "unit": "s", "season_type": "OUTDOOR"},
        {"id": 12, "athlete_id": 1, "event_id": 7, "result_date": "2024-10-31", "numeric_value": 14.8, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 13, "athlete_id": 1, "event_id": 7, "result_date": "2025-05-01", "numeric_value": 14.4, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 14, "athlete_id": 1, "event_id": 7, "result_date": "2025-09-10", "numeric_value": 14.1, "unit": "s", "season_type": "UNKNOWN"},
        {"id": 15, "athlete_id": 1, "event_id": 7, "result_date": "2025-11-01", "numeric_value": 10., "unit": "s", "season_type": "OUTDOOR"},
    ])
    return (AuditData(pd.DataFrame([{"id": 1}]), tests, results, 0),
            CompetitionData(pd.DataFrame([{"id": 7, "name": "100 m", "unit": "s"}]), races))


def test_season_boundaries_in_season_and_known_best():
    _, competition = fixture()
    anchors = pd.DataFrame([
        {"athlete_id": 1, "prediction_date": pd.Timestamp(d)} for d in
        ["2024-04-02", "2025-04-02", "2025-09-02", "2025-10-31"]
    ])
    result = build_season_examples(anchors, competition, 7, date(2025, 11, 1), frozenset({14}))
    assert len(result) == 3
    before = result.loc[result["prediction_date"] == "2025-04-02"].iloc[0]
    later = result.loc[result["prediction_date"] == "2025-09-02"].iloc[0]
    assert before["period"] == "preseason" and pd.isna(before["known_season_best"])
    assert later["period"] == "in_season" and later["known_season_best"] == 14.4
    assert later["target"] == 14.1 and later["future_race_rows"] == 1
    assert result.iloc[0]["target"] == 14.8
    unconfirmed = build_season_examples(anchors, competition, 7, date(2025, 11, 1), frozenset())
    assert unconfirmed.loc[unconfirmed["prediction_date"] == "2025-09-02", "status"].iloc[0] == "review"
    incomplete = build_season_examples(anchors, competition, 7, date(2025, 10, 31), frozenset({14}))
    assert len(incomplete) == 1


def test_same_day_is_review_and_known_only_is_visible():
    _, competition = fixture()
    anchors = pd.DataFrame([
        {"athlete_id": 1, "prediction_date": pd.Timestamp("2025-05-01")},
        {"athlete_id": 1, "prediction_date": pd.Timestamp("2025-10-01")},
    ])
    result = build_season_examples(anchors, competition, 7, date(2025, 11, 1), frozenset({14}))
    assert result.iloc[0]["status"] == "review"
    assert result.iloc[1]["status"] == "observed"
    assert result.iloc[1]["future_race_rows"] == 0
    assert result.iloc[1]["known_season_best"] == result.iloc[1]["target"]


def test_changes_use_only_matured_outcomes_and_current_minus_previous():
    observed = pd.DataFrame([
        {"athlete_id": 1, "test_date": pd.Timestamp("2024-04-01"), "window_end": pd.Timestamp("2024-05-02"),
         "test_value": 2., "target": 15., "test_result_id": 1, "target_result_id": 11},
        {"athlete_id": 1, "test_date": pd.Timestamp("2024-04-20"), "window_end": pd.Timestamp("2024-05-21"),
         "test_value": 2.1, "target": 14.9, "test_result_id": 2, "target_result_id": 12},
        {"athlete_id": 1, "test_date": pd.Timestamp("2024-05-21"), "window_end": pd.Timestamp("2024-06-21"),
         "test_value": 2.2, "target": 14.5, "test_result_id": 3, "target_result_id": 13},
    ])
    changes = build_changes(observed)
    assert len(changes) == 1
    assert changes.iloc[0]["prior_test_result_id"] == 1
    assert changes.iloc[0]["test_change"] == pytest.approx(.2)
    assert changes.iloc[0]["time_change"] == pytest.approx(-.5)


def test_selection_and_end_to_end_empty_and_one_athlete():
    data, competition = fixture()
    audit = analyze(data, competition, 7, date(2025, 11, 1), frozenset({14}), 30)
    season = audit.coverage.set_index(["outcome", "test_id"]).loc[("season_best", 1)]
    assert season["preseason_observed"] == 2 and season["in_season_observed"] == 1
    change = audit.correlations.set_index(["outcome", "analysis", "method"]).loc[("season_best", "within_athlete_change", "pearson")]
    assert change["paired_observations"] == 2 and change["athletes"] == 1
    empty = analyze(data, competition, 7, date(2023, 1, 1), frozenset(), 30)
    assert empty.coverage["observed_dates"].sum() == 0
    assert empty.correlations["correlation"].isna().all()
    anchors = pd.DataFrame([{"athlete_id": 1, "prediction_date": pd.Timestamp("2024-04-02")}])
    chosen = selected_test_anchors(anchors, data.results, True)
    assert chosen.iloc[0]["test_value"] == 2.1
    assert chosen.iloc[0]["test_result_id"] == 2
