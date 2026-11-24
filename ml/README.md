# TFHUB ML data audit

This is an isolated Python project for local, read-only analysis of TFHUB data. It does not integrate with Spring Boot or Angular, run on the production Raspberry Pi, or modify the database. Exploratory 100 m models can be trained and saved locally, but are not validated for production or individual decisions. The audit reads athlete identifiers and demographics (not names), training-group counts, test metadata and results, and competition event and result fields.

## Requirements and setup (Windows PowerShell)

Use Python 3.11 or newer and Windows OpenSSH Client. From the repository root:

```powershell
Set-Location .\ml
python --version
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
Copy-Item .env.example .env
notepad .env
```

If PowerShell blocks activation, use `.\.venv\Scripts\python.exe` directly instead of `python`; there is no need to change the execution policy. Keep `.env` local; it is ignored by Git. Set `DB_USER` to the dedicated `tfhub_ml_reader` account, never the application's database account.

Run unit tests without a database connection:

```powershell
python -m pytest
```

## Production database access

The production Compose configuration identifies the database service as `db` on port `5432`, attached to an internal Docker network. It publishes no PostgreSQL port. The production database name is `TFHUB_DB_NAME` from the Pi's `.env.production`, which is not present in this repository. Do not copy production credentials into this project.

This repository does not identify the Pi's SSH host/IP or login username. Verify SSH availability in your environment. Docker's `db` service name is resolvable inside the Compose network, not necessarily by `sshd` on the Pi host, so forwarding to `db` directly is not assumed to work.

After you confirm SSH access and the Pi host/login, obtain the current database container address on the Pi from the repository root:

```sh
docker compose --env-file .env.production -f docker-compose.prod.yml ps -q db
docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' <database-container-id>
```

The Compose stack uses a private network and does not publish this address. Confirm that the Pi host can route to the returned container IP; the address can change when the container is recreated. Once that host-reachable address and SSH destination are verified, the Windows tunnel command is:

```powershell
ssh -N -L 127.0.0.1:15432:<database-container-ip>:5432 <ssh-user>@<pi-host>
```

Keep that terminal open while the audit runs. The local `.env` connection settings should then be:

```dotenv
DB_HOST=127.0.0.1
DB_PORT=15432
DB_NAME=<value-of-TFHUB_DB_NAME>
DB_USER=tfhub_ml_reader
DB_PASSWORD=<locally-entered-read-only-password>
DB_SCHEMA=public
```

Values in angle brackets are placeholders, not literal settings. Do not run the audit until the tunnel target is confirmed, the dedicated role is provisioned, and the database name and role credentials have been entered locally. If the Pi host cannot route to the container IP, stop; do not publish a production database port or change production Compose as a workaround.

`MATCH_WINDOWS_DAYS` in `.env` controls the candidate cross-test comparison windows. It defaults to `7,14,30,60` to show how available pairs change across several cutoffs; this is not a selected business rule. Change the list to review other positive day counts. Correlations are calculated separately for each configured window.

### Provision a dedicated SELECT-only account

An administrator must perform this manually on the Pi against the production database. Use the actual database name from `.env.production`. The statements in [`config/create_readonly_role.sql`](config/create_readonly_role.sql) document the role and grants; the script is intentionally not invoked by this project. The administrator should verify that the role is new/dedicated and is not a member of any role with broader privileges before granting access.

The required direct permissions are:

```sql
CREATE ROLE tfhub_ml_reader LOGIN;
GRANT CONNECT ON DATABASE <production_database_name> TO tfhub_ml_reader;
GRANT USAGE ON SCHEMA public TO tfhub_ml_reader;
GRANT SELECT (id, birth_year, gender, active, training_group_id)
    ON public.athletes TO tfhub_ml_reader;
GRANT SELECT (id) ON public.training_groups TO tfhub_ml_reader;
GRANT SELECT ON public.tests, public.test_results TO tfhub_ml_reader;
GRANT SELECT (id, athlete_id, event_id, result_date, numeric_value, unit, season_type)
    ON public.competition_results TO tfhub_ml_reader;
GRANT SELECT (id, name, unit, active)
    ON public.competition_events TO tfhub_ml_reader;
```

Set the password interactively in `psql` with `\password tfhub_ml_reader`; do not put it in a command, script, repository file, or message. No administrative statements are run automatically. This audit also requests PostgreSQL's read-only transaction mode and marks its extraction transaction `READ ONLY`; the role's limited grants are the primary safeguard.

## Run the audit

With the tunnel open and local `.env` complete, from `ml/`:

```powershell
.\.venv\Scripts\Activate.ps1
python scripts\analyze_data.py
```

The audit fails with a clear error if configuration, either source table, or its required permissions are unavailable. It never substitutes sample results. Each successful run creates a new UTC timestamped folder in `reports\`; generated reports and datasets are ignored by Git. Outputs include a Markdown report, name-free CSV audit tables, and PNG charts. Candidate comparison windows default to 7, 14, 30, and 60 days; no final window is selected. Test-to-competition coverage is descriptive, not an approved mapping or correlation: `competition_gaps.csv` records the closest pair per athlete and test/event, and `competition_windows.csv` counts all qualifying pairs and distinct athletes. A positive signed gap means the test came first; same-day rows have unknown ordering. Individual result pairs may be reused, so pair counts are not independent samples. Competition results' stored units are historical snapshots, and missing numeric values can be legitimate non-numeric marks.

## Outdoor 60 m season feasibility

Verify the 60 m competition event ID in the general audit output, then run from `ml/`:

```powershell
.\.venv\Scripts\python.exe scripts\assess_60m_season.py --event-id 4
```

The explicit ID is checked against the event name `60 m` and unit `s`; use the ID from the database being audited. Optional `--as-of YYYY-MM-DD` sets the calendar cutoff for reproducible date-based counts, but cannot restore the database to its historical state.

The output is a separate ignored `reports\season_feasibility_*` directory containing aggregate season coverage, input test coverage, prediction-date ages and a Markdown assessment. It considers every current athlete for each year from the first observed 60 m result to the audit year; this does not establish historical roster membership. Prediction dates are the last test of any kind before May 1; each test contributes its most recent result, with the best attempt selected using current test direction when dates tie. The target is the lowest positive numeric outdoor 60 m time in seconds from May 1 through October 31. Only calendar-complete seasons count as eligible. Unknown season classifications are excluded and reported; outdoor marks missing a comparable numeric time cause that athlete-season to be flagged for review rather than assumed valid.

Dates in source records are performance dates, not proof that the data or current metadata existed at the prediction date. Corrections, incomplete competition records, and unknown season classifications can affect the true season best. These descriptive counts do not establish predictive performance or chronological data availability; no model is trained and no individual candidate dataset is exported.

For explicitly verified UNKNOWN 60 m records from May–October 2026 only, the optional `--confirmed-unknown-outdoor-ids <csv-path>` accepts a CSV containing a single `result_id` column. Only the exact IDs in this file are treated as outdoor for that run; the tool validates the event, dates and original UNKNOWN status against the snapshot, and records the IDs in the report directory. Other UNKNOWN results remain excluded, and the database is never changed. Store confirmation files under ignored `reports\` and do not assume future imports are covered by an earlier confirmation.

## 100 m readiness audit

With the existing read-only tunnel and permissions, run:

```powershell
.\.venv\Scripts\python.exe scripts\assess_100m_readiness.py --event-id 1
```

Verify the event ID against the database's event catalog; the command checks that it identifies `100 m` in seconds. Optional `--as-of YYYY-MM-DD` sets the calendar cutoff, not a historical snapshot. Output goes to an ignored `reports\100m_readiness_*` directory. This descriptive audit compares candidate windows from `MATCH_WINDOWS_DAYS`; it counts strictly prior measurement dates and future result rows separately for recorded `OUTDOOR` and `UNKNOWN` targets. Same-day ordering is unknown. Repeated race rows and reused source dates are not independent examples. No model is trained and no individual measurements are exported. Wind is not read, record entry and correction dates are unavailable, and no eligibility or forecast window has been approved.

When the coach explicitly confirms that all UNKNOWN 100 m results in a particular snapshot are outdoor, `--confirm-current-unknown-outdoor` includes those exact IDs in an additional outdoor cohort and saves them in the report directory. Use this flag only with fresh confirmation for that snapshot; it must not be used to assume future UNKNOWN records are outdoor. For later runs, `--confirmed-unknown-outdoor-ids <path-to-confirmed_outdoor_result_ids.csv>` reuses the saved exact IDs, validating the event, original classification and cutoff. Recorded classifications and the database remain unchanged.

## Exploratory 30-day 100 m study

Use the reviewed IDs produced by the readiness audit:

```powershell
.\.venv\Scripts\python.exe scripts\experiment_100m.py --event-id 1 --test-id 6 --confirmed-unknown-outdoor-ids reports\100m_readiness_20261124T171700749006Z\confirmed_outdoor_result_ids.csv
```

The study takes the day after each distinct test or 100 m race date as a prediction date and uses the fastest recorded eligible race 1–30 days later as the outcome. It compares a latest prior race baseline with an exploratory 60 m-in-spikes linear model when sufficiently many earlier labels from other athletes are available; it reports matching cohorts, counts and limitations. The report and confirmed IDs are saved in an ignored timestamped `reports\100m_experiment_*` directory. Only full windows are eligible, and unconfirmed or noncomparable target marks cause review. Same-day records on the prediction date are excluded. Results are not calibrated intervals or predictions for arbitrary dates; wind, record entry and correction timing remain unverified.

For an explicitly approved local case review, add `--export-baseline-review`. This writes `baseline_review.csv` in the ignored report directory, sorted by absolute baseline error, with athlete IDs, source and outcome result IDs, dates and times; it contains sensitive athlete-level data, but no names. It covers the latest-100 m baseline, not the test-based model. A new run uses the current database snapshot even when `--as-of` is supplied, so later corrections can change historical results. Do not share or commit the file.

For an exploratory wind review, an administrator must separately grant the reader `SELECT (wind)` on `public.competition_results`. Then run:

```powershell
.\.venv\Scripts\python.exe scripts\review_100m_wind.py --event-id 1 --baseline-review reports\100m_experiment_20261124T174728496731Z\baseline_review.csv
```

The command reads wind for the reviewed cases, verifies their source data against the current read-only snapshot, and writes aggregate results to an ignored `reports\100m_wind_review_*` directory. The supplied rule of thumb adjusts both the previous and future race to approximate calm air. Future wind is retrospectively known, so this is not a real-world prediction or an assessment of the 60 m model. Missing wind is reported and excluded. The original fastest recorded race remains the target even if a different race would be fastest after adjustment.

## Tests and future 100 m correlations

```powershell
.\.venv\Scripts\python.exe scripts\correlate_tests_100m.py --event-id 1 --confirmed-unknown-outdoor-ids reports\100m_readiness_20261124T171700749006Z\confirmed_outdoor_result_ids.csv
```

This exploratory analysis uses the same prediction dates and 1–30-day fastest recorded outdoor race target as the 100 m study. Each test is examined separately using its latest strictly prior value and current direction for same-day attempts. Outputs include Pearson and Spearman correlations, sample sizes, distinct athletes, result reuse and test ages in an ignored `reports\100m_test_correlations_*` directory. It does not adjust wind, train a model or export athlete-level measurements. Repeated and overlapping observations are not independent; small sample sizes, multiple comparisons and data-entry timing limit conclusions. `--as-of YYYY-MM-DD` sets a cutoff, not a historical snapshot.

## Test-enhanced 100 m comparison

```powershell
.\.venv\Scripts\python.exe scripts\compare_100m_tests.py --event-id 1 --confirmed-unknown-outdoor-ids reports\100m_readiness_20261124T171700749006Z\confirmed_outdoor_result_ids.csv
```

This local study fits ordinary least-squares models adding each of 60 m spikes, 40 m shoes, standing long jump, and backward 2 kg ball throw to the prior-race input, plus a model using all four. It uses earlier complete windows from other athletes and checks distinct training athlete counts and matrix rank. Each model is compared with the prior-race baseline on identical evaluable dates; only the all-models-common cohort permits cross-model comparison. Reports are aggregate and ignored under `reports\100m_test_comparison_*`; no individual predictions or models are exported. Counts can be too small to evaluate; training outcomes and overlapping windows repeat athletes, inputs may be stale, wind is unadjusted, and historical availability cannot be proven. `--as-of` is a cutoff, not a historical snapshot.

## Within-athlete changes

```powershell
.\.venv\Scripts\python.exe scripts\analyze_100m_changes.py --event-id 1 --confirmed-unknown-outdoor-ids reports\100m_readiness_20261124T171700749006Z\confirmed_outdoor_result_ids.csv
```

This descriptive study compares each test date separately with a next-30-day result and a May–October season best, including already recorded season results in in-season targets. It evaluates earliest and latest eligible test dates in preseason and in-season separately, and compares within-athlete test changes only after the previous outcome window has fully elapsed. Seasonal changes compare separate completed seasons. Its ignored `reports\100m_changes_*` output contains aggregate data only. It does not evaluate arbitrary earlier-year test dates as preseason anchors, train models, estimate causal effects, or establish prediction accuracy; in-season targets may already be known, and wind and historical record availability are unverified.

## Test-only 100 m experiment

```powershell
.\.venv\Scripts\python.exe scripts\experiment_test_only_100m.py --event-id 1 --confirmed-unknown-outdoor-ids reports\100m_readiness_20261124T171700749006Z\confirmed_outdoor_result_ids.csv
```

This exploratory study evaluates all 15 nonempty combinations of the four reviewed tests, without requiring a previous 100 m race. Missing tests are not imputed: each subset needs only its own inputs. Its models use only other athletes and fully matured earlier outcomes; the training-fold mean is evaluated on the same held-out cases. Coverage and matched errors, including the no-prior-race subset, are in an ignored `reports\100m_test_only_*` directory. Feature sets can have different evaluation cohorts, and no model selection, fallback, individual predictions or intervals are implemented. Small samples and repeated examples limit conclusions; wind and historical availability remain unverified. `--as-of` is a cutoff, not a historical snapshot.

## Saved experimental 100 m models

The separate training and prediction tools, model format, detailed methodology, results, safety limitations and future integration considerations are documented in [`docs/100m-models.md`](docs/100m-models.md). They cover both the next-30-day and May–October season-best targets. Training uses the read-only connection and writes an ignored `model_artifacts\` directory. Models and their coefficients may disclose information about the small training population; keep them private. These are research artifacts, not validated or deployable application models.

For next-season data collection and future implementation steps for additional tests, new competition events and tempo execution data, see the [model expansion guide](docs/model-expansion-guide.md). These extensions are not yet implemented.

## Experimental season study

The approved initial experiment compares the training-fold mean with a one-feature ordinary least-squares line using the latest 60 m-in-spikes test and leave-one-athlete-out evaluation. Run from `ml/`, with a verified event ID, test ID, complete season year and reviewed confirmation file:

```powershell
.\.venv\Scripts\python.exe scripts\experiment_60m_season.py --event-id 4 --test-id 6 --year 2026 --confirmed-unknown-outdoor-ids reports\confirmed_outdoor_60m_2026.csv
```

The result is an ignored, timestamped `reports\season_experiment_*` directory with aggregate held-out error statistics and the reviewed result IDs. Training and validation use separate athletes within the same season; neither athlete-level examples, model files nor individual predictions are exported. Optional `--as-of YYYY-MM-DD` sets a date cutoff but cannot recreate a historical database snapshot. The study is not a future-season validation, and historical data entry and correction dates are unavailable. The observed errors are not prediction intervals and do not establish that the experiment generalizes. Verify IDs and source data in each environment; the tool refuses unmatched event or test metadata.

## Scope and future extension

Athletic test results and official competition results are extracted as separate sources; tempo training executions are not included. No test-to-event equivalence is inferred from names, units, timing, or shared athletes; review event definitions, relay context, seasons, and units with the coach before mapping. Any future prediction example must use only information available before its prediction date. Season-best outcomes can include a race already recorded at the prediction date, as described in the model documentation. No production model or service is part of this project.

The audit reports descriptive coverage and flags potential data issues for review only. It does not infer universal athletic performance limits, modify suspicious data, or treat repeated results from one athlete as independent athletes in cross-test correlations.
