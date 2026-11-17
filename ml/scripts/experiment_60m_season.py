"""Run an experimental 60 m season study without exporting athlete-level data."""

import argparse
import logging
import sys
from datetime import date, datetime, timezone
from pathlib import Path

from sqlalchemy.exc import SQLAlchemyError

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from tfhub_ml.analysis.season_experiment import run_experiment
from tfhub_ml.config.settings import load_settings
from tfhub_ml.data.confirmation import load_confirmed_outdoor_ids
from tfhub_ml.data.extract import read_audit_data
from tfhub_ml.database.connection import create_read_only_engine
from tfhub_ml.reporting.generate import _markdown_table


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event-id", type=int, required=True)
    parser.add_argument("--test-id", type=int, required=True)
    parser.add_argument("--year", type=int, required=True)
    parser.add_argument("--as-of", type=date.fromisoformat, default=date.today())
    parser.add_argument("--confirmed-unknown-outdoor-ids", type=Path)
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    try:
        confirmed = (
            load_confirmed_outdoor_ids(args.confirmed_unknown_outdoor_ids)
            if args.confirmed_unknown_outdoor_ids else frozenset()
        )
        settings = load_settings(PROJECT_ROOT / ".env")
        engine = create_read_only_engine(settings)
        try:
            data, competition = read_audit_data(engine, settings)
        finally:
            engine.dispose()
        result = run_experiment(data, competition, args.event_id, args.test_id, args.year, args.as_of, confirmed)
        output = PROJECT_ROOT / "reports" / (
            "season_experiment_" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
        )
        output.mkdir(parents=True, exist_ok=False)
        result.metrics.to_csv(output / "metrics.csv", index=False, float_format="%.6f")
        text = [
            "# Experimental outdoor 60 m season study",
            "",
            f"- Snapshot date: {args.as_of}; season: {args.year}; competition event ID: {args.event_id}; 60 m in spikes test ID: {args.test_id}.",
            f"- Eligible athlete-seasons: {result.candidate_count}; missing selected test: {result.missing_input_count}; evaluated distinct athletes: {result.athlete_count}.",
            f"- Confirmed UNKNOWN result IDs applied: {len(confirmed)}; selected athletes with additional unconfirmed UNKNOWN season results: {result.unconfirmed_unknown_athletes}. IDs are recorded separately for reproducibility.",
            "- Prediction date: the last test of any kind before May 1. Input: latest 60 m in spikes test before May 1, using the fastest same-day attempt. Target: best comparable outdoor 60 m result from May 1 to October 31 in the same completed season.",
            "- Evaluation: leave one athlete out, fit the mean baseline and an ordinary least-squares line only on the remaining athletes, and compare the held-out prediction with the observed season best. Signed error is prediction minus observed time.",
            "- Estimates and errors are in seconds. Each fold uses a different fit; aggregate errors are not prediction intervals or guarantees. No model file, athlete-level data, or athlete-level predictions are exported.",
            "- The study is a preliminary within-season check, not validation on a future season. Tiny sample size, retrospective entry/correction uncertainty, incomplete competition records, and possible test metadata changes limit interpretation; the confirmed UNKNOWN assumption applies only to the reviewed IDs.",
            "",
            "## Leave-one-athlete-out error",
            "",
            _markdown_table(result.metrics.round(4)),
            "",
            "A lower error on this small sample does not establish a reliable predictor. Review errors, feature choice and data provenance before any individual prediction. Do not extrapolate these results to another season or population.",
            "",
        ]
        if confirmed:
            (output / "confirmed_outdoor_result_ids.csv").write_text(
                "result_id\n" + "".join(f"{value}\n" for value in sorted(confirmed)), encoding="utf-8"
            )
        (output / "experiment_report.md").write_text("\n".join(text), encoding="utf-8")
    except (ValueError, SQLAlchemyError, OSError) as exc:
        logging.error("%s", exc)
        return 1
    logging.info("Experiment report: %s", output / "experiment_report.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
