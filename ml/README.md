# TFHUB ML data audit

This is an isolated Python project for local, read-only analysis of TFHUB data. It does not integrate with Spring Boot or Angular, run on the production Raspberry Pi, or modify the database. A separate experimental study fits models temporarily for evaluation; it does not deploy or persist a model. The audit reads athlete identifiers and demographics (not names), training-group counts, test metadata and results, and competition event and result fields.

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

## Experimental season study

The approved initial experiment compares the training-fold mean with a one-feature ordinary least-squares line using the latest 60 m-in-spikes test and leave-one-athlete-out evaluation. Run from `ml/`, with a verified event ID, test ID, complete season year and reviewed confirmation file:

```powershell
.\.venv\Scripts\python.exe scripts\experiment_60m_season.py --event-id 4 --test-id 6 --year 2026 --confirmed-unknown-outdoor-ids reports\confirmed_outdoor_60m_2026.csv
```

The result is an ignored, timestamped `reports\season_experiment_*` directory with aggregate held-out error statistics and the reviewed result IDs. Training and validation use separate athletes within the same season; neither athlete-level examples, model files nor individual predictions are exported. Optional `--as-of YYYY-MM-DD` sets a date cutoff but cannot recreate a historical database snapshot. The study is not a future-season validation, and historical data entry and correction dates are unavailable. The observed errors are not prediction intervals and do not establish that the experiment generalizes. Verify IDs and source data in each environment; the tool refuses unmatched event or test metadata.

## Scope and future extension

Athletic test results and official competition results are extracted as separate sources; tempo training executions are not included. No test-to-event equivalence is inferred from names, units, timing, or shared athletes; review event definitions, relay context, seasons, and units with the coach before mapping. Any future prediction example must use only information available on or before its prediction date and target a result measured afterward. Aside from the explicit local experimental study above, no production model or service is part of this project.

The audit reports descriptive coverage and flags potential data issues for review only. It does not infer universal athletic performance limits, modify suspicious data, or treat repeated results from one athlete as independent athletes in cross-test correlations.
