from datetime import date

import pandas as pd
import pytest

from tfhub_ml.analysis.season_experiment import build_examples, evaluate_examples, run_experiment
from tfhub_ml.data.extract import AuditData, CompetitionData
from test_season_feasibility import fixtures


def experiment_fixtures() -> tuple[AuditData, CompetitionData]:
    data, competition = fixtures()
    tests = data.tests.copy()
    tests.loc[tests["id"] == 10, "name"] = "60m w kolcach"
    competitions = competition.results.copy()
    competitions.loc[competitions["id"] == 13, "season_type"] = "OUTDOOR"
    competitions.loc[competitions["id"] == 14, "numeric_value"] = 9.5
    data = AuditData(data.athletes, tests, data.results, data.training_group_count)
    return data, CompetitionData(competition.events, competitions)


def test_examples_use_pre_may_data_and_best_target() -> None:
    data, competition = experiment_fixtures()
    examples, unresolved = build_examples(data, competition, 4, 10, 2024, date(2025, 11, 1), frozenset())
    first = examples.set_index("athlete_id").loc[1]
    assert first["input_time"] == 8.0
    assert first["target_time"] == 7.9
    assert unresolved == 0


def test_latest_test_attempt_and_invalid_target_handling() -> None:
    data, competition = experiment_fixtures()
    extra = pd.DataFrame([
        {"id": 101, "athlete_id": 1, "test_id": 10, "result_date": "2024-04-15", "value": 8.3},
        {"id": 102, "athlete_id": 1, "test_id": 10, "result_date": "2024-04-15", "value": 8.1},
    ])
    data = AuditData(data.athletes, data.tests, pd.concat([data.results, extra]), data.training_group_count)
    competition.results.loc[competition.results["id"] == 14, "numeric_value"] = None
    examples, _ = build_examples(data, competition, 4, 10, 2024, date(2025, 11, 1), frozenset())
    assert examples.set_index("athlete_id").loc[1, "input_time"] == 8.1
    assert 3 not in set(examples["athlete_id"])


def test_unconfirmed_unknown_marks_remain_excluded() -> None:
    data, competition = experiment_fixtures()
    extra = pd.DataFrame([{
        "id": 50, "athlete_id": 1, "event_id": 4, "result_date": "2024-06-01",
        "numeric_value": 7.0, "unit": "s", "season_type": "UNKNOWN",
    }])
    competition = CompetitionData(competition.events, pd.concat([competition.results, extra]))
    examples, unresolved = build_examples(data, competition, 4, 10, 2024, date(2025, 11, 1), frozenset())
    assert examples.set_index("athlete_id").loc[1, "target_time"] == 7.9
    assert unresolved == 1
    with pytest.raises(ValueError, match="UNKNOWN results"):
        build_examples(data, competition, 4, 10, 2024, date(2025, 11, 1), frozenset({50}))


def test_leave_one_athlete_out_metrics_use_training_fold_only() -> None:
    examples = pd.DataFrame({
        "athlete_id": [1, 2, 3, 4],
        "input_time": [1.0, 2.0, 3.0, 4.0],
        "target_time": [3.0, 5.0, 7.0, 9.0],
    })
    metrics = evaluate_examples(examples).set_index("method")
    assert metrics.loc["linear_one_feature", "mean_absolute_error_seconds"] == pytest.approx(0.0, abs=1e-12)
    assert metrics.loc["mean_baseline", "mean_absolute_error_seconds"] == pytest.approx(8 / 3)
    assert metrics.loc["mean_baseline", "maximum_absolute_error_seconds"] == pytest.approx(4.0)
    assert metrics.loc["linear_one_feature", "held_out_athletes"] == 4


def test_repeated_athlete_or_constant_fold_fails_explicitly() -> None:
    repeated = pd.DataFrame({"athlete_id": [1, 1, 2], "input_time": [1, 2, 3], "target_time": [3, 4, 5]})
    with pytest.raises(ValueError, match="one example"):
        evaluate_examples(repeated)
    constant = pd.DataFrame({"athlete_id": [1, 2, 3], "input_time": [1, 1, 2], "target_time": [3, 4, 5]})
    with pytest.raises(ValueError, match="constant"):
        evaluate_examples(constant)


def test_experiment_rejects_bad_metadata_and_incomplete_season() -> None:
    data, competition = experiment_fixtures()
    with pytest.raises(ValueError, match="chosen season"):
        build_examples(data, competition, 4, 10, 2025, date(2025, 6, 1), frozenset())
    with pytest.raises(ValueError, match="verified"):
        build_examples(data, competition, 4, 20, 2024, date(2025, 11, 1), frozenset())


def test_missing_input_is_counted_not_imputed() -> None:
    data, competition = experiment_fixtures()
    reduced = data.results.loc[~((data.results["athlete_id"] == 3) & (data.results["test_id"] == 10))]
    reduced = pd.concat([reduced, pd.DataFrame([{
        "id": 99, "athlete_id": 3, "test_id": 20, "result_date": "2024-04-01", "value": 2.0,
    }])], ignore_index=True)
    data = AuditData(data.athletes, data.tests, reduced, data.training_group_count)
    examples, _ = build_examples(data, competition, 4, 10, 2024, date(2025, 11, 1), frozenset())
    assert len(examples) == 2
    with pytest.raises(ValueError, match="three distinct"):
        run_experiment(data, competition, 4, 10, 2024, date(2025, 11, 1), frozenset())
