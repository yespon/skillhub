#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd -- "$SCRIPT_DIR/../.." && pwd)

NAMESPACE=${NAMESPACE:-skillhub}
ROLLOUT_TIMEOUT=${ROLLOUT_TIMEOUT:-300s}
LOG_TAIL=${LOG_TAIL:-200}
PUBLIC_BASE_URL=${PUBLIC_BASE_URL:-}
EXPECT_AUTH_METHOD=${EXPECT_AUTH_METHOD:-oauth-sourceid}
CHECK_PUBLIC_ENDPOINTS=${CHECK_PUBLIC_ENDPOINTS:-true}

DEPLOYMENTS=(
  skillhub-scanner
  skillhub-server
  skillhub-web
)

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
Usage: bash deploy/k8s/rollback-health-check.sh

Environment variables:
  NAMESPACE             Kubernetes namespace, default: skillhub
  ROLLOUT_TIMEOUT       rollout status timeout, default: 300s
  LOG_TAIL              log lines to print on failure, default: 200
  PUBLIC_BASE_URL       public URL to check; defaults to SKILLHUB_PUBLIC_BASE_URL in 01-configmap.yml
  EXPECT_AUTH_METHOD    expected auth method marker, default: oauth-sourceid
  CHECK_PUBLIC_ENDPOINTS true/false, default: true
EOF
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

detect_public_base_url() {
  if [[ -n "$PUBLIC_BASE_URL" ]]; then
    return
  fi

  local configmap_file=$ROOT_DIR/deploy/k8s/01-configmap.yml
  if [[ -f "$configmap_file" ]]; then
    PUBLIC_BASE_URL=$(awk -F': ' '/  SKILLHUB_PUBLIC_BASE_URL:/ {gsub(/^"|"$/, "", $2); print $2; exit}' "$configmap_file")
  fi
}

print_recent_logs() {
  local dep
  for dep in "${DEPLOYMENTS[@]}"; do
    warn "Recent logs for deployment/$dep"
    kubectl -n "$NAMESPACE" logs "deployment/$dep" --tail="$LOG_TAIL" || true
  done
}

wait_for_rollouts() {
  local dep
  for dep in "${DEPLOYMENTS[@]}"; do
    info "Waiting for deployment/$dep rollout"
    kubectl -n "$NAMESPACE" rollout status "deployment/$dep" --timeout "$ROLLOUT_TIMEOUT"
  done
}

check_backend_health() {
  info "Checking backend actuator health from inside deployment/skillhub-server"
  local response
  response=$(kubectl -n "$NAMESPACE" exec deploy/skillhub-server -- wget -qO- http://127.0.0.1:8080/actuator/health)
  [[ "$response" == *'"status":"UP"'* || "$response" == *'"status": "UP"'* ]] || \
    die "backend /actuator/health did not report UP: $response"
}

check_public_endpoints() {
  [[ "$CHECK_PUBLIC_ENDPOINTS" == "true" ]] || return 0
  [[ -n "$PUBLIC_BASE_URL" ]] || die "PUBLIC_BASE_URL is empty; set PUBLIC_BASE_URL or configure SKILLHUB_PUBLIC_BASE_URL in deploy/k8s/01-configmap.yml"
  require_cmd curl

  info "Checking public home page at $PUBLIC_BASE_URL/"
  curl -ksSf "$PUBLIC_BASE_URL/" >/dev/null

  info "Checking auth methods at $PUBLIC_BASE_URL/api/v1/auth/methods"
  local auth_methods
  auth_methods=$(curl -ksSf "$PUBLIC_BASE_URL/api/v1/auth/methods")
  [[ "$auth_methods" == *"$EXPECT_AUTH_METHOD"* ]] || \
    die "auth methods response did not contain $EXPECT_AUTH_METHOD: $auth_methods"
}

main() {
  if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
  fi

  trap 'print_recent_logs' ERR

  require_cmd kubectl
  detect_public_base_url

  info "Namespace: $NAMESPACE"
  if [[ -n "$PUBLIC_BASE_URL" ]]; then
    info "Public base URL: $PUBLIC_BASE_URL"
  fi

  wait_for_rollouts
  info "Listing pods after rollback"
  kubectl -n "$NAMESPACE" get pods
  check_backend_health
  check_public_endpoints

  trap - ERR
  info "Rollback health checks passed"
}

main "$@"