\set ON_ERROR_STOP on
\getenv app_user TFHUB_DB_USER
\getenv app_password TFHUB_DB_PASSWORD
\getenv app_database POSTGRES_DB

CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password';
ALTER DATABASE :"app_database" OWNER TO :"app_user";
