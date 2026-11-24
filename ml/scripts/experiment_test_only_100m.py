"""Evaluate test-only 100 m models with missing test combinations."""

import argparse
import logging
import sys
from datetime import date, datetime, timezone
from pathlib import Path

import pandas as pd
from sqlalchemy.exc import SQLAlchemyError

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from tfhub_ml.analysis.multi_test_study import build_feature_examples
from tfhub_ml.analysis.test_only_study import evaluate_test_only
from tfhub_ml.config.settings import load_settings
from tfhub_ml.data.confirmation import load_confirmed_outdoor_ids
from tfhub_ml.data.extract import read_audit_data
from tfhub_ml.database.connection import create_read_only_engine
from tfhub_ml.reporting.generate import _markdown_table


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event-id", type=int, required=True)
    parser.add_argument("--confirmed-unknown-outdoor-ids", type=Path, required=True)
    parser.add_argument("--as-of", type=date.fromisoformat, default=date.today())
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
        examples = build_feature_examples(data, competition, args.event_id, args.as_of, confirmed, 30)
        study = evaluate_test_only(examples)
        output = PROJECT_ROOT / "reports" / ("100m_test_only_" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
        output.mkdir(parents=True, exist_ok=False)
        study.coverage.to_csv(output / "coverage.csv", index=False)
        study.metrics.to_csv(output / "metrics.csv", index=False, float_format="%.6f")
        pd.DataFrame({"result_id": sorted(confirmed)}).to_csv(output / "confirmed_outdoor_result_ids.csv", index=False)
        text = [
            "# Experimental test-only 100 m study", "",
            f"- Snapshot cutoff {args.as_of}; verified event ID {args.event_id}; 30-day future window. {len(confirmed)} reviewed 100 m result IDs recorded separately.",
            "- Anchors are the day after a test or 100 m race date; target is the fastest recorded comparable outdoor 100 m race in days 1–30 after prediction. Only fully elapsed windows count; unconfirmed UNKNOWN and noncomparable outdoor results require review. Same-day records on the prediction date are excluded. No race observed is not a poor result.",
            "- Each of the 15 nonempty subsets of 60 m spikes, 40 m shoes, standing long jump and backward 2 kg ball throw is evaluated separately. A subset requires only its own inputs, never a prior 100 m race. Latest strictly prior test is selected, using the best same-day attempt and current test direction. No test-age cutoff or imputation is applied.",
            "- Ordinary least squares includes an intercept. For each held-out prediction, training uses only other athletes and outcomes with entire windows completed before the prediction date. Each fit requires more distinct training athletes than coefficients and a full-rank design. Standardization and the mean baseline use only the same training fold.",
            "- The mean baseline and test model within each feature set and cohort use identical held-out cases. Different subsets have different cohorts and must not be ranked directly. The no_previous_100m cohort is separately reported where possible; it is not equivalent to a never-before-seen athlete at deployment.",
            "- Repeated prediction dates, training examples and overlapping race windows are not independent; example-weighted MAE may overrepresent frequent athletes. Athlete-weighted MAE averages each athlete's mean absolute error. Models are exploratory; multiple combinations and tiny samples can produce misleading comparisons.",
            "- No calibration, model selection, automatic fallback, prediction interval, persisted model, athlete-level export or production integration is provided. Skipped fits are reported; they do not mean a test is useless. Wind is not adjusted, and historical entry/correction times and competition completeness are unknown.",
            "", "## Coverage and skipped fits", "", _markdown_table(study.coverage, limit=len(study.coverage)),
            "", "## Matched held-out errors in seconds", "", _markdown_table(study.metrics.round(4), limit=len(study.metrics)),
            "", "Do not infer reliable individual predictions from these results.", "",
        ]
        (output / "test_only_report.md").write_text("\n".join(text), encoding="utf-8")
    except (ValueError, OSError, SQLAlchemyError) as exc:
        logging.error("%s", exc)
        return 1
    logging.info("Test-only report: %s", output / "test_only_report.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
