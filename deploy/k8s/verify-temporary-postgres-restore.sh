#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
BACKUP_ROOT=${BACKUP_ROOT:-$SCRIPT_DIR/archives}
BACKUP_DIR=${BACKUP_DIR:-}
CONTAINER_NAME=${CONTAINER_NAME:-skillhub-pg-restore-check}
POSTGRES_IMAGE=${POSTGRES_IMAGE:-postgres:16}
RESTORE_DB=${RESTORE_DB:-skillhub_restore_check}
RESTORE_USER=${RESTORE_USER:-restore_user}
RESTORE_PASSWORD=${RESTORE_PASSWORD:-restore_password}
KEEP_CONTAINER=${KEEP_CONTAINER:-false}

info() {
  printf '[INFO] %s\n' "$*"
}

die() {
  printf '[ERROR] %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: deploy/k8s/verify-temporary-postgres-restore.sh

Validates the PostgreSQL dump inside a temporary release archive by restoring it
into an isolated Docker PostgreSQL container and running basic sanity queries.

Environment variables:
  BACKUP_DIR       Path to a specific backup directory. Defaults to the newest directory under deploy/k8s/archives.
  BACKUP_ROOT      Root directory containing archives. Default: deploy/k8s/archives
  CONTAINER_NAME   Docker container name. Default: skillhub-pg-restore-check
  POSTGRES_IMAGE   Docker image. Default: postgres:16
  RESTORE_DB       Temporary restore database name. Default: skillhub_restore_check
  RESTORE_USER     Temporary restore user. Default: restore_user
  RESTORE_PASSWORD Temporary restore password. Default: restore_password
  KEEP_CONTAINER   true/false. Default: false
EOF
}

has_cmd() {
  command -v "$1" >/dev/null 2>&1
}

latest_backup_dir() {
  find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name 'backup-*' | sort | tail -n 1
}

require_file() {
  [[ -f "$1" ]] || die "File not found: $1"
}

abs_path() {
  local path=$1
  [[ -d "$path" ]] || die "Directory not found: $path"
  (
    cd "$path"
    pwd
  )
}

cleanup() {
  if [[ "$KEEP_CONTAINER" == "true" ]]; then
    return
  fi
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
}

docker_pg_exec() {
  docker exec \
    -e PGPASSWORD="$RESTORE_PASSWORD" \
    "$CONTAINER_NAME" \
    "$@"
}

wait_for_postgres() {
  local attempts=0
  until docker_pg_exec pg_isready -h 127.0.0.1 -U "$RESTORE_USER" -d postgres >/dev/null 2>&1; do
    attempts=$((attempts + 1))
    if (( attempts > 60 )); then
      die "Timed out waiting for temporary PostgreSQL container to become ready"
    fi
  done
}

main() {
  case "${1:-}" in
    -h|--help)
      usage
      exit 0
      ;;
  esac

  has_cmd docker || die "Docker is required for restore validation"

  if [[ -z "$BACKUP_DIR" ]]; then
    BACKUP_DIR=$(latest_backup_dir)
  fi

  [[ -n "$BACKUP_DIR" ]] || die "No backup directory found"
  [[ -d "$BACKUP_DIR" ]] || die "Backup directory not found: $BACKUP_DIR"
  BACKUP_DIR=$(abs_path "$BACKUP_DIR")

  local dump_file=$BACKUP_DIR/postgres/skillhub.dump
  local report_file=$BACKUP_DIR/manifest/postgres-restore-validation.txt
  require_file "$dump_file"

  trap cleanup EXIT
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true

  info "Starting temporary PostgreSQL restore container"
  docker run -d \
    --name "$CONTAINER_NAME" \
    -e POSTGRES_DB=postgres \
    -e POSTGRES_USER="$RESTORE_USER" \
    -e POSTGRES_PASSWORD="$RESTORE_PASSWORD" \
    -v "$BACKUP_DIR/postgres:/backup" \
    "$POSTGRES_IMAGE" >/dev/null

  wait_for_postgres

  info "Creating isolated restore database"
  docker_pg_exec createdb -h 127.0.0.1 -U "$RESTORE_USER" "$RESTORE_DB"

  info "Restoring PostgreSQL dump"
  docker_pg_exec \
    pg_restore \
    -h 127.0.0.1 \
    -U "$RESTORE_USER" \
    --no-owner \
    --no-privileges \
    -d "$RESTORE_DB" \
    /backup/skillhub.dump

  info "Running restore validation queries"
  docker_pg_exec \
    psql -h 127.0.0.1 -U "$RESTORE_USER" -d "$RESTORE_DB" -v ON_ERROR_STOP=1 -At -F '|' -c "
SELECT 'flyway_rows', COUNT(*)::text FROM flyway_schema_history
UNION ALL
SELECT 'flyway_latest', COALESCE(MAX(version)::text, 'none') FROM flyway_schema_history WHERE success
UNION ALL
SELECT 'user_account_rows', COUNT(*)::text FROM user_account
UNION ALL
SELECT 'namespace_member_rows', COUNT(*)::text FROM namespace_member
UNION ALL
SELECT 'skill_version_rows', COUNT(*)::text FROM skill_version
UNION ALL
SELECT 'security_audit_rows', COUNT(*)::text FROM security_audit
UNION ALL
SELECT 'skill_version_stats_rows', COUNT(*)::text FROM skill_version_stats;
" >"$report_file"

  info "Restore validation report written to $report_file"
  cat "$report_file"
}

main "$@"