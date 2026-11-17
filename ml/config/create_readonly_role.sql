-- Run manually on the Raspberry Pi with a PostgreSQL administrator account.
-- Do not run this from the ML project or from its read-only audit process.
-- Replace <production_database_name> with the value configured by
-- TFHUB_DB_NAME in the Pi's .env.production file.

-- In psql connected to the production database:
CREATE ROLE tfhub_ml_reader LOGIN;
\password tfhub_ml_reader

-- Replace the placeholder before executing:
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

-- The role is intentionally not a table owner and receives no write grants.
