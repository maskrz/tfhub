# Experimental outdoor 100 m models

## Status and purpose

This workspace preserves research code and fitted weights for two 100 m questions. It is **not a production service or a validated model for athlete decisions**. It does not change the application, database, schema, deployment or any race classification. The results are point estimates, not ranges; neither a prediction interval nor uncertainty calibration is available.

The 2026-11-24 snapshot produced trainable subsets for both questions. In the chronological evaluation, the next-30-day 60 m-in-spikes model had four held-out dates from two athletes, and **no evaluated case had no prior 100 m race**. All season-best subsets had **zero** chronologically evaluable dates. In particular, fitting season models does not establish that they forecast a future season or generalize. More data and independent evaluation are necessary.

## Questions and labels

Both use recorded outdoor 100 m times in seconds, unadjusted for wind. An athlete with no recorded race in the relevant period has no observed label, not a slow label.

1. `next_30_days`: the fastest comparable recorded race on days 1–30 **after** the prediction date. A race on the prediction date is excluded because its ordering is unknown. A full window must have elapsed.
2. `season_best`: the fastest comparable recorded race from May 1 through October 31 of the prediction year. This includes races already recorded strictly before the prediction date and later races. Only completed seasons are used for training. A same-prediction-day outdoor race, an unconfirmed UNKNOWN race or a noncomparable outdoor race causes the corresponding season example to require review. The date must be before October 31; this is a calendar-year season, not a rolling 12-month forecast.

Near-term windows with unconfirmed UNKNOWN or invalid outdoor results are also set aside for review. Only the exact previously reviewed IDs supplied in the confirmation file may supplement stored `OUTDOOR` classifications, and IDs are validated against the event and snapshot. Other unknown records are not silently assumed outdoor. No wind adjustment or prediction of future wind is included.

## Examples and inputs

Research prediction dates are the day after each distinct test or 100 m race date. For each of the four reviewed tests, select its latest result **strictly before** that date; if several valid attempts occur on that latest date, use the best according to the test's direction. If any latest-day attempts are invalid, that feature is missing; older valid results are not silently substituted. There is no maximum age cutoff.

| Feature | Verified test | Unit | Better |
| --- | --- | --- | --- |
| `spikes_60m` | 60m w kolcach (ID 6) | s | lower |
| `shoes_40m` | 40m w butach (ID 2) | s | lower |
| `standing_long_jump` | skok w dal z miejsca (ID 3) | m | higher |
| `ball_throw_2kg_backward` | piłka lekarska 2kg w tył (ID 4) | m | higher |

Training verifies these exact definitions and that the selected competition event is 100 m in seconds. Other tests, demographics, names, prior races and training sessions are not model features. Athlete IDs are used to group and exclude athletes during evaluation, not as features. The season-best post-processing described below can use a prior season race separately.

The season examples use the same test/race anchor dates and features as the near-term study, but with a one-day anchor-building horizon so eligible late-season anchors are not lost. They are then labeled against the full season; in-season labels may already be known and repeated dates can reuse the same race. An arbitrary prediction date is allowed by the local prediction tool, but was not separately validated by this anchor-based evaluation.

## Fitting and missing tests

Each target has up to 15 separate ordinary least-squares models, one per nonempty subset of the four tests. A subset trains only on observed, fully matured examples with every feature in that subset present. No values are imputed and no previous 100 m race is required. A fit requires **more distinct athletes than coefficients** (including the intercept), nonconstant features and a full-rank design. If these conditions fail, the bundle records the reason and does not contain that fit. These minimum checks are not a guarantee of statistical reliability.

Each feature is standardized using its training-fold mean and population standard deviation. For ordered feature vector `x`, the prediction is `coefficients[0] + sum(coefficients[i+1] * (x[i] - center[i]) / scale[i])`. The JSON artifact stores the order, means, scales, coefficients, training ranges, sample counts and target mean. Training dates and overlapping target windows can be repeated; ordinary least squares weights each example equally, so frequently observed athletes can dominate. Coefficients describe pooled associations, **not** causal effects or reliable within-athlete change effects. This version cannot learn the desired individual improvement relationship from the very limited repeated-athlete data.

At inference, the caller explicitly provides one exact subset. The tool does not automatically select a model, drop a missing feature or claim a larger subset is better. If the chosen subset has no fit, it fails rather than falling back silently. The returned list of values outside the training ranges signals extrapolation; being inside those ranges does not prove a prediction is reliable. Unusually old test inputs are also possible and should be reviewed.

For `season_best`, the caller may supply the fastest valid comparable outdoor 100 m already recorded **strictly before** the prediction date in the same May–October season. The returned estimate is the smaller of the raw test-model estimate and this known best, because the final best cannot be slower than a result already achieved. This is post-processing, not a model trained with that known race as an input. An omitted or incorrect known best may produce a misleading estimate. All relevant unknown, invalid or same-day marks must be reviewed by the caller; this local tool cannot validate a manually supplied race history. It reports both the raw and adjusted estimates.

## Evaluation

For each historical prediction date and subset, fit a fresh model using only **other athletes** whose full target windows ended strictly before that date. Use the same training rows for a training-fold mean baseline and compare both on exactly the same held-out cases. For season best, apply the known-best post-processing to both estimates when known. Require the same fit checks as above; skipped folds are counted. The fitted full-snapshot model is separate from these temporary evaluation fits.

The artifact records eligible and evaluated dates and athletes, skipped reasons, example-weighted MAE, athlete-weighted MAE, median and maximum absolute error. Repeated dates and reused races are not independent; models with different feature subsets can have different training sets and held-out cohorts, so do not rank them from unmatched aggregate scores. For near-term evaluation, the number of evaluated dates without a prior race is recorded. A fold with an invalid nonpositive model prediction is explicitly counted as skipped for both model and matched mean, not counted as a successful prediction.

Model evaluation remains constrained by selective competition participation, incomplete imports, wind, multiple comparisons, sparse data and current rather than historical metadata. A performance date does not establish when the data was entered or later corrected. `--as-of` limits dates but does not restore an earlier database snapshot. Even chronological evaluation therefore cannot prove what was actually known at the time. No season-best model currently has a chronologically evaluable fold.

## Train locally

Follow the setup and read-only tunnel instructions in the [ML README](../README.md). From `ml\`, using your own reviewed confirmation file:

```powershell
.\.venv\Scripts\python.exe scripts\train_100m_models.py --event-id 1 --confirmed-unknown-outdoor-ids reports\100m_readiness_20261124T171700749006Z\confirmed_outdoor_result_ids.csv
```

Each run reads a single repeatable-read, explicitly read-only database transaction and creates a new ignored `model_artifacts\100m_<UTC timestamp>\` directory. `model.json` contains both targets, fits, preprocessing, coverage and evaluation results. The reviewed IDs are copied into that directory, with a SHA-256 digest in the bundle. Python, NumPy and pandas versions, training cutoff, event ID, creation time and verified test definitions are recorded. The cutoff defaults to the local current date and can be set using `--as-of YYYY-MM-DD`, but cannot be in the future.

The model file and confirmation IDs are **sensitive local artifacts** even though they contain no athlete names: coefficients and small-group counts can expose population information, and result IDs identify records. Do not commit or share them without authorization. The directory is Git-ignored. Use an appropriately protected local backup if persistence beyond this computer is required; Git alone will not preserve it.

To reproduce a run exactly, retain the exact code revision, dependency versions, confirmation file and a securely held consistent source snapshot under an approved data-handling process. This tool deliberately does not export raw source data. The dependencies are specified as version ranges, and a later database snapshot can include corrections or backfilled records, so rerunning the command with the same cutoff is **not** an exact reproduction.

## Make a local research prediction

Prepare a private JSON file under ignored `datasets\`, using actual verified latest attempts, correct units and dates. For illustration only, this is synthetic data, not a recommended athlete result:

```json
{
  "target": "season_best",
  "prediction_date": "2027-06-02",
  "features": {
    "spikes_60m": {"date": "2027-05-30", "value": 8.5},
    "standing_long_jump": {"date": "2027-05-30", "value": 2.2}
  },
  "known_season_best": {"date": "2027-05-20", "value": 14.5}
}
```

For `next_30_days`, set that target and omit `known_season_best`. For a season estimate without an established best, omit it. Input dates must be ISO dates; each test date must be strictly before the prediction date. The tool expects the caller to choose the best valid attempt on the latest date for each test. A known best must be a positive, strictly prior result in the current season. Prediction date must be after the model's training cutoff, preventing use of the full-snapshot fit as a historical backtest. The model accepts positive finite measurements; it cannot check units or actual source data against the database.

```powershell
.\.venv\Scripts\python.exe scripts\predict_100m.py --model model_artifacts\<saved-run>\model.json --input datasets\<private-input>.json
```

Replace placeholders with actual paths. Output is printed to the terminal, including a point estimate, raw estimate, input subset, training/evaluation counts and extrapolation warning. Do not save or share athlete-level inputs or outputs without authorization. No database or application is accessed during prediction.

## Future application work

Before any athlete-page integration, agree on the product semantics and architecture, collect more repeated tests and race results across athletes and seasons, establish historically available data and independent chronological validation, assess subgroups and missing tests, and calibrate uncertainty ranges. Decide how to handle same-day ordering, stale tests, unresolved race classifications, wind, missing competitions, model selection, retraining, monitoring and authorization. Retrospective data cannot establish that updating a test causes an athlete's result to change. The future application must separately implement data access and security, select and validate model versions, refresh predictions when inputs change, and visibly communicate uncertainty. No API, automated update mechanism or application integration is provided here.

See the [model expansion guide](model-expansion-guide.md) for next-season collection recommendations and future steps for adding tests, events and tempo execution data.
