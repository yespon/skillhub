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

require_env SPRING_DATASOURCE_URL
require_env SPRING_DATASOURCE_USERNAME
require_env SPRING_DATASOURCE_PASSWORD
require_env SKILLHUB_STORAGE_S3_ENDPOINT
require_env SKILLHUB_STORAGE_S3_PUBLIC_ENDPOINT
require_env SKILLHUB_STORAGE_S3_BUCKET
require_env SKILLHUB_STORAGE_S3_ACCESS_KEY
require_env SKILLHUB_STORAGE_S3_SECRET_KEY
require_env SKILLHUB_STORAGE_S3_REGION

BOOTSTRAP_ADMIN_ENABLED=${BOOTSTRAP_ADMIN_ENABLED:-false}
BOOTSTRAP_ADMIN_USER_ID=${BOOTSTRAP_ADMIN_USER_ID:-bootstrap-admin}
BOOTSTRAP_ADMIN_USERNAME=${BOOTSTRAP_ADMIN_USERNAME:-admin}
BOOTSTRAP_ADMIN_PASSWORD=${BOOTSTRAP_ADMIN_PASSWORD:-change-me}
BOOTSTRAP_ADMIN_DISPLAY_NAME=${BOOTSTRAP_ADMIN_DISPLAY_NAME:-Admin}
BOOTSTRAP_ADMIN_EMAIL=${BOOTSTRAP_ADMIN_EMAIL:-admin@ruijie.com.cn}
OAUTH2_GITHUB_CLIENT_ID=${OAUTH2_GITHUB_CLIENT_ID:-}
OAUTH2_GITHUB_CLIENT_SECRET=${OAUTH2_GITHUB_CLIENT_SECRET:-}
OAUTH2_SOURCEID_CLIENT_ID=${OAUTH2_SOURCEID_CLIENT_ID:-change-me}
OAUTH2_SOURCEID_CLIENT_SECRET=${OAUTH2_SOURCEID_CLIENT_SECRET:-change-me}
OAUTH2_SOURCEID_REDIRECT_URI=${OAUTH2_SOURCEID_REDIRECT_URI:-https://skillhub.ruijie.com.cn/login/oauth2/code/sourceid}
OAUTH2_SOURCEID_AUTHORIZATION_URI=${OAUTH2_SOURCEID_AUTHORIZATION_URI:-https://sid.ruijie.com.cn/oauth2.0/authorize}
OAUTH2_SOURCEID_TOKEN_URI=${OAUTH2_SOURCEID_TOKEN_URI:-https://sid.ruijie.com.cn/oauth2.0/accessToken}
OAUTH2_SOURCEID_USER_INFO_URI=${OAUTH2_SOURCEID_USER_INFO_URI:-https://sid.ruijie.com.cn/oauth2.0/profile}

manifest() {
  cat <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: ${SECRET_NAME}
  namespace: ${NAMESPACE}
type: Opaque
stringData:
  spring-datasource-url: ${SPRING_DATASOURCE_URL}
  spring-datasource-username: ${SPRING_DATASOURCE_USERNAME}
  spring-datasource-password: ${SPRING_DATASOURCE_PASSWORD}
  skillhub-storage-s3-endpoint: ${SKILLHUB_STORAGE_S3_ENDPOINT}
  skillhub-storage-s3-public-endpoint: ${SKILLHUB_STORAGE_S3_PUBLIC_ENDPOINT}
  skillhub-storage-s3-bucket: ${SKILLHUB_STORAGE_S3_BUCKET}
  skillhub-storage-s3-access-key: ${SKILLHUB_STORAGE_S3_ACCESS_KEY}
  skillhub-storage-s3-secret-key: ${SKILLHUB_STORAGE_S3_SECRET_KEY}
  skillhub-storage-s3-region: ${SKILLHUB_STORAGE_S3_REGION}
  bootstrap-admin-enabled: "${BOOTSTRAP_ADMIN_ENABLED}"
  bootstrap-admin-user-id: ${BOOTSTRAP_ADMIN_USER_ID}
  bootstrap-admin-username: ${BOOTSTRAP_ADMIN_USERNAME}
  bootstrap-admin-password: ${BOOTSTRAP_ADMIN_PASSWORD}
  bootstrap-admin-display-name: ${BOOTSTRAP_ADMIN_DISPLAY_NAME}
  bootstrap-admin-email: ${BOOTSTRAP_ADMIN_EMAIL}
  oauth2-github-client-id: "${OAUTH2_GITHUB_CLIENT_ID}"
  oauth2-github-client-secret: "${OAUTH2_GITHUB_CLIENT_SECRET}"
  oauth2-sourceid-client-id: ${OAUTH2_SOURCEID_CLIENT_ID}
  oauth2-sourceid-client-secret: ${OAUTH2_SOURCEID_CLIENT_SECRET}
  oauth2-sourceid-redirect-uri: ${OAUTH2_SOURCEID_REDIRECT_URI}
  oauth2-sourceid-authorization-uri: ${OAUTH2_SOURCEID_AUTHORIZATION_URI}
  oauth2-sourceid-token-uri: ${OAUTH2_SOURCEID_TOKEN_URI}
  oauth2-sourceid-user-info-uri: ${OAUTH2_SOURCEID_USER_INFO_URI}
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