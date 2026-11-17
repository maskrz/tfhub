import pandas as pd

from tfhub_ml.analysis.competition import competition_audit
from tfhub_ml.data.extract import CompetitionData
from test_analysis import sample_data


def sample_competition() -> CompetitionData:
    events = pd.DataFrame([
        {"id": 20, "name": "100 m", "unit": "s", "active": True},
        {"id": 21, "name": "Relay", "unit": "s", "active": True},
        {"id": 22, "name": "Unused", "unit": "m", "active": False},
    ])
    results = pd.DataFrame([
        {"id": 201, "athlete_id": 1, "event_id": 20, "result_date": "2025-01-05", "numeric_value": 12.0, "unit": "s", "season_type": "INDOOR"},
        {"id": 202, "athlete_id": 1, "event_id": 20, "result_date": "2025-01-10", "numeric_value": None, "unit": "m", "season_type": "UNKNOWN"},
        {"id": 203, "athlete_id": 2, "event_id": 20, "result_date": "2025-02-28", "numeric_value": 13.0, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 204, "athlete_id": 2, "event_id": 21, "result_date": "2025-03-01", "numeric_value": 50.0, "unit": "s", "season_type": "OUTDOOR"},
    ])
    return CompetitionData(events, results)


def test_competition_coverage_quality_and_timing() -> None:
    audit = competition_audit(sample_data(), sample_competition(), (7, 30))
    assert audit.overview.loc[0, "result_count"] == 4
    assert audit.overview.loc[0, "athletes_with_test_and_competition_results"] == 2
    assert audit.overview.loc[0, "missing_numeric_values"] == 1
    events = audit.events.set_index("event_id")
    assert events.loc[20, "result_count"] == 3
    assert events.loc[20, "athlete_count"] == 2
    assert events.loc[20, "athletes_with_2plus_dates"] == 1
    assert events.loc[20, "athletes_with_3plus_dates"] == 0
    assert events.loc[22, "result_count"] == 0
    assert {"missing_numeric_value", "unknown_season_type", "unit_differs_from_current_event"} <= set(audit.quality["issue_type"])
    overlap = audit.overlap.set_index(["test_id", "event_id"])
    assert overlap.loc[(10, 20), "shared_athletes"] == 2
    assert overlap.loc[(10, 20), "all_result_pairs"] == 5
    window = audit.windows.set_index(["test_id", "event_id", "window_days"]).loc[(10, 20, 7)]
    assert window["pairs_within_window"] == 2
    assert window["athletes_within_window"] == 2
    assert window["test_on_or_before_competition_athletes"] == 1
    assert window["numeric_pair_athletes_within_window"] == 2
    closest = audit.gaps.set_index(["test_id", "event_id", "athlete_id"])
    assert closest.loc[(10, 20, 2), "gap_days"] == -1
    same_day = audit.windows.set_index(["test_id", "event_id", "window_days"]).loc[(10, 21, 7)]
    assert same_day["test_on_or_before_competition_athletes"] == 1
    assert closest.loc[(10, 21, 2), "gap_days"] == 0


def test_missing_numeric_result_counts_as_coverage_but_not_numeric_pair() -> None:
    data = sample_competition()
    data.results.loc[data.results["athlete_id"] == 1, "numeric_value"] = None
    audit = competition_audit(sample_data(), data, (7,))
    window = audit.windows.set_index(["test_id", "event_id", "window_days"]).loc[(10, 20, 7)]
    assert window["athletes_within_window"] == 2
    assert window["numeric_pair_athletes_within_window"] == 1


def test_no_competition_results() -> None:
    data = sample_competition()
    data = CompetitionData(data.events, data.results.iloc[0:0])
    audit = competition_audit(sample_data(), data, (7,))
    assert audit.overview.loc[0, "result_count"] == 0
    assert (audit.events["result_count"] == 0).all()
    assert audit.overlap.empty and audit.windows.empty and audit.quality.empty
