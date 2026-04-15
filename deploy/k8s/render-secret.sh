#!/usr/bin/env bash

set -euo pipefail

NAMESPACE=${NAMESPACE:-skillhub}
SECRET_NAME=${SECRET_NAME:-skillhub-secret}
OUTPUT_MODE=${1:-stdout}

require_env() {
  local name=$1
  if [[ -z "${!name:-}" ]]; then
    echo "ERROR: $name is required" >&2
    exit 1
  fi
}

require_env SPRING_DATASOURCE_PASSWORD
require_env SKILLHUB_STORAGE_S3_ACCESS_KEY
require_env SKILLHUB_STORAGE_S3_SECRET_KEY

REDIS_PASSWORD=${REDIS_PASSWORD:-}
BOOTSTRAP_ADMIN_PASSWORD=${BOOTSTRAP_ADMIN_PASSWORD:-change-me}
OAUTH2_SOURCEID_CLIENT_ID=${OAUTH2_SOURCEID_CLIENT_ID:-change-me}
OAUTH2_SOURCEID_CLIENT_SECRET=${OAUTH2_SOURCEID_CLIENT_SECRET:-change-me}
OSDS_SYSID=${OSDS_SYSID:-}
OSDS_ACCESS_KEY_SECRET=${OSDS_ACCESS_KEY_SECRET:-}

manifest() {
  cat <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: ${SECRET_NAME}
  namespace: ${NAMESPACE}
type: Opaque
stringData:
  # ========== Database ==========
  POSTGRES_PASSWORD: ${SPRING_DATASOURCE_PASSWORD}

  # ========== Redis ==========
  REDIS_PASSWORD: ${REDIS_PASSWORD:-}

  # ========== Object Storage ==========
  SKILLHUB_STORAGE_S3_ACCESS_KEY: ${SKILLHUB_STORAGE_S3_ACCESS_KEY}
  SKILLHUB_STORAGE_S3_SECRET_KEY: ${SKILLHUB_STORAGE_S3_SECRET_KEY}

  # ========== Bootstrap Admin ==========
  BOOTSTRAP_ADMIN_PASSWORD: ${BOOTSTRAP_ADMIN_PASSWORD}

  # ========== OAuth2 ==========
  SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_ID: "${OAUTH2_SOURCEID_CLIENT_ID}"
  SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_SOURCEID_CLIENT_SECRET: "${OAUTH2_SOURCEID_CLIENT_SECRET}"

  # ========== OSDS ==========
  SKILLHUB_AUTH_SOURCEID_OSDS_SYSID: "${OSDS_SYSID}"
  SKILLHUB_AUTH_SOURCEID_OSDS_ACCESS_KEY_SECRET: "${OSDS_ACCESS_KEY_SECRET}"
EOF
}

case "$OUTPUT_MODE" in
  stdout)
    manifest
    ;;
  apply)
    manifest | kubectl apply -f -
    ;;
  *)
    echo "Usage: deploy/k8s/render-secret.sh [stdout|apply]" >&2
    exit 1
    ;;
esac