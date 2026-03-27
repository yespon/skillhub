#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
CONFIGMAP_FILE=${1:-$ROOT_DIR/deploy/k8s/configmap.yaml}
SECRET_FILE=${2:-$ROOT_DIR/deploy/k8s/secret.yaml}
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

extract_host_port_from_jdbc() {
  local jdbc_url=$1
  local without_prefix=${jdbc_url#jdbc:postgresql://}
  local host_port=${without_prefix%%/*}
  printf '%s\n' "$host_port"
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

  if ! command -v curl >/dev/null 2>&1; then
    warn "Skipping HTTP probe for $name because curl is not available"
    return
  fi

  if ! curl -fsS --max-time 5 -I "$url" >/dev/null 2>&1; then
    error "HTTP probe failed for $name: $url"
  fi
}

require_file "$CONFIGMAP_FILE"
require_file "$SECRET_FILE"

public_base_url=$(yaml_value "$CONFIGMAP_FILE" "skillhub-public-base-url")
device_auth_verification_uri=$(yaml_value "$CONFIGMAP_FILE" "device-auth-verification-uri")
redis_host=$(yaml_value "$CONFIGMAP_FILE" "redis-host")
redis_port=$(yaml_value "$CONFIGMAP_FILE" "redis-port")
storage_provider=$(yaml_value "$CONFIGMAP_FILE" "skillhub-storage-provider")

datasource_url=$(yaml_value "$SECRET_FILE" "spring-datasource-url")
datasource_username=$(yaml_value "$SECRET_FILE" "spring-datasource-username")
datasource_password=$(yaml_value "$SECRET_FILE" "spring-datasource-password")
s3_endpoint=$(yaml_value "$SECRET_FILE" "skillhub-storage-s3-endpoint")
s3_public_endpoint=$(yaml_value "$SECRET_FILE" "skillhub-storage-s3-public-endpoint")
s3_bucket=$(yaml_value "$SECRET_FILE" "skillhub-storage-s3-bucket")
s3_access_key=$(yaml_value "$SECRET_FILE" "skillhub-storage-s3-access-key")
s3_secret_key=$(yaml_value "$SECRET_FILE" "skillhub-storage-s3-secret-key")
s3_region=$(yaml_value "$SECRET_FILE" "skillhub-storage-s3-region")
bootstrap_admin_password=$(yaml_value "$SECRET_FILE" "bootstrap-admin-password")

require_non_empty skillhub-public-base-url "$public_base_url"
require_non_empty device-auth-verification-uri "$device_auth_verification_uri"
require_non_empty redis-host "$redis_host"
require_non_empty redis-port "$redis_port"
require_non_empty spring-datasource-url "$datasource_url"
require_non_empty spring-datasource-username "$datasource_username"
require_non_empty spring-datasource-password "$datasource_password"

validate_url skillhub-public-base-url "$public_base_url"
validate_url device-auth-verification-uri "$device_auth_verification_uri"
validate_port redis-port "$redis_port"

reject_placeholder redis-host "$redis_host" "redis.example.internal"
reject_placeholder spring-datasource-url "$datasource_url" "jdbc:postgresql://postgres.example.internal:5432/skillhub"
reject_placeholder spring-datasource-password "$datasource_password" "change-me"
reject_placeholder skillhub-storage-s3-endpoint "$s3_endpoint" "https://s3.example.internal"
reject_placeholder skillhub-storage-s3-public-endpoint "$s3_public_endpoint" "https://s3.example.internal"
reject_placeholder skillhub-storage-s3-access-key "$s3_access_key" "change-me"
reject_placeholder skillhub-storage-s3-secret-key "$s3_secret_key" "change-me"
reject_placeholder bootstrap-admin-password "$bootstrap_admin_password" "change-me"

case "$storage_provider" in
  s3)
    require_non_empty skillhub-storage-s3-endpoint "$s3_endpoint"
    require_non_empty skillhub-storage-s3-public-endpoint "$s3_public_endpoint"
    require_non_empty skillhub-storage-s3-bucket "$s3_bucket"
    require_non_empty skillhub-storage-s3-access-key "$s3_access_key"
    require_non_empty skillhub-storage-s3-secret-key "$s3_secret_key"
    require_non_empty skillhub-storage-s3-region "$s3_region"
    validate_url skillhub-storage-s3-endpoint "$s3_endpoint"
    validate_url skillhub-storage-s3-public-endpoint "$s3_public_endpoint"
    ;;
  local)
    warn "skillhub-storage-provider=local is not recommended for production Kubernetes"
    ;;
  *)
    error "skillhub-storage-provider must be local or s3"
    ;;
esac

if [[ "$datasource_url" != jdbc:postgresql://* ]]; then
  error "spring-datasource-url must use jdbc:postgresql://"
fi

if [[ "$CHECK_NETWORK" == "true" ]]; then
  tcp_probe "$(extract_host_port_from_jdbc "$datasource_url")"
  tcp_probe "$redis_host:$redis_port"
  http_probe skillhub-public-base-url "$public_base_url"
  if [[ -n "$s3_endpoint" ]]; then
    http_probe skillhub-storage-s3-endpoint "$s3_endpoint"
  fi
fi

if (( errors > 0 )); then
  echo "Kubernetes external dependency validation failed: $errors error(s), $warnings warning(s)." >&2
  exit 1
fi

echo "Kubernetes external dependency validation passed with $warnings warning(s)."