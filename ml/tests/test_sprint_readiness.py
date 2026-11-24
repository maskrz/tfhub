from datetime import date

import pandas as pd
import pytest

from tfhub_ml.analysis.sprint_readiness import assess_sprint_readiness
from tfhub_ml.data.extract import AuditData, CompetitionData
from test_analysis import sample_data
from test_competition import sample_competition


def fixture():
    data = sample_data()
    tests = data.tests.iloc[:1].copy()
    tests.loc[tests.index[0], "id"] = 10
    results = pd.DataFrame([
        {"id": 1, "athlete_id": 1, "test_id": 10, "result_date": "2025-01-01", "value": 2},
        {"id": 2, "athlete_id": 1, "test_id": 10, "result_date": "2025-01-01", "value": 3},
        {"id": 3, "athlete_id": 1, "test_id": 10, "result_date": "2025-01-08", "value": 4},
        {"id": 4, "athlete_id": 2, "test_id": 10, "result_date": "2025-01-01", "value": 3},
    ])
    competition = sample_competition()
    rows = pd.DataFrame([
        {"id": 10, "athlete_id": 1, "event_id": 20, "result_date": "2024-12-01", "numeric_value": 13, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 11, "athlete_id": 1, "event_id": 20, "result_date": "2025-01-08", "numeric_value": 12, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 12, "athlete_id": 1, "event_id": 20, "result_date": "2025-01-08", "numeric_value": 12, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 13, "athlete_id": 2, "event_id": 20, "result_date": "2025-01-08", "numeric_value": 13, "unit": "s", "season_type": "UNKNOWN"},
        {"id": 14, "athlete_id": 2, "event_id": 20, "result_date": "2025-01-08", "numeric_value": None, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 15, "athlete_id": 2, "event_id": 20, "result_date": "2025-01-08", "numeric_value": 13, "unit": "m", "season_type": "OUTDOOR"},
        {"id": 16, "athlete_id": 2, "event_id": 20, "result_date": "2026-01-01", "numeric_value": 13, "unit": "s", "season_type": "OUTDOOR"},
    ])
    return AuditData(data.athletes, tests, results, 0), CompetitionData(competition.events, rows)


def test_timing_classifications_and_multiple_attempts():
    data, competition = fixture()
    audit = assess_sprint_readiness(data, competition, 20, date(2025, 12, 31), (6, 7, 60))
    classifications = audit.classifications.set_index("season_type")
    assert classifications.loc["OUTDOOR", "result_rows"] == 5
    assert classifications.loc["OUTDOOR", "comparable_rows"] == 3
    assert classifications.loc["UNKNOWN", "comparable_rows"] == 1
    coverage = audit.coverage.set_index(["target_classification", "source", "test_id"])
    row = coverage.loc[("OUTDOOR", "test", 10)]
    assert row["targets_with_prior"] == 2
    assert row["targets_with_same_day_source"] == 2
    assert row["nearest_prior_median_days"] == 7
    windows = audit.windows.set_index(["target_classification", "source", "test_id", "window_days"])
    assert windows.loc[("OUTDOOR", "test", 10, 6), "target_rows"] == 0
    row = windows.loc[("OUTDOOR", "test", 10, 7)]
    assert row["anchor_target_pairs"] == 2
    assert row["distinct_athlete_anchor_dates"] == 1
    assert row["athletes"] == 1
    assert windows.loc[("UNKNOWN", "test", 10, 7), "target_rows"] == 1
    baseline = audit.coverage.loc[(audit.coverage["source"] == "prior_outdoor_100m") & (audit.coverage["target_classification"] == "OUTDOOR")].iloc[0]
    assert baseline["targets_with_prior"] == 2
    assert baseline["targets_with_same_day_source"] == 2


def test_empty_and_invalid_inputs():
    data, competition = fixture()
    empty = CompetitionData(competition.events, competition.results.iloc[0:0])
    audit = assess_sprint_readiness(data, empty, 20, date(2025, 1, 1), (7,))
    assert audit.classifications.empty
    assert (audit.windows["target_rows"] == 0).all()
    with pytest.raises(ValueError, match="verified"):
        assess_sprint_readiness(data, competition, 21, date(2025, 1, 1), (7,))
    with pytest.raises(ValueError, match="Windows"):
        assess_sprint_readiness(data, competition, 20, date(2025, 1, 1), (7, 7))
    with pytest.raises(ValueError, match="future"):
        assess_sprint_readiness(data, competition, 20, date(9999, 1, 1), (7,))


def test_confirmed_unknown_is_scoped_and_usable_as_prior_source():
    data, competition = fixture()
    extra = pd.DataFrame([
        {"id": 17, "athlete_id": 2, "event_id": 20, "result_date": "2025-01-15", "numeric_value": 12, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 18, "athlete_id": 2, "event_id": 20, "result_date": "2025-01-09", "numeric_value": 12, "unit": "s", "season_type": "UNKNOWN"},
    ])
    competition = CompetitionData(competition.events, pd.concat([competition.results, extra]))
    audit = assess_sprint_readiness(data, competition, 20, date(2025, 12, 31), (7,), frozenset({13}))
    combined = audit.coverage.loc[
        (audit.coverage["target_classification"] == "OUTDOOR_WITH_CONFIRMED")
        & (audit.coverage["source"] == "prior_outdoor_100m")
    ].iloc[0]
    assert combined["target_rows"] == 5
    assert combined["targets_with_prior"] == 3
    assert combined["targets_with_same_day_source"] == 2
    assert audit.classifications.set_index("season_type").loc["UNKNOWN", "result_rows"] == 2
    assert competition.results.loc[competition.results["id"] == 13, "season_type"].iloc[0] == "UNKNOWN"
    for ids in (frozenset({10}), frozenset({999}), frozenset({16})):
        with pytest.raises(ValueError, match="Confirmed IDs"):
            assess_sprint_readiness(data, competition, 20, date(2025, 12, 31), (7,), ids)
