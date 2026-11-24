"""Explore 30-day 100 m predictions, with optional local review export."""

import argparse
import logging
import sys
from datetime import date, datetime, timezone
from pathlib import Path

import pandas as pd
from sqlalchemy.exc import SQLAlchemyError

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from tfhub_ml.analysis.near_term_100m import baseline_review, build_examples, run_study
from tfhub_ml.config.settings import load_settings
from tfhub_ml.data.confirmation import load_confirmed_outdoor_ids
from tfhub_ml.data.extract import read_audit_data
from tfhub_ml.database.connection import create_read_only_engine
from tfhub_ml.reporting.generate import _markdown_table


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event-id", type=int, required=True)
    parser.add_argument("--test-id", type=int, required=True)
    parser.add_argument("--confirmed-unknown-outdoor-ids", type=Path, required=True)
    parser.add_argument("--as-of", type=date.fromisoformat, default=date.today())
    parser.add_argument("--export-baseline-review", action="store_true", help="Export sensitive athlete-ID baseline cases to the ignored report directory.")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    try:
        confirmed = load_confirmed_outdoor_ids(args.confirmed_unknown_outdoor_ids)
        settings = load_settings(PROJECT_ROOT / ".env")
        engine = create_read_only_engine(settings)
        try:
            data, competition = read_audit_data(engine, settings)
        finally:
            engine.dispose()
        study = run_study(data, competition, args.event_id, args.test_id, args.as_of, confirmed, 30)
        output = PROJECT_ROOT / "reports" / ("100m_experiment_" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
        output.mkdir(parents=True, exist_ok=False)
        for name, table in (("counts", study.counts), ("years", study.years), ("metrics", study.metrics)):
            table.to_csv(output / f"{name}.csv", index=False, float_format="%.6f")
        pd.DataFrame({"result_id": sorted(confirmed)}).to_csv(output / "confirmed_outdoor_result_ids.csv", index=False)
        if args.export_baseline_review:
            examples = build_examples(data, competition, args.event_id, args.test_id, args.as_of, confirmed, 30)
            baseline_review(examples).to_csv(output / "baseline_review.csv", index=False, float_format="%.3f")
        text = [
            "# Exploratory 30-day outdoor 100 m study", "",
            f"- Snapshot cutoff {args.as_of}; event ID {args.event_id}; 60 m in spikes test ID {args.test_id}.",
            f"- {len(confirmed)} IDs from the reviewed UNKNOWN set are copied into this directory. They must still belong to this 100 m event and now be UNKNOWN or OUTDOOR; only those still UNKNOWN are reclassified for this analysis. Other UNKNOWN results are not assumed outdoor.",
            "- Anchors are the calendar day after each distinct athlete/test or athlete/100 m competition date, selected regardless of future races. The outcome is the fastest comparable recorded outdoor 100 m race from day 1 through day 30 after the prediction date, inclusive. Races on the prediction date are excluded because ordering is unknown. Only fully elapsed windows are included.",
            "- Positive numeric results in seconds are comparable. A window containing an unconfirmed UNKNOWN race or a noncomparable outdoor mark is set aside for review, since it might change the fastest result. No recorded race is not a poor result: it is an unobserved outcome.",
            "- The baseline is the fastest comparable outdoor 100 m time on the most recent prior race date. Test input is the fastest 60 m in spikes attempt on the most recent prior test date. Prediction-date records are excluded because ordering is unknown. Input recency is not capped.",
            "- The exploratory test-only linear model is fit separately for each evaluated anchor using only other athletes and examples whose entire outcome window ended before the prediction date; it requires at least three distinct training athletes and variation in test values. The baseline is also reported on the identical model-evaluable cohort. Other baseline cohorts are not directly comparable to that model.",
            "- Repeated prediction dates and overlapping target windows are not independent; example-weighted metrics can be dominated by frequent athletes. Athlete-weighted MAE averages each athlete's mean absolute error equally. Small samples and anchor selection after recorded events do not establish accuracy on arbitrary dates or an unseen season. Training data may include repeated examples of the same other athlete.",
            "- Wind, timing methods and competition context have not been verified. Data entry and correction dates are unavailable: source event dates do not prove historical availability. Missing race records and race participation can bias outcomes; current test metadata can change.",
            "- Errors are seconds; no model is exported. Errors are not calibrated prediction intervals. No production data was modified.",
            (
                "- Sensitive `baseline_review.csv` contains athlete IDs, result IDs, dates and individual times for the latest-100 m baseline; it is local and Git-ignored. It is not a review of the test-based model."
                if args.export_baseline_review else
                "- No individual predictions or athlete-level examples are exported."
            ),
            "", "## Coverage", "", _markdown_table(study.counts),
            "", "## Coverage by prediction year", "", _markdown_table(study.years, limit=len(study.years)),
            "", "## Exploratory error", "", _markdown_table(study.metrics.round(4), limit=len(study.metrics)),
            "", "If no chronological test-model rows appear, too few earlier distinct athletes with matured target windows and varying test inputs were available. Do not interpret that as evidence that tests cannot help.", "",
        ]
        (output / "experiment_report.md").write_text("\n".join(text), encoding="utf-8")
    except (ValueError, SQLAlchemyError, OSError) as exc:
        logging.error("%s", exc)
        return 1
    logging.info("100 m experiment report: %s", output / "experiment_report.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
