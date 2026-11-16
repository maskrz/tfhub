"""Write timestamped audit outputs without exporting athlete names."""

from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt
import pandas as pd

from tfhub_ml.analysis.audit import AuditData, overview


def _save_csv(frame: pd.DataFrame, path: Path) -> None:
    frame.to_csv(path, index=False, na_rep="")


def _plot_results_by_test(test_stats: pd.DataFrame, path: Path) -> None:
    fig, axis = plt.subplots(figsize=(max(8, len(test_stats) * 0.6), 5))
    ordered = test_stats.sort_values("result_count")
    axis.barh(ordered["test_name"], ordered["result_count"], color="#326891")
    axis.set_xlabel("Recorded results")
    axis.set_title("Results per test")
    fig.tight_layout()
    fig.savefig(path, dpi=150)
    plt.close(fig)


def _plot_monthly_results(monthly: pd.DataFrame, path: Path) -> None:
    fig, axis = plt.subplots(figsize=(10, 4))
    if not monthly.empty:
        axis.plot(monthly["period"], monthly["result_count"], marker="o", color="#326891")
        axis.tick_params(axis="x", labelrotation=60)
    axis.set_ylabel("Recorded results")
    axis.set_title("Results per month")
    fig.tight_layout()
    fig.savefig(path, dpi=150)
    plt.close(fig)


def _plot_coverage(matrix: pd.DataFrame, path: Path) -> None:
    fig, axis = plt.subplots(figsize=(max(6, len(matrix) * 0.55), max(5, len(matrix) * 0.5)))
    if matrix.empty:
        axis.text(0.5, 0.5, "No test definitions", ha="center", va="center")
        axis.set_axis_off()
    else:
        image = axis.imshow(matrix.to_numpy(), cmap="Blues", aspect="auto")
        axis.set_xticks(range(len(matrix.columns)), labels=matrix.columns, rotation=45, ha="right")
        axis.set_yticks(range(len(matrix.index)), labels=matrix.index)
        axis.set_xlabel("Test ID")
        axis.set_ylabel("Test ID")
        fig.colorbar(image, ax=axis, label="Athletes with results in both tests")
    axis.set_title("Athlete overlap by test")
    fig.tight_layout()
    fig.savefig(path, dpi=150)
    plt.close(fig)


def _markdown_table(frame: pd.DataFrame, limit: int = 20) -> str:
    if frame.empty:
        return "_No rows._"
    shown = frame.head(limit).fillna("")
    headers = [str(column) for column in shown.columns]
    rows = [[str(value).replace("|", "\\|") for value in row] for row in shown.itertuples(index=False, name=None)]
    return "\n".join(
        [
            "| " + " | ".join(headers) + " |",
            "| " + " | ".join("---" for _ in headers) + " |",
            *("| " + " | ".join(row) + " |" for row in rows),
        ]
    )


def _assessment(
    stats: dict[str, object],
    tests: pd.DataFrame,
    coverage_distribution: pd.DataFrame,
    repeat_intervals: pd.DataFrame,
    issues: pd.DataFrame,
    windows: pd.DataFrame,
) -> tuple[list[str], list[str]]:
    sparse = tests.loc[tests["result_count"] < 10]
    well_sampled = tests.loc[tests["result_count"] >= 10]
    sequence_test_ids: set[int] = set()
    # A test/athlete sequence needs at least three distinct dates to permit a basic chronological split.
    if not repeat_intervals.empty:
        sequence_test_ids = set(
            repeat_intervals.loc[
                repeat_intervals["athlete_test_sequences_with_3plus_dates"] > 0,
                "test_id",
            ].astype(int)
        )
    athlete_counts = {
        str(row["distinct_tests"]): int(row["athlete_count"])
        for row in coverage_distribution.to_dict("records")
    }
    readiness = [
        f"- **Exploratory analysis:** {'possible' if int(stats['test_results']) else 'not possible yet'}; "
        f"{stats['test_results']} result rows across {stats['tests_with_results']} tests and "
        f"{stats['athletes_with_results']} athletes with results.",
        f"- **Simple predictive experiments:** the data are not yet established as sufficient. "
        f"{len(well_sampled)} tests have at least 10 rows (a descriptive screening threshold, not a sufficiency guarantee); "
        f"{len(sparse)} have fewer.",
        f"- **Tests to consider for further study:** {', '.join(str(value) for value in well_sampled['test_id'].tolist()) or 'none meet the 10-row screening threshold'}. "
        f"Tests with insufficient coverage (<10 rows): {', '.join(str(value) for value in sparse['test_id'].tolist()) or 'none'}.",
        f"- **Cross-test prediction:** not established. Athletes covering one, two, and three-or-more distinct tests: "
        f"{athlete_counts.get('1', 0)}, {athlete_counts.get('2', 0)}, and {athlete_counts.get('3 or more', 0)}; "
        f"candidate time-window pair counts are listed for every test pair below.",
        f"- **Chronological validation:** {len(sequence_test_ids)} test(s) have at least one athlete/test sequence with three or more distinct dates for an initial chronology screen "
        f"(test IDs: {', '.join(map(str, sorted(sequence_test_ids))) or 'none'}). This alone does not establish a reliable holdout.",
        f"- **Potential quality issues:** {len(issues)} flagged rows/items across "
        f"{issues['issue_type'].nunique() if not issues.empty else 0} issue categories; all are review signals, not automatic corrections.",
        "- Correlations use one nearest-in-time result pair per athlete and test pair within each reported window. Athletes with repeated measurements are not treated as independent rows within a correlation.",
    ]
    next_steps = [
        "- Review the observed date-gap distribution and the 7/14/30/60-day pair-count table before selecting any correlation window.",
        "- Investigate flagged quality records against the source application; do not remove or normalize records based only on statistical flags.",
        "- For any later prediction experiment, define the target, prediction date, and leakage-safe chronological evaluation split before feature engineering.",
    ]
    return readiness, next_steps


def generate_report(
    data: AuditData,
    output_root: Path,
    test_stats: pd.DataFrame,
    athlete_stats: pd.DataFrame,
    coverage: pd.DataFrame,
    coverage_distribution: pd.DataFrame,
    monthly: pd.DataFrame,
    yearly: pd.DataFrame,
    athlete_period: pd.DataFrame,
    repeat_intervals: pd.DataFrame,
    cross_test_gaps: pd.DataFrame,
    pair_windows: pd.DataFrame,
    correlations: pd.DataFrame,
    issues: pd.DataFrame,
) -> Path:
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output = output_root / timestamp
    output.mkdir(parents=True, exist_ok=False)

    _save_csv(athlete_stats, output / "athlete_statistics.csv")
    _save_csv(test_stats, output / "test_statistics.csv")
    _save_csv(coverage.rename_axis("test_id").reset_index(), output / "test_coverage_matrix.csv")
    temporal = pd.concat(
        [
            monthly.assign(period_type="month"),
            yearly.rename(columns={"year": "period"}).assign(period_type="year"),
        ],
        ignore_index=True,
        sort=False,
    )
    _save_csv(temporal, output / "temporal_statistics.csv")
    _save_csv(athlete_period, output / "athlete_temporal_statistics.csv")
    _save_csv(repeat_intervals, output / "repeat_measurement_intervals.csv")
    _save_csv(cross_test_gaps, output / "cross_test_time_gaps.csv")
    _save_csv(pair_windows, output / "cross_test_pair_counts_by_window.csv")
    _save_csv(correlations, output / "test_correlations.csv")
    _save_csv(issues, output / "data_quality_issues.csv")

    _plot_results_by_test(test_stats, output / "results_per_test.png")
    _plot_monthly_results(monthly, output / "results_per_month.png")
    _plot_coverage(coverage, output / "test_coverage_heatmap.png")

    stats = overview(data)
    issue_summary = (
        issues.groupby(["issue_type", "severity"], as_index=False)
        .size()
        .sort_values("size", ascending=False)
        .rename(columns={"size": "flagged_item_count"})
    )
    readiness, next_steps = _assessment(
        stats, test_stats, coverage_distribution, repeat_intervals, issues, pair_windows
    )
    report_lines = [
        "# TFHUB data audit",
        "",
        f"- Generated (UTC): {timestamp}",
        "- Source: configured PostgreSQL schema, read-only transaction.",
        "- Scope: athletes, training groups, tests, and test results. No athlete names were extracted.",
        "- No source records were modified.",
        "",
        "## Database overview",
        "",
        "| Metric | Count / value |",
        "|---|---:|",
        *[f"| {key.replace('_', ' ').title()} | {value if value is not None else 'n/a'} |" for key, value in stats.items()],
        "",
        "## Test-level statistics",
        "",
        _markdown_table(test_stats),
        "",
        "Tests are summarized separately; raw values are not compared across different units or test meanings.",
        "",
        "## Athlete-level coverage",
        "",
        _markdown_table(coverage_distribution),
        "",
        "Athlete IDs are used for grouping; names are excluded. Full per-athlete summaries are in `athlete_statistics.csv`.",
        "",
        "## Test coverage and temporal matching",
        "",
        "The coverage matrix counts athletes with at least one result in each test pair. Cross-test pair tables show raw result-pair counts and distinct athletes at 7, 14, 30, and 60 days. Correlations use at most one nearest-in-time pair per athlete for each test pair and window.",
        "",
        _markdown_table(pair_windows, limit=40),
        "",
        "The `cross_test_time_gaps.csv` file includes every cross-test result-pair date gap for a shared athlete. Its nearest-pair flag identifies the one pair per athlete/test pair used for correlations when it falls within a candidate window.",
        "",
        "## Temporal distribution",
        "",
        _markdown_table(temporal, limit=40),
        "",
        "The active-athlete measure reflects current active status, not status at the historical measurement date. Athlete/month counts are in `athlete_temporal_statistics.csv`; repeated-date intervals are in `repeat_measurement_intervals.csv`.",
        "",
        "## Data quality review flags",
        "",
        f"{len(issues)} potential issue/item row(s) were flagged. Flags do not imply that a record is invalid. Duplicate-looking results are review-only because multiple attempts on the same date can be legitimate.",
        "",
        _markdown_table(issue_summary),
        "",
        _markdown_table(issues, limit=30),
        "",
        "See `data_quality_issues.csv` for the complete review list. Statistical outliers are computed per test using the 1.5-IQR rule and are not domain limits.",
        "",
        "## Exploratory cross-test correlations",
        "",
        _markdown_table(correlations, limit=40),
        "",
        "Pearson and Spearman coefficients are exploratory, not causal evidence or proof of predictive ability. Small samples are explicitly flagged; repeated results from one athlete are reduced to one nearest-date pair per athlete for each test pair and cutoff.",
        "",
        "## Dataset readiness assessment",
        "",
        *readiness,
        "",
        "## Recommended next steps",
        "",
        *next_steps,
        "",
    ]
    (output / "data_audit_report.md").write_text(
        "\n".join(report_lines), encoding="utf-8"
    )
    return output
