"""Make an experimental local prediction using an explicitly selected test subset."""

import argparse
import json
import logging
import sys
from datetime import date
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from tfhub_ml.analysis.saved_models import predict


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--input", type=Path, required=True)
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    try:
        bundle = json.loads(args.model.read_text(encoding="utf-8"))
        request = json.loads(args.input.read_text(encoding="utf-8"))
        known = request.get("known_season_best")
        known_best = (date.fromisoformat(known["date"]), float(known["value"])) if known is not None else None
        result = predict(bundle, request["target"], request["features"],
                         date.fromisoformat(request["prediction_date"]), known_best)
        print(json.dumps(result, indent=2, allow_nan=False))
    except (ValueError, TypeError, KeyError, OSError) as exc:
        logging.error("Prediction failed: %s", exc)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
