#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd -- "$SCRIPT_DIR/../.." && pwd)

CONFIGMAP_FILE=${CONFIGMAP_FILE:-$SCRIPT_DIR/01-configmap.yml}
SECRET_FILE=${SECRET_FILE:-$SCRIPT_DIR/02-secret.yml}
BACKUP_ROOT=${BACKUP_ROOT:-$SCRIPT_DIR/archives}
TIMESTAMP=${TIMESTAMP:-$(date +%Y%m%d-%H%M%S)}
BACKUP_DIR=${BACKUP_DIR:-$BACKUP_ROOT/backup-$TIMESTAMP}
ARCHIVE_TAR=${ARCHIVE_TAR:-true}
REDIS_STREAM_KEY=${REDIS_STREAM_KEY:-skillhub:scan:requests}
REDIS_STREAM_GROUP=${REDIS_STREAM_GROUP:-skillhub-scanners}
REDIS_STREAM_EXPORT_COUNT=${REDIS_STREAM_EXPORT_COUNT:-100000}
APP_BASE_URL=${APP_BASE_URL:-}
APP_COOKIE_JAR=${APP_COOKIE_JAR:-}
APP_MOCK_USER_ID=${APP_MOCK_USER_ID:-}
APP_ADMIN_FILE_API=${APP_ADMIN_FILE_API:-false}

POSTGRES_DIR=
REDIS_DIR=
S3_DIR=
MANIFEST_DIR=

PGHOST_VALUE=
PGPORT_VALUE=
PGDATABASE_VALUE=
PGUSER_VALUE=
PGPASSWORD_VALUE=
REDIS_HOST_VALUE=
REDIS_PORT_VALUE=
REDIS_PASSWORD_VALUE=
S3_ENDPOINT_VALUE=
S3_BUCKET_VALUE=
S3_REGION_VALUE=
S3_ACCESS_KEY_VALUE=
S3_SECRET_KEY_VALUE=
SERVER_IMAGE_VALUE=
WEB_IMAGE_VALUE=
SCANNER_IMAGE_VALUE=
S3_EXACT_KEY_CLIENT_USED=
S3_AWS_CLI_AVAILABLE=

info() {
  printf '[INFO] %s\n' "$*"
}

warn() {
  printf '[WARN] %s\n' "$*" >&2
}

die() {
  printf '[ERROR] %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: deploy/k8s/create-temporary-release-archive.sh

Creates a temporary pre-release archive using the connection credentials from:
  - deploy/k8s/01-configmap.yml
  - deploy/k8s/02-secret.yml

Environment variables:
  CONFIGMAP_FILE   Override ConfigMap file path
  SECRET_FILE      Override Secret file path
  BACKUP_ROOT      Override archive root directory
  BACKUP_DIR       Override final archive directory
  ARCHIVE_TAR      true/false, default: true
  REDIS_STREAM_KEY Redis stream to export, default: skillhub:scan:requests
  REDIS_STREAM_GROUP Redis consumer group, default: skillhub-scanners
  REDIS_STREAM_EXPORT_COUNT Max Redis stream records to export, default: 100000
  APP_BASE_URL     Optional SkillHub base URL for application API file export fallback
  APP_COOKIE_JAR   Optional curl cookie jar for authenticated API file export fallback
  APP_MOCK_USER_ID Optional X-Mock-User-Id header value for API file export fallback
  APP_ADMIN_FILE_API true/false, use admin versionId file API fallback, default: false

This script prefers local CLI tools and falls back to Docker images when possible.
EOF
}

require_file() {
  [[ -f "$1" ]] || die "File not found: $1"
}

yaml_value() {
  local file_path=$1
  local key=$2

  awk -F': ' -v key="$key" '
    $1 == "  " key {
      value = substr($0, length($1) + 3)
      gsub(/^"|"$/, "", value)
      print value
      exit
    }
  ' "$file_path"
}

image_value() {
  local file_path=$1
  awk '/image:/ {gsub(/"/, "", $2); print $2; exit}' "$file_path"
}

require_value() {
  local name=$1
  local value=$2
  [[ -n "$value" ]] || die "$name is required"
}

has_cmd() {
  command -v "$1" >/dev/null 2>&1
}

urlencode() {
  local raw=$1

  if has_cmd python3; then
    python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$raw"
    return
  fi

  die "python3 is required for URL encoding during application API fallback"
}

record_s3_backup_metadata() {
  local mode=$1
  local client=$2
  printf 's3_backup_mode=%s\n' "$mode" >>"$MANIFEST_DIR/metadata.txt"
  printf 's3_backup_client=%s\n' "$client" >>"$MANIFEST_DIR/metadata.txt"
}

note_s3_exact_key_client() {
  local client=$1

  if [[ -z "$S3_EXACT_KEY_CLIENT_USED" ]]; then
    S3_EXACT_KEY_CLIENT_USED=$client
    return
  fi

  if [[ "$S3_EXACT_KEY_CLIENT_USED" != "$client" ]]; then
    S3_EXACT_KEY_CLIENT_USED=mixed
  fi
}

run_aws_cli() {
  if has_cmd aws; then
    AWS_ACCESS_KEY_ID="$S3_ACCESS_KEY_VALUE" \
      AWS_SECRET_ACCESS_KEY="$S3_SECRET_KEY_VALUE" \
      AWS_DEFAULT_REGION="$S3_REGION_VALUE" \
      aws "$@"
    return
  fi

  if has_cmd docker; then
    docker run --rm \
      -e AWS_ACCESS_KEY_ID="$S3_ACCESS_KEY_VALUE" \
      -e AWS_SECRET_ACCESS_KEY="$S3_SECRET_KEY_VALUE" \
      -e AWS_DEFAULT_REGION="$S3_REGION_VALUE" \
      -v "$S3_DIR:/backup" \
      amazon/aws-cli:2 "$@"
    return
  fi

  return 1
}

can_use_aws_cli() {
  if [[ -n "$S3_AWS_CLI_AVAILABLE" ]]; then
    [[ "$S3_AWS_CLI_AVAILABLE" == "true" ]]
    return
  fi

  if run_aws_cli --version >/dev/null 2>&1; then
    S3_AWS_CLI_AVAILABLE=true
    return 0
  fi

  S3_AWS_CLI_AVAILABLE=false
  return 1
}

load_config() {
  require_file "$CONFIGMAP_FILE"
  require_file "$SECRET_FILE"

  PGHOST_VALUE=$(yaml_value "$CONFIGMAP_FILE" "POSTGRES_HOST")
  PGPORT_VALUE=$(yaml_value "$CONFIGMAP_FILE" "POSTGRES_PORT")
  PGDATABASE_VALUE=$(yaml_value "$CONFIGMAP_FILE" "POSTGRES_DB")
  PGUSER_VALUE=$(yaml_value "$CONFIGMAP_FILE" "POSTGRES_USER")
  PGPASSWORD_VALUE=$(yaml_value "$SECRET_FILE" "POSTGRES_PASSWORD")

  REDIS_HOST_VALUE=$(yaml_value "$CONFIGMAP_FILE" "REDIS_HOST")
  REDIS_PORT_VALUE=$(yaml_value "$CONFIGMAP_FILE" "REDIS_PORT")
  REDIS_PASSWORD_VALUE=$(yaml_value "$SECRET_FILE" "REDIS_PASSWORD")

  S3_ENDPOINT_VALUE=$(yaml_value "$CONFIGMAP_FILE" "SKILLHUB_STORAGE_S3_ENDPOINT")
  S3_BUCKET_VALUE=$(yaml_value "$CONFIGMAP_FILE" "SKILLHUB_STORAGE_S3_BUCKET")
  S3_REGION_VALUE=$(yaml_value "$CONFIGMAP_FILE" "SKILLHUB_STORAGE_S3_REGION")
  S3_ACCESS_KEY_VALUE=$(yaml_value "$SECRET_FILE" "SKILLHUB_STORAGE_S3_ACCESS_KEY")
  S3_SECRET_KEY_VALUE=$(yaml_value "$SECRET_FILE" "SKILLHUB_STORAGE_S3_SECRET_KEY")

  SERVER_IMAGE_VALUE=$(image_value "$SCRIPT_DIR/03-backend-deployment.yml")
  WEB_IMAGE_VALUE=$(image_value "$SCRIPT_DIR/04-frontend-deployment.yml")
  SCANNER_IMAGE_VALUE=$(image_value "$SCRIPT_DIR/03-01-scanner-deployment.yaml")

  require_value POSTGRES_HOST "$PGHOST_VALUE"
  require_value POSTGRES_PORT "$PGPORT_VALUE"
  require_value POSTGRES_DB "$PGDATABASE_VALUE"
  require_value POSTGRES_USER "$PGUSER_VALUE"
  require_value POSTGRES_PASSWORD "$PGPASSWORD_VALUE"
  require_value REDIS_HOST "$REDIS_HOST_VALUE"
  require_value REDIS_PORT "$REDIS_PORT_VALUE"
  require_value SKILLHUB_STORAGE_S3_ENDPOINT "$S3_ENDPOINT_VALUE"
  require_value SKILLHUB_STORAGE_S3_BUCKET "$S3_BUCKET_VALUE"
  require_value SKILLHUB_STORAGE_S3_ACCESS_KEY "$S3_ACCESS_KEY_VALUE"
  require_value SKILLHUB_STORAGE_S3_SECRET_KEY "$S3_SECRET_KEY_VALUE"
}

init_dirs() {
  POSTGRES_DIR=$BACKUP_DIR/postgres
  REDIS_DIR=$BACKUP_DIR/redis
  S3_DIR=$BACKUP_DIR/s3
  MANIFEST_DIR=$BACKUP_DIR/manifest
  mkdir -p "$POSTGRES_DIR" "$REDIS_DIR" "$S3_DIR" "$MANIFEST_DIR"
}

write_metadata() {
  local metadata_file=$MANIFEST_DIR/metadata.txt
  {
    printf 'timestamp=%s\n' "$TIMESTAMP"
    printf 'git_commit=%s\n' "$(git -C "$ROOT_DIR" rev-parse HEAD 2>/dev/null || echo unknown)"
    printf 'server_image=%s\n' "$SERVER_IMAGE_VALUE"
    printf 'web_image=%s\n' "$WEB_IMAGE_VALUE"
    printf 'scanner_image=%s\n' "$SCANNER_IMAGE_VALUE"
    printf 'configmap_file=%s\n' "$CONFIGMAP_FILE"
    printf 'secret_file=%s\n' "$SECRET_FILE"
    printf 'postgres_host=%s\n' "$PGHOST_VALUE"
    printf 'postgres_port=%s\n' "$PGPORT_VALUE"
    printf 'postgres_db=%s\n' "$PGDATABASE_VALUE"
    printf 'postgres_user=%s\n' "$PGUSER_VALUE"
    printf 'redis_host=%s\n' "$REDIS_HOST_VALUE"
    printf 'redis_port=%s\n' "$REDIS_PORT_VALUE"
    printf 'redis_stream_key=%s\n' "$REDIS_STREAM_KEY"
    printf 'redis_stream_group=%s\n' "$REDIS_STREAM_GROUP"
    printf 's3_endpoint=%s\n' "$S3_ENDPOINT_VALUE"
    printf 's3_bucket=%s\n' "$S3_BUCKET_VALUE"
    printf 's3_region=%s\n' "$S3_REGION_VALUE"
  } >"$metadata_file"
}

run_pg_dump() {
  info "Exporting PostgreSQL logical backup"

  if has_cmd pg_dump; then
    PGPASSWORD="$PGPASSWORD_VALUE" \
      pg_dump \
      -h "$PGHOST_VALUE" \
      -p "$PGPORT_VALUE" \
      -U "$PGUSER_VALUE" \
      -d "$PGDATABASE_VALUE" \
      -Fc \
      -f "$POSTGRES_DIR/skillhub.dump"
    return
  fi

  if has_cmd docker; then
    docker run --rm \
      -e PGPASSWORD="$PGPASSWORD_VALUE" \
      -v "$POSTGRES_DIR:/backup" \
      postgres:16 \
      pg_dump \
      -h "$PGHOST_VALUE" \
      -p "$PGPORT_VALUE" \
      -U "$PGUSER_VALUE" \
      -d "$PGDATABASE_VALUE" \
      -Fc \
      -f /backup/skillhub.dump
    return
  fi

  die "pg_dump is unavailable and Docker is not installed"
}

run_psql_query_to_file() {
  local sql=$1
  local output_file=$2

  if has_cmd psql; then
    PGPASSWORD="$PGPASSWORD_VALUE" \
      psql \
      -h "$PGHOST_VALUE" \
      -p "$PGPORT_VALUE" \
      -U "$PGUSER_VALUE" \
      -d "$PGDATABASE_VALUE" \
      -v ON_ERROR_STOP=1 \
      -At \
      -c "$sql" >"$output_file"
    return
  fi

  if has_cmd docker; then
    docker run --rm \
      -e PGPASSWORD="$PGPASSWORD_VALUE" \
      -v "$(dirname "$output_file"):/query-output" \
      postgres:16 \
      sh -c "psql -h '$PGHOST_VALUE' -p '$PGPORT_VALUE' -U '$PGUSER_VALUE' -d '$PGDATABASE_VALUE' -v ON_ERROR_STOP=1 -At -c \"$sql\" > /query-output/$(basename "$output_file")"
    return
  fi

  die "psql is unavailable and Docker is not installed"
}

redis_base_args() {
  local args=(-h "$REDIS_HOST_VALUE" -p "$REDIS_PORT_VALUE")
  if [[ -n "$REDIS_PASSWORD_VALUE" ]]; then
    args+=(-a "$REDIS_PASSWORD_VALUE")
  fi
  printf '%s\n' "${args[@]}"
}

run_redis_rdb_local() {
  local -a base_args=()
  mapfile -t base_args < <(redis_base_args)
  redis-cli "${base_args[@]}" --rdb "$REDIS_DIR/dump.rdb"
}

run_redis_rdb_docker() {
  local -a base_args=()
  mapfile -t base_args < <(redis_base_args)
  docker run --rm \
    -v "$REDIS_DIR:/backup" \
    redis:7-alpine \
    redis-cli "${base_args[@]}" --rdb /backup/dump.rdb
}

run_redis_cli_capture() {
  local output_file=$1
  shift

  local -a base_args=()
  mapfile -t base_args < <(redis_base_args)

  if has_cmd redis-cli; then
    redis-cli --raw "${base_args[@]}" "$@" >"$output_file" 2>&1 || true
    return
  fi

  if has_cmd docker; then
    docker run --rm \
      redis:7-alpine \
      redis-cli --raw "${base_args[@]}" "$@" >"$output_file" 2>&1 || true
    return
  fi

  warn "Skipping Redis capture for $output_file because redis-cli and Docker are unavailable"
}

run_redis_backup() {
  info "Exporting Redis backup"

  if has_cmd redis-cli; then
    if run_redis_rdb_local; then
      printf 'redis_backup_mode=rdb\n' >>"$MANIFEST_DIR/metadata.txt"
      return
    fi
    warn "redis-cli --rdb failed, falling back to best-effort Redis export"
  elif has_cmd docker; then
    if run_redis_rdb_docker; then
      printf 'redis_backup_mode=rdb\n' >>"$MANIFEST_DIR/metadata.txt"
      return
    fi
    warn "Docker-based redis-cli --rdb failed, falling back to best-effort Redis export"
  else
    warn "Redis RDB export is unavailable; using best-effort Redis export"
  fi

  printf 'redis_backup_mode=best-effort\n' >>"$MANIFEST_DIR/metadata.txt"
  run_redis_cli_capture "$REDIS_DIR/all-keys.txt" --scan
  run_redis_cli_capture "$REDIS_DIR/scan-stream-type.txt" TYPE "$REDIS_STREAM_KEY"
  run_redis_cli_capture "$REDIS_DIR/scan-stream-len.txt" XLEN "$REDIS_STREAM_KEY"
  run_redis_cli_capture "$REDIS_DIR/stream-groups.txt" XINFO GROUPS "$REDIS_STREAM_KEY"
  run_redis_cli_capture "$REDIS_DIR/stream-consumers.txt" XINFO CONSUMERS "$REDIS_STREAM_KEY" "$REDIS_STREAM_GROUP"
  run_redis_cli_capture "$REDIS_DIR/scan-requests.txt" XRANGE "$REDIS_STREAM_KEY" - + COUNT "$REDIS_STREAM_EXPORT_COUNT"
}

run_s3_backup_with_mc() {
  MC_CONFIG_DIR="$S3_DIR/.mc" \
    mc alias set skillhub-backup "$S3_ENDPOINT_VALUE" "$S3_ACCESS_KEY_VALUE" "$S3_SECRET_KEY_VALUE" >/dev/null
  MC_CONFIG_DIR="$S3_DIR/.mc" \
    mc mirror skillhub-backup/"$S3_BUCKET_VALUE" "$S3_DIR/bucket"
}

run_s3_backup_with_aws() {
  run_aws_cli \
    --endpoint-url "$S3_ENDPOINT_VALUE" \
    s3 sync \
    "s3://$S3_BUCKET_VALUE" \
    /backup/bucket \
    --only-show-errors
}

run_s3_backup_with_docker() {
  docker run --rm \
    -v "$S3_DIR:/backup" \
    minio/mc \
    sh -c "mc alias set skillhub-backup '$S3_ENDPOINT_VALUE' '$S3_ACCESS_KEY_VALUE' '$S3_SECRET_KEY_VALUE' >/dev/null && mc mirror skillhub-backup/'$S3_BUCKET_VALUE' /backup/bucket"
}

export_storage_keys() {
  local keys_file=$MANIFEST_DIR/storage-keys.txt
  run_psql_query_to_file "SELECT DISTINCT storage_key FROM skill_file ORDER BY storage_key;" "$keys_file"
  if [[ ! -s "$keys_file" ]]; then
    warn "No storage keys were exported from skill_file; S3 exact-key fallback will have nothing to copy"
  fi
}

copy_s3_object_with_mc() {
  local object_key=$1
  local target_file=$2
  mkdir -p "$(dirname "$target_file")"
  MC_CONFIG_DIR="$S3_DIR/.mc" \
    mc cat "skillhub-backup/$S3_BUCKET_VALUE/$object_key" >"$target_file"
}

copy_s3_object_with_aws() {
  local object_key=$1
  local target_file=$2
  local relative_target=${target_file#"$S3_DIR/"}
  mkdir -p "$(dirname "$target_file")"
  run_aws_cli \
    --endpoint-url "$S3_ENDPOINT_VALUE" \
    s3api get-object \
    --bucket "$S3_BUCKET_VALUE" \
    --key "$object_key" \
    "/backup/$relative_target" >/dev/null
}

copy_s3_object_with_docker() {
  local object_key=$1
  local target_file=$2
  local relative_target=${target_file#"$S3_DIR/"}
  mkdir -p "$(dirname "$target_file")"
  docker run --rm \
    -v "$S3_DIR:/backup" \
    minio/mc \
    sh -c "mc alias set skillhub-backup '$S3_ENDPOINT_VALUE' '$S3_ACCESS_KEY_VALUE' '$S3_SECRET_KEY_VALUE' >/dev/null && mc cat 'skillhub-backup/$S3_BUCKET_VALUE/$object_key' > '/backup/$relative_target'"
}

export_app_api_jobs() {
  local jobs_file=$MANIFEST_DIR/app-api-export-jobs.tsv
  run_psql_query_to_file "SELECT sf.version_id || E'\t' || n.slug || E'\t' || s.slug || E'\t' || sv.version || E'\t' || sf.file_path || E'\t' || sf.storage_key FROM skill_file sf JOIN skill_version sv ON sv.id = sf.version_id JOIN skill s ON s.id = sv.skill_id JOIN namespace n ON n.id = s.namespace_id ORDER BY sf.storage_key;" "$jobs_file"
  if [[ ! -s "$jobs_file" ]]; then
    warn "No application API export jobs were generated from PostgreSQL metadata"
  fi
}

copy_s3_object_via_app_api() {
  local version_id=$1
  local namespace_slug=$2
  local skill_slug=$3
  local version=$4
  local file_path=$5
  local target_file=$6
  local encoded_namespace encoded_slug encoded_version encoded_path url
  local -a curl_args=(-fsSL)

  mkdir -p "$(dirname "$target_file")"

  encoded_path=$(urlencode "$file_path")

  if [[ "$APP_ADMIN_FILE_API" == "true" ]]; then
    url="${APP_BASE_URL%/}/api/v1/admin/skills/versions/${version_id}/file?path=${encoded_path}"
  else
    encoded_namespace=$(urlencode "$namespace_slug")
    encoded_slug=$(urlencode "$skill_slug")
    encoded_version=$(urlencode "$version")
    url="${APP_BASE_URL%/}/api/v1/skills/${encoded_namespace}/${encoded_slug}/versions/${encoded_version}/file?path=${encoded_path}"
  fi

  if [[ -n "$APP_COOKIE_JAR" ]]; then
    curl_args+=(-b "$APP_COOKIE_JAR")
  fi

  if [[ -n "$APP_MOCK_USER_ID" ]]; then
    curl_args+=(-H "X-Mock-User-Id: $APP_MOCK_USER_ID")
  fi

  curl "${curl_args[@]}" -o "$target_file" "$url"
}

run_app_api_file_backup() {
  local jobs_file=$MANIFEST_DIR/app-api-export-jobs.tsv
  local failed_file=$MANIFEST_DIR/s3-copy-failures.txt
  local copied=0

  [[ -n "$APP_BASE_URL" ]] || return 1
  has_cmd curl || die "curl is required for application API fallback"

  export_app_api_jobs
  : >"$failed_file"

  [[ -f "$jobs_file" ]] || die "Application API job list was not created"

  while IFS=$'\t' read -r version_id namespace_slug skill_slug version file_path storage_key; do
    [[ -n "$storage_key" ]] || continue
    local target_file="$S3_DIR/bucket/$storage_key"
    if copy_s3_object_via_app_api "$version_id" "$namespace_slug" "$skill_slug" "$version" "$file_path" "$target_file"; then
      copied=$((copied + 1))
      continue
    fi

    rm -f "$target_file"
    printf '%s\n' "$storage_key" >>"$failed_file"
  done <"$jobs_file"

  printf 's3_app_api_copied=%s\n' "$copied" >>"$MANIFEST_DIR/metadata.txt"

  if [[ -s "$failed_file" ]]; then
    return 1
  fi

  return 0
}

run_s3_exact_key_backup() {
  local keys_file=$MANIFEST_DIR/storage-keys.txt
  local failed_file=$MANIFEST_DIR/s3-copy-failures.txt
  local copied=0

  export_storage_keys
  : >"$failed_file"

  [[ -f "$keys_file" ]] || die "Storage key list was not created"

  while IFS= read -r object_key; do
    [[ -n "$object_key" ]] || continue
    local target_file="$S3_DIR/bucket/$object_key"
    if can_use_aws_cli; then
      if copy_s3_object_with_aws "$object_key" "$target_file"; then
        note_s3_exact_key_client aws-cli
        copied=$((copied + 1))
        continue
      fi
    fi

    if has_cmd mc; then
      if copy_s3_object_with_mc "$object_key" "$target_file"; then
        note_s3_exact_key_client mc
        copied=$((copied + 1))
        continue
      fi
    elif has_cmd docker; then
      if copy_s3_object_with_docker "$object_key" "$target_file"; then
        note_s3_exact_key_client minio-mc-docker
        copied=$((copied + 1))
        continue
      fi
    else
      die "mc is unavailable and Docker is not installed"
    fi

    printf '%s\n' "$object_key" >>"$failed_file"
  done <"$keys_file"

  printf 's3_exact_key_copied=%s\n' "$copied" >>"$MANIFEST_DIR/metadata.txt"

  if [[ -s "$failed_file" ]]; then
    return 1
  fi

  return 0
}

run_s3_backup() {
  info "Exporting S3-compatible object storage bucket"

  if [[ "$APP_ADMIN_FILE_API" == "true" && -n "$APP_BASE_URL" ]]; then
    warn "Using SkillHub admin version file API fallback before direct object-store access"
    if run_app_api_file_backup; then
      record_s3_backup_metadata app-api-fallback curl
      return
    fi
    die "Application admin API fallback failed for one or more objects; see $MANIFEST_DIR/s3-copy-failures.txt"
  fi

  if can_use_aws_cli; then
    if run_s3_backup_with_aws; then
      record_s3_backup_metadata bucket-mirror aws-cli
      return
    fi
    warn "AWS CLI bucket mirror failed, falling back to exact-key object copy"
    if run_s3_exact_key_backup; then
      record_s3_backup_metadata exact-key-fallback "${S3_EXACT_KEY_CLIENT_USED:-aws-cli}"
      return
    fi
    warn "AWS CLI exact-key object copy failed"
  fi

  if has_cmd mc; then
    if run_s3_backup_with_mc; then
      record_s3_backup_metadata bucket-mirror mc
      return
    fi
    warn "Bucket mirror failed, falling back to exact-key object copy"
    if run_s3_exact_key_backup; then
      record_s3_backup_metadata exact-key-fallback "${S3_EXACT_KEY_CLIENT_USED:-mc}"
      return
    fi
    warn "Exact-key object copy failed with current object-storage credentials"
  fi

  if ! has_cmd mc && has_cmd docker; then
    if run_s3_backup_with_docker; then
      record_s3_backup_metadata bucket-mirror minio-mc-docker
      return
    fi
    warn "Docker-based bucket mirror failed, falling back to exact-key object copy"
    if run_s3_exact_key_backup; then
      record_s3_backup_metadata exact-key-fallback "${S3_EXACT_KEY_CLIENT_USED:-minio-mc-docker}"
      return
    fi
    warn "Docker-based exact-key object copy failed with current object-storage credentials"
  fi

  if [[ -n "$APP_BASE_URL" ]]; then
    warn "Falling back to SkillHub application API file export via APP_BASE_URL"
    if run_app_api_file_backup; then
      record_s3_backup_metadata app-api-fallback curl
      return
    fi
    die "Application API fallback failed for one or more objects; see $MANIFEST_DIR/s3-copy-failures.txt"
  fi

  die "S3 backup failed. Provide object read permissions, or set APP_BASE_URL for application API fallback"
}

write_checksums() {
  info "Writing checksums"
  (
    cd "$BACKUP_DIR"
    find postgres redis s3 manifest -type f ! -path 'manifest/sha256.txt' -print0 \
      | sort -z \
      | xargs -0 sha256sum > manifest/sha256.txt
  )
}

write_archive() {
  if [[ "$ARCHIVE_TAR" != "true" ]]; then
    return 0
  fi
  info "Creating compressed archive"
  tar -czf "$BACKUP_DIR.tar.gz" -C "$(dirname "$BACKUP_DIR")" "$(basename "$BACKUP_DIR")"
}

main() {
  case "${1:-}" in
    -h|--help)
      usage
      exit 0
      ;;
  esac

  load_config
  init_dirs
  write_metadata
  run_pg_dump
  run_redis_backup
  run_s3_backup
  write_checksums
  write_archive

  info "Temporary release archive created at $BACKUP_DIR"
  if [[ "$ARCHIVE_TAR" == "true" ]]; then
    info "Compressed archive created at $BACKUP_DIR.tar.gz"
  fi
}

main "$@"