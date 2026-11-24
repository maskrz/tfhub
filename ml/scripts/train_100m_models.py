"""Train local experimental 100 m model bundles from a read-only snapshot."""

import argparse
import hashlib
import json
import logging
import platform
import sys
from datetime import date, datetime, timezone
from pathlib import Path

import numpy as np
import pandas as pd
from sqlalchemy.exc import SQLAlchemyError

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from tfhub_ml.analysis.saved_models import build_training_examples, train_and_evaluate
from tfhub_ml.config.settings import load_settings
from tfhub_ml.data.confirmation import load_confirmed_outdoor_ids
from tfhub_ml.data.extract import read_audit_data
from tfhub_ml.database.connection import create_read_only_engine


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event-id", type=int, required=True)
    parser.add_argument("--confirmed-unknown-outdoor-ids", type=Path, required=True)
    parser.add_argument("--as-of", type=date.fromisoformat, default=date.today())
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    try:
        if args.as_of > date.today():
            raise ValueError("The training cutoff cannot be in the future.")
        confirmed = load_confirmed_outdoor_ids(args.confirmed_unknown_outdoor_ids)
        settings = load_settings(PROJECT_ROOT / ".env")
        engine = create_read_only_engine(settings)
        try:
            data, competition = read_audit_data(engine, settings)
        finally:
            engine.dispose()
        examples = build_training_examples(data, competition, args.event_id, args.as_of, confirmed)
        bundle = train_and_evaluate(examples, args.as_of, args.event_id)
        if any(not any(item["fit"] is not None for item in bundle["targets"][target].values())
               for target in bundle["targets"]):
            raise ValueError("At least one target has no trainable feature subsets; no bundle was written.")
        now = datetime.now(timezone.utc)
        bundle["created_at_utc"] = now.isoformat()
        bundle["environment"] = {"python": platform.python_version(), "numpy": np.__version__, "pandas": pd.__version__}
        confirmed_csv = "result_id\n" + "".join(f"{value}\n" for value in sorted(confirmed))
        bundle["confirmed_ids_sha256"] = hashlib.sha256(confirmed_csv.encode("utf-8")).hexdigest()
        output = PROJECT_ROOT / "model_artifacts" / ("100m_" + now.strftime("%Y%m%dT%H%M%S%fZ"))
        output.mkdir(parents=True, exist_ok=False)
        (output / "confirmed_outdoor_result_ids.csv").write_text(confirmed_csv, encoding="utf-8")
        (output / "model.json").write_text(json.dumps(bundle, indent=2, ensure_ascii=False, allow_nan=False) + "\n", encoding="utf-8")
    except (ValueError, OSError, SQLAlchemyError) as exc:
        logging.error("%s", exc)
        return 1
    logging.info("Experimental model bundle: %s", output / "model.json")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
