from pathlib import Path

from tfhub_ml.analysis.audit import (
    athlete_statistics,
    coverage_analysis,
    cross_test_analysis,
    data_quality_issues,
    temporal_statistics,
    test_statistics as calculate_test_statistics,
)
from tfhub_ml.reporting.generate import generate_report
from tfhub_ml.analysis.competition import competition_audit
from test_analysis import sample_data
from test_competition import sample_competition


def test_report_generation_writes_name_free_timestamped_outputs(tmp_path: Path) -> None:
    data = sample_data()
    tests = calculate_test_statistics(data)
    athletes = athlete_statistics(data)
    coverage, distribution = coverage_analysis(data)
    monthly, yearly, athlete_period, intervals = temporal_statistics(data)
    gaps, windows, correlations = cross_test_analysis(data)
    issues = data_quality_issues(data)

    output = generate_report(
        data, tmp_path, tests, athletes, coverage, distribution,
        monthly, yearly, athlete_period, intervals, gaps, windows, correlations, issues,
        competition_audit(data, sample_competition(), (7, 30)),
    )
    report = (output / "data_audit_report.md").read_text(encoding="utf-8")

    assert (output / "test_statistics.csv").exists()
    assert (output / "test_coverage_heatmap.png").exists()
    assert "Dataset readiness assessment" in report
    assert "Competition data overview" in report
    assert "not approved mappings" in report
    assert (output / "competition_windows.csv").exists()
    assert (output / "competition_results_per_event.png").exists()
    assert report.rstrip().endswith("- For any later prediction experiment, define the target, prediction date, and leakage-safe chronological evaluation split before feature engineering.")
    assert "first_name" not in report
    assert "last_name" not in report
