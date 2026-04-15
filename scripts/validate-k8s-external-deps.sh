#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
CONFIGMAP_FILE=${1:-$ROOT_DIR/deploy/k8s/01-configmap.yml}

DEFAULT_SECRET_FILE=$ROOT_DIR/.dev/02-secret.yml
if [[ ! -f "$DEFAULT_SECRET_FILE" ]]; then
  DEFAULT_SECRET_FILE=$ROOT_DIR/deploy/k8s/02-secret.example.yml
fi
SECRET_FILE=${2:-$DEFAULT_SECRET_FILE}
CHECK_NETWORK=${CHECK_NETWORK:-false}

errors=0
warnings=0

error() {
  errors=$((errors + 1))
  echo "ERROR: $*" >&2
}

warn() {
  warnings=$((warnings + 1))
  echo "WARN: $*" >&2
}

require_file() {
  local file_path=$1
  if [[ ! -f "$file_path" ]]; then
    error "File not found: $file_path"
  fi
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

require_non_empty() {
  local name=$1
  local value=$2
  if [[ -z "$value" ]]; then
    error "$name is required"
  fi
}

reject_placeholder() {
  local name=$1
  local value=$2
  shift 2
  local bad
  for bad in "$@"; do
    if [[ "$value" == "$bad" ]]; then
      error "$name still uses placeholder value: $bad"
      return
    fi
  done
}

validate_url() {
  local name=$1
  local value=$2
  if [[ -z "$value" ]]; then
    return
  fi
  case "$value" in
    http://*|https://*) ;;
    *) error "$name must start with http:// or https://" ;;
  esac
}

validate_port() {
  local name=$1
  local value=$2
  if [[ -z "$value" ]]; then
    return
  fi
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then
    error "$name must be numeric"
    return
  fi
  if (( value < 1 || value > 65535 )); then
    error "$name must be between 1 and 65535"
  fi
}

tcp_probe() {
  local target=$1
  local host=${target%:*}
  local port=${target##*:}

  if ! command -v nc >/dev/null 2>&1; then
    warn "Skipping TCP probe for $target because nc is not available"
    return
  fi

  if ! nc -z -w 3 "$host" "$port" >/dev/null 2>&1; then
    error "TCP probe failed for $target"
  fi
}

http_probe() {
  local name=$1
  local url=$2
  local http_code

  if ! command -v curl >/dev/null 2>&1; then
    warn "Skipping HTTP probe for $name because curl is not available"
    return
  fi

  http_code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 5 "$url" || true)
  case "$http_code" in
    2*|3*|401|403|405) ;;
    *) error "HTTP probe failed for $name: $url (status: ${http_code:-unknown})" ;;
  esac
}

require_file "$CONFIGMAP_FILE"
require_file "$SECRET_FILE"

public_base_url=$(yaml_value "$CONFIGMAP_FILE" "skillhub-public-base-url")
postgres_host=$(yaml_value "$CONFIGMAP_FILE" "postgres-host")
postgres_port=$(yaml_value "$CONFIGMAP_FILE" "postgres-port")
postgres_db=$(yaml_value "$CONFIGMAP_FILE" "postgres-db")
postgres_user=$(yaml_value "$CONFIGMAP_FILE" "postgres-user")
redis_host=$(yaml_value "$CONFIGMAP_FILE" "redis-host")
redis_port=$(yaml_value "$CONFIGMAP_FILE" "redis-port")
storage_provider=$(yaml_value "$CONFIGMAP_FILE" "skillhub-storage-provider")
s3_endpoint=$(yaml_value "$CONFIGMAP_FILE" "skillhub-storage-s3-endpoint")
s3_bucket=$(yaml_value "$CONFIGMAP_FILE" "skillhub-storage-s3-bucket")
s3_region=$(yaml_value "$CONFIGMAP_FILE" "skillhub-storage-s3-region")
sourceid_redirect_uri=$(yaml_value "$CONFIGMAP_FILE" "oauth2-sourceid-redirect-uri")
bootstrap_admin_enabled=$(yaml_value "$CONFIGMAP_FILE" "bootstrap-admin-enabled")

# Fallback: also try SCREAMING_SNAKE keys (production ConfigMap)
if [[ -z "$public_base_url" ]]; then
  public_base_url=$(yaml_value "$CONFIGMAP_FILE" "SKILLHUB_PUBLIC_BASE_URL")
fi
if [[ -z "$postgres_host" ]]; then
  postgres_host=$(yaml_value "$CONFIGMAP_FILE" "POSTGRES_HOST")
  postgres_port=$(yaml_value "$CONFIGMAP_FILE" "POSTGRES_PORT")
  postgres_db=$(yaml_value "$CONFIGMAP_FILE" "POSTGRES_DB")
  postgres_user=$(yaml_value "$CONFIGMAP_FILE" "POSTGRES_USER")
fi
if [[ -z "$redis_host" ]]; then
  redis_host=$(yaml_value "$CONFIGMAP_FILE" "REDIS_HOST")
  redis_port=$(yaml_value "$CONFIGMAP_FILE" "REDIS_PORT")
fi
if [[ -z "$storage_provider" ]]; then
  storage_provider=$(yaml_value "$CONFIGMAP_FILE" "SKILLHUB_STORAGE_PROVIDER")
  s3_endpoint=$(yaml_value "$CONFIGMAP_FILE" "SKILLHUB_STORAGE_S3_ENDPOINT")
  s3_bucket=$(yaml_value "$CONFIGMAP_FILE" "SKILLHUB_STORAGE_S3_BUCKET")
  s3_region=$(yaml_value "$CONFIGMAP_FILE" "SKILLHUB_STORAGE_S3_REGION")
fi
if [[ -z "$bootstrap_admin_enabled" ]]; then
  bootstrap_admin_enabled=$(yaml_value "$CONFIGMAP_FILE" "BOOTSTRAP_ADMIN_ENABLED")
fi

postgres_password=$(yaml_value "$SECRET_FILE" "POSTGRES_PASSWORD")
redis_password=$(yaml_value "$SECRET_FILE" "REDIS_PASSWORD")
s3_access_key=$(yaml_value "$SECRET_FILE" "SKILLHUB_STORAGE_S3_ACCESS_KEY")
s3_secret_key=$(yaml_value "$SECRET_FILE" "SKILLHUB_STORAGE_S3_SECRET_KEY")
bootstrap_admin_password=$(yaml_value "$SECRET_FILE" "BOOTSTRAP_ADMIN_PASSWORD")
sourceid_client_id=$(yaml_value "$SECRET_FILE" "SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_ID")
sourceid_client_secret=$(yaml_value "$SECRET_FILE" "SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_SECRET")

require_non_empty SKILLHUB_PUBLIC_BASE_URL "$public_base_url"
require_non_empty POSTGRES_HOST "$postgres_host"
require_non_empty POSTGRES_PORT "$postgres_port"
require_non_empty POSTGRES_DB "$postgres_db"
require_non_empty POSTGRES_USER "$postgres_user"
require_non_empty POSTGRES_PASSWORD "$postgres_password"
require_non_empty REDIS_HOST "$redis_host"
require_non_empty REDIS_PORT "$redis_port"

validate_url SKILLHUB_PUBLIC_BASE_URL "$public_base_url"
validate_port POSTGRES_PORT "$postgres_port"
validate_port REDIS_PORT "$redis_port"

reject_placeholder POSTGRES_HOST "$postgres_host" "postgres" "postgres.example.internal"
reject_placeholder REDIS_HOST "$redis_host" "redis" "redis.example.internal"
reject_placeholder POSTGRES_PASSWORD "$postgres_password" "change-me" "skillhub_dev" "skillhub_demo"
if [[ -n "$redis_password" ]]; then
  reject_placeholder REDIS_PASSWORD "$redis_password" "change-me"
fi
reject_placeholder BOOTSTRAP_ADMIN_PASSWORD "$bootstrap_admin_password" "change-me" "ChangeMekubectl logs triton-bge-reranker-755566767b-m5jzv" "Admin@2026"

if [[ "$bootstrap_admin_enabled" == "true" ]]; then
  require_non_empty BOOTSTRAP_ADMIN_PASSWORD "$bootstrap_admin_password"
  reject_placeholder BOOTSTRAP_ADMIN_PASSWORD "$bootstrap_admin_password" "change-me"
fi

case "$storage_provider" in
  s3)
    require_non_empty SKILLHUB_STORAGE_S3_ENDPOINT "$s3_endpoint"
    require_non_empty SKILLHUB_STORAGE_S3_BUCKET "$s3_bucket"
    require_non_empty SKILLHUB_STORAGE_S3_REGION "$s3_region"
    require_non_empty SKILLHUB_STORAGE_S3_ACCESS_KEY "$s3_access_key"
    require_non_empty SKILLHUB_STORAGE_S3_SECRET_KEY "$s3_secret_key"
    validate_url SKILLHUB_STORAGE_S3_ENDPOINT "$s3_endpoint"
    reject_placeholder SKILLHUB_STORAGE_S3_ACCESS_KEY "$s3_access_key" "change-me" "ak-replace-me"
    reject_placeholder SKILLHUB_STORAGE_S3_SECRET_KEY "$s3_secret_key" "change-me" "sk-replace-me"
    ;;
  local)
    warn "SKILLHUB_STORAGE_PROVIDER=local is not recommended for production Kubernetes"
    ;;
  *)
    error "SKILLHUB_STORAGE_PROVIDER must be local or s3"
    ;;
esac

sourceid_enabled=false
if [[ -n "$sourceid_redirect_uri" ]]; then
  sourceid_enabled=true
fi
if [[ -n "$sourceid_client_id" && "$sourceid_client_id" != "change-me" ]]; then
  sourceid_enabled=true
fi

if [[ "$sourceid_enabled" == "true" ]]; then
  require_non_empty SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_ID "$sourceid_client_id"
  require_non_empty SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_SECRET "$sourceid_client_secret"
  reject_placeholder SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_ID "$sourceid_client_id" "change-me"
  reject_placeholder SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_SECRET "$sourceid_client_secret" "change-me"
fi

if [[ "$CHECK_NETWORK" == "true" ]]; then
  tcp_probe "$postgres_host:$postgres_port"
  tcp_probe "$redis_host:$redis_port"
  if [[ -n "$s3_endpoint" ]]; then
    http_probe SKILLHUB_STORAGE_S3_ENDPOINT "$s3_endpoint"
  fi
  if [[ -n "$sourceid_authorization_uri" ]]; then
    http_probe oauth2-sourceid-authorization-uri "$sourceid_authorization_uri"
  fi
fi

if (( errors > 0 )); then
  echo "Kubernetes external dependency validation failed: $errors error(s), $warnings warning(s)." >&2
  exit 1
fi

echo "Kubernetes external dependency validation passed with $warnings warning(s)."