# TFHUB ML data audit

This is an isolated Python project for local, read-only analysis of TFHUB data. It does not integrate with Spring Boot or Angular, run on the production Raspberry Pi, train models, or modify the database. The current audit reads athlete identifiers and demographics (not names), training-group counts, test metadata, and test-result rows.

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

If PowerShell blocks virtual-environment activation, run `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser` once, then activate again. Keep `.env` local; it is ignored by Git. Set `DB_USER` to the dedicated `tfhub_ml_reader` account, never the application's database account.

Run unit tests without a database connection:

```powershell
python -m pytest
```

## Production database access

The production Compose configuration identifies the database service as `db` on port `5432`, attached to an internal Docker network. It publishes no PostgreSQL port. The production database name is `TFHUB_DB_NAME` from the Pi's `.env.production`, which is not present in this repository. Do not copy production credentials into this project.

The Windows machine has the OpenSSH client installed, but this repository does not identify the Pi's SSH host/IP or login username. Remote SSH availability has therefore **not** been verified. Also, Docker's `db` service name is resolvable inside the Compose network, not necessarily by `sshd` on the Pi host, so forwarding to `db` directly is not assumed to work.

After you confirm SSH access and the Pi host/login, obtain the current database container address on the Pi from the repository root:

```sh
docker compose -f docker-compose.prod.yml ps -q db
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
GRANT SELECT ON TABLE
    public.athletes,
    public.training_groups,
    public.tests,
    public.test_results
TO tfhub_ml_reader;
```

Set the password interactively in `psql` with `\password tfhub_ml_reader`; do not put it in a command, script, repository file, or message. No administrative statements are run automatically. This audit also requests PostgreSQL's read-only transaction mode and marks its extraction transaction `READ ONLY`; the role's limited grants are the primary safeguard.

## Run the audit

With the tunnel open and local `.env` complete, from `ml/`:

```powershell
.\.venv\Scripts\Activate.ps1
python scripts\analyze_data.py
```

The audit fails with a clear error if configuration or database access is unavailable. It never substitutes sample results. Each successful run creates a new UTC timestamped folder in `reports\`; generated reports and datasets are ignored by Git. Outputs include a Markdown report, name-free CSV audit tables, and PNG charts. Candidate cross-test comparison windows are reported at 7, 14, 30, and 60 days; no default window is selected. Correlations report method, paired observations, distinct athletes, and the window, and use at most one nearest-in-time pair per athlete/test pair/window.

## Scope and future extension

Current extraction is limited to athletic test data. Tempo training executions and official competition results are separate possible future data sources; neither is extracted or integrated here. Keep raw source-specific extraction separate from later prediction dataset generation. Any future prediction example must use only information available on or before its prediction date and target a result measured afterward. No prediction models or services are part of this project.

The audit reports descriptive coverage and flags potential data issues for review only. It does not infer universal athletic performance limits, modify suspicious data, or treat repeated results from one athlete as independent athletes in cross-test correlations.
