#!/usr/bin/env bash
# Manual TFHub deployment for the Raspberry Pi.
#
# Usage:
#   ./deploy.sh            deploy the latest commit of origin/main
#   ./deploy.sh <commit>   deploy a specific commit (any commit; schema compatibility is your responsibility)
#
# The script never removes volumes and never restores the database automatically.
set -Eeuo pipefail

ENV_FILE=".env.production"
COMPOSE_FILE="docker-compose.prod.yml"
BACKUP_DIR="${TFHUB_BACKUP_DIR:-$HOME/tfhub-backups}"
MIN_FREE_MB=3000
HEALTH_TIMEOUT_SECONDS=300
MIGRATION_DIR="tfhub-api/src/main/resources/db/migration"

log() { printf '\n==> %s\n' "$*"; }
die() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }

if [ "$#" -gt 1 ]; then
  die "Usage: ./deploy.sh [commit]"
fi
REQUESTED_REF="${1:-}"

cd "$(dirname "${BASH_SOURCE[0]}")"

exec 9>.deploy.lock
flock -n 9 || die "Another deployment is already running."

if docker info >/dev/null 2>&1; then
  DOCKER=(docker)
else
  DOCKER=(sudo docker)
fi
COMPOSE=("${DOCKER[@]}" compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")

PREVIOUS_COMMIT="$(git rev-parse HEAD)"
BACKUP_FILE=""

on_error() {
  local line="$1"
  cat >&2 <<EOF

DEPLOYMENT FAILED (line $line). Nothing has been rolled back automatically.
  Previous commit : $PREVIOUS_COMMIT
  Database backup : ${BACKUP_FILE:-none created}
Inspect with:
  ${COMPOSE[*]} ps
  ${COMPOSE[*]} logs --tail=100 api db web
Decide on any rollback deliberately: re-running ./deploy.sh $PREVIOUS_COMMIT is only safe if
that version is compatible with the current database schema. The database volume was not touched.
EOF
}
trap 'on_error $LINENO' ERR

log "Pre-flight checks"
[ -f "$ENV_FILE" ] || die "$ENV_FILE is missing."
[ -f "$COMPOSE_FILE" ] || die "$COMPOSE_FILE is missing."
[ -z "$(git status --porcelain --untracked-files=no)" ] || die "Tracked files have local changes; commit or discard them first."

free_mb="$(df -Pm . | awk 'NR==2 {print $4}')"
[ "$free_mb" -ge "$MIN_FREE_MB" ] || die "Only ${free_mb} MB free; at least ${MIN_FREE_MB} MB required."

log "Fetching from GitHub"
git fetch --prune origin

if [ -z "$REQUESTED_REF" ]; then
  TARGET_COMMIT="$(git rev-parse --verify origin/main^{commit})"
  TARGET_DESC="latest origin/main"
else
  TARGET_COMMIT="$(git rev-parse --verify "${REQUESTED_REF}^{commit}" 2>/dev/null)" \
    || die "Commit '$REQUESTED_REF' not found."
  TARGET_DESC="requested commit $REQUESTED_REF"
fi

printf '\nCurrent : %s\nTarget  : %s (%s)\n' \
  "$(git log -1 --format='%h %s' "$PREVIOUS_COMMIT")" \
  "$(git log -1 --format='%h %s' "$TARGET_COMMIT")" "$TARGET_DESC"

if [ "$PREVIOUS_COMMIT" = "$TARGET_COMMIT" ]; then
  echo "Target equals the currently checked-out commit; the images will still be rebuilt and checked."
fi

MIGRATIONS="$(git diff --name-only "$PREVIOUS_COMMIT" "$TARGET_COMMIT" -- "$MIGRATION_DIR" || true)"
if [ -n "$MIGRATIONS" ]; then
  printf '\nWARNING: Flyway migrations changed and will run on API startup (not reversible by restarting old code):\n%s\n' "$MIGRATIONS"
fi

if [ "${TFHUB_DEPLOY_YES:-}" != "1" ]; then
  read -r -p "Continue? [y/N] " answer
  [ "$answer" = "y" ] || [ "$answer" = "Y" ] || die "Aborted by user."
fi

log "Database backup"
if [ -n "$("${COMPOSE[@]}" ps --status running -q db)" ]; then
  mkdir -p "$BACKUP_DIR"
  chmod 700 "$BACKUP_DIR"
  BACKUP_FILE="$BACKUP_DIR/tfhub-$(date +%Y%m%d-%H%M%S)-${PREVIOUS_COMMIT:0:7}.dump"
  (
    umask 077
    "${COMPOSE[@]}" exec -T db sh -c \
      'PGPASSWORD="$TFHUB_DB_PASSWORD" pg_dump --format=custom --no-owner --no-acl --username="$TFHUB_DB_USER" "$POSTGRES_DB"' \
      > "$BACKUP_FILE"
  )
  [ -s "$BACKUP_FILE" ] || die "Backup file is empty."
  "${COMPOSE[@]}" exec -T db pg_restore --list < "$BACKUP_FILE" >/dev/null \
    || die "Backup could not be read back by pg_restore."
  echo "Backup verified: $BACKUP_FILE"
else
  echo "Database container is not running (first deployment?); no backup created."
fi

log "Checking out target commit"
if [ -z "$REQUESTED_REF" ]; then
  git checkout main
  git merge --ff-only "$TARGET_COMMIT"
else
  git checkout --detach "$TARGET_COMMIT"
fi

log "Validating Compose configuration"
"${COMPOSE[@]}" config -q

log "Keeping previous images as :previous"
for image in tfhub-prod-api tfhub-prod-web; do
  if "${DOCKER[@]}" image inspect "$image:latest" >/dev/null 2>&1; then
    "${DOCKER[@]}" tag "$image:latest" "$image:previous"
  fi
done

log "Building images (running containers are untouched until this succeeds)"
"${COMPOSE[@]}" build

log "Updating services and waiting for health checks"
"${COMPOSE[@]}" up -d --wait --wait-timeout "$HEALTH_TIMEOUT_SECONDS"

log "Smoke test"
HTTP_PORT="$(grep -E '^TFHUB_HTTP_PORT=' "$ENV_FILE" | tail -n1 | cut -d= -f2- | tr -d '[:space:]"' || true)"
HTTP_PORT="${HTTP_PORT:-8081}"
curl -fsS -o /dev/null "http://127.0.0.1:${HTTP_PORT}/"
curl -sS -o /dev/null -w 'GET /api/auth/session without login -> HTTP %{http_code} (expected 401)\n' \
  "http://127.0.0.1:${HTTP_PORT}/api/auth/session" || true

trap - ERR
log "Deployment succeeded"
"${COMPOSE[@]}" ps
echo "Deployed: $(git log -1 --format='%h %s')"
echo "Previous: ${PREVIOUS_COMMIT:0:7}   Backup: ${BACKUP_FILE:-none}"
