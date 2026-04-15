#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd -- "$SCRIPT_DIR/../.." && pwd)

MANIFEST_NAMESPACE=skillhub
NAMESPACE=${NAMESPACE:-$MANIFEST_NAMESPACE}
CONFIGMAP_FILE=${CONFIGMAP_FILE:-$ROOT_DIR/deploy/k8s/01-configmap.yml}
SECRET_FILE=${SECRET_FILE:-$ROOT_DIR/deploy/k8s/02-secret.yml}
CHECK_NETWORK=${CHECK_NETWORK:-false}
AUTO_ROLLBACK=${AUTO_ROLLBACK:-true}
ROLLOUT_TIMEOUT=${ROLLOUT_TIMEOUT:-300s}
SNAPSHOT_DIR=${SNAPSHOT_DIR:-$ROOT_DIR/deploy/k8s/snapshots}
DIFF_SECRETS=${DIFF_SECRETS:-false}
INCLUDE_SECRETS_IN_SNAPSHOT=${INCLUDE_SECRETS_IN_SNAPSHOT:-false}
KEEP_ROLLBACK_ARTIFACTS=${KEEP_ROLLBACK_ARTIFACTS:-false}
ROLLBACK_ARTIFACT_DIR=${ROLLBACK_ARTIFACT_DIR:-}
DEPLOY_SCANNER=${DEPLOY_SCANNER:-true}

NAMESPACE_MANIFEST=$ROOT_DIR/deploy/k8s/00-namespace.yml
SERVICE_MANIFEST=$ROOT_DIR/deploy/k8s/06-services.yaml
SCANNER_MANIFEST=$ROOT_DIR/deploy/k8s/03-01-scanner-deployment.yaml
SERVER_MANIFEST=$ROOT_DIR/deploy/k8s/03-backend-deployment.yml
WEB_MANIFEST=$ROOT_DIR/deploy/k8s/04-frontend-deployment.yml
INGRESS_MANIFEST=$ROOT_DIR/deploy/k8s/05-ingress.yml

HARBOR_PULL_SECRET=harbor-regcred
INGRESS_NAME=skillhub-ingress

ROLLBACK_RESOURCES=(
  configmap/skillhub-config
  secret/skillhub-secret
  service/skillhub-server
  service/skillhub-scanner
  service/skillhub-web
  ingress/$INGRESS_NAME
)

ROLLBACK_WORK_DIR=
CONFIG_CHECKSUM=
SECRET_CHECKSUM=

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
Usage: deploy/k8s/safe-rollout.sh <command>

Commands:
  precheck  Validate config and render an object snapshot (no apply)
  diff      Run kubectl diff for all rollout manifests
  apply     Incremental rollout with health checks and optional auto rollback
  all       Run precheck + diff + apply
  rollback  Roll back app deployments to previous revision

Environment variables:
  NAMESPACE        Kubernetes namespace, default: skillhub
  CONFIGMAP_FILE   ConfigMap file path, default: deploy/k8s/01-configmap.yml
  SECRET_FILE      Secret file path, default: deploy/k8s/02-secret.yml
  CHECK_NETWORK    true/false for external dependency network probing
  AUTO_ROLLBACK    true/false rollback deployments when apply fails
  ROLLOUT_TIMEOUT  rollout status timeout, default: 300s
  SNAPSHOT_DIR     snapshot output dir, default: deploy/k8s/snapshots
  DIFF_SECRETS     true/false to include secret manifest in kubectl diff output
  INCLUDE_SECRETS_IN_SNAPSHOT true/false to include live Secret objects in snapshots
  KEEP_ROLLBACK_ARTIFACTS true/false to keep rollback backups after a successful apply
  ROLLBACK_ARTIFACT_DIR directory captured by a previous failed rollout; required for rollback command
  DEPLOY_SCANNER    true/false to include the scanner deployment rollout, default: true
EOF
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

require_file() {
  [[ -f "$1" ]] || die "File not found: $1"
}

manifest_files() {
  printf '%s\n' \
    "$NAMESPACE_MANIFEST" \
    "$CONFIGMAP_FILE" \
    "$SERVICE_MANIFEST" \
    "$SERVER_MANIFEST" \
    "$WEB_MANIFEST" \
    "$INGRESS_MANIFEST"

  if [[ "$DEPLOY_SCANNER" == "true" ]]; then
    printf '%s\n' "$SCANNER_MANIFEST"
  fi
}

active_deployments() {
  if [[ "$DEPLOY_SCANNER" == "true" ]]; then
    printf '%s\n' skillhub-scanner skillhub-server skillhub-web
  else
    printf '%s\n' skillhub-server skillhub-web
  fi
}

ensure_supported_namespace() {
  if [[ "$NAMESPACE" != "$MANIFEST_NAMESPACE" ]]; then
    die "NAMESPACE override is not supported by the checked-in manifests. Use NAMESPACE=$MANIFEST_NAMESPACE."
  fi
}

require_rollout_files() {
  local file
  require_file "$NAMESPACE_MANIFEST"
  require_file "$CONFIGMAP_FILE"
  require_file "$SECRET_FILE"
  require_file "$SERVICE_MANIFEST"
  require_file "$SERVER_MANIFEST"
  require_file "$WEB_MANIFEST"
  require_file "$INGRESS_MANIFEST"
  if [[ "$DEPLOY_SCANNER" == "true" ]]; then
    require_file "$SCANNER_MANIFEST"
  fi
  for file in $(manifest_files); do
    require_file "$file"
  done
}

timestamp() {
  date +%Y%m%d-%H%M%S
}

compute_checksums() {
  CONFIG_CHECKSUM=$(sha256sum "$CONFIGMAP_FILE" | awk '{print $1}')
  SECRET_CHECKSUM=$(sha256sum "$SECRET_FILE" | awk '{print $1}')
}

tls_secret_name() {
  awk '/secretName:/ {print $2; exit}' "$INGRESS_MANIFEST"
}

check_cluster_prerequisites() {
  info "Checking rollout prerequisites in namespace $NAMESPACE"
  kubectl -n "$NAMESPACE" get secret "$HARBOR_PULL_SECRET" >/dev/null 2>&1 || \
    die "Missing required image pull secret: $HARBOR_PULL_SECRET"

  local tls_secret
  tls_secret=$(tls_secret_name)
  if [[ -n "$tls_secret" ]]; then
    kubectl -n "$NAMESPACE" get secret "$tls_secret" >/dev/null 2>&1 || \
      die "Missing required ingress TLS secret: $tls_secret"
  fi
}

snapshot_cluster_state() {
  mkdir -p "$SNAPSHOT_DIR"
  local out="$SNAPSHOT_DIR/pre-release-${NAMESPACE}-$(timestamp).yaml"
  local resources="deploy,svc,cm,ingress"
  if [[ "$INCLUDE_SECRETS_IN_SNAPSHOT" == "true" ]]; then
    resources="$resources,secret"
  fi
  info "Exporting current cluster objects to $out"
  kubectl -n "$NAMESPACE" get "$resources" -o yaml >"$out"
}

init_rollback_work_dir() {
  if [[ -n "$ROLLBACK_ARTIFACT_DIR" ]]; then
    ROLLBACK_WORK_DIR=$ROLLBACK_ARTIFACT_DIR
    [[ -d "$ROLLBACK_WORK_DIR" ]] || die "ROLLBACK_ARTIFACT_DIR does not exist: $ROLLBACK_WORK_DIR"
    return
  fi

  mkdir -p "$SNAPSHOT_DIR"
  umask 077
  ROLLBACK_WORK_DIR=$(mktemp -d "$SNAPSHOT_DIR/rollback-${NAMESPACE}-XXXXXX")
}

use_existing_rollback_artifacts() {
  [[ -n "$ROLLBACK_ARTIFACT_DIR" ]] || \
    die "ROLLBACK_ARTIFACT_DIR is required for the rollback command."
  init_rollback_work_dir
}

resource_backup_file() {
  local resource=$1
  local kind=${resource%/*}
  local name=${resource#*/}
  printf '%s/%s-%s.json\n' "$ROLLBACK_WORK_DIR" "$kind" "$name"
}

resource_absent_file() {
  local resource=$1
  local kind=${resource%/*}
  local name=${resource#*/}
  printf '%s/%s-%s.absent\n' "$ROLLBACK_WORK_DIR" "$kind" "$name"
}

backup_resource() {
  local resource=$1
  local kind=${resource%/*}
  local name=${resource#*/}
  local backup_file
  local absent_file
  backup_file=$(resource_backup_file "$resource")
  absent_file=$(resource_absent_file "$resource")

  rm -f "$backup_file" "$absent_file"
  if kubectl -n "$NAMESPACE" get "$kind" "$name" -o json 2>/dev/null | \
    jq 'del(
      .metadata.resourceVersion,
      .metadata.uid,
      .metadata.creationTimestamp,
      .metadata.managedFields,
      .metadata.generation,
      .metadata.selfLink,
      .metadata.annotations."kubectl.kubernetes.io/last-applied-configuration",
      .status
    )' >"$backup_file"; then
    info "Backed up $resource to $backup_file"
  else
    rm -f "$backup_file"
    : >"$absent_file"
    info "No existing $resource; rollback will delete it if this rollout creates it"
  fi
}

prepare_rollback_artifacts() {
  init_rollback_work_dir
  capture_deployment_revisions
  local resource
  for resource in "${ROLLBACK_RESOURCES[@]}"; do
    backup_resource "$resource"
  done
}

deployment_revision_file() {
  printf '%s/deployment-revisions.tsv\n' "$ROLLBACK_WORK_DIR"
}

current_deployment_revision() {
  local dep=$1
  kubectl -n "$NAMESPACE" get deployment "$dep" -o jsonpath='{.metadata.annotations.deployment\.kubernetes\.io/revision}' 2>/dev/null || true
}

capture_deployment_revisions() {
  local dep
  local revision_file
  revision_file=$(deployment_revision_file)
  : >"$revision_file"

  for dep in $(active_deployments); do
    printf '%s\t%s\n' "$dep" "$(current_deployment_revision "$dep")" >>"$revision_file"
  done
}

saved_deployment_revision() {
  local dep=$1
  local revision_file
  revision_file=$(deployment_revision_file)
  [[ -f "$revision_file" ]] || return 0
  awk -F '\t' -v dep="$dep" '$1 == dep {print $2; exit}' "$revision_file"
}

restore_resource() {
  local resource=$1
  local kind=${resource%/*}
  local name=${resource#*/}
  local backup_file
  local absent_file
  backup_file=$(resource_backup_file "$resource")
  absent_file=$(resource_absent_file "$resource")

  if [[ -f "$backup_file" ]]; then
    kubectl apply -f "$backup_file" >/dev/null
    info "Restored $resource from rollback backup"
    return
  fi

  if [[ -f "$absent_file" ]]; then
    kubectl -n "$NAMESPACE" delete "$kind" "$name" --ignore-not-found >/dev/null
    info "Deleted $resource because it did not exist before the rollout"
  fi
}

rollback_managed_resources() {
  warn "Restoring ConfigMap, Secret, Services, and Ingress from pre-rollout backups"
  local resource
  local failures=0
  for resource in "${ROLLBACK_RESOURCES[@]}"; do
    if ! restore_resource "$resource"; then
      warn "Failed to restore $resource"
      failures=$((failures + 1))
    fi
  done
  return "$failures"
}

cleanup_rollout_artifacts() {
  if [[ -n "$ROLLBACK_WORK_DIR" && "$KEEP_ROLLBACK_ARTIFACTS" != "true" ]]; then
    rm -rf "$ROLLBACK_WORK_DIR"
  fi
}

run_precheck() {
  ensure_supported_namespace
  require_rollout_files
  require_file "$ROOT_DIR/scripts/validate-k8s-external-deps.sh"
  check_cluster_prerequisites
  info "Running external dependency validation"
  CHECK_NETWORK="$CHECK_NETWORK" bash "$ROOT_DIR/scripts/validate-k8s-external-deps.sh" "$CONFIGMAP_FILE" "$SECRET_FILE"
  snapshot_cluster_state
}

run_diff_all() {
  local file
  ensure_supported_namespace
  require_rollout_files
  for file in $(manifest_files); do
    info "kubectl diff -f $file"
    set +e
    kubectl diff -f "$file"
    local code=$?
    set -e
    if [[ $code -gt 1 ]]; then
      die "kubectl diff failed for $file"
    fi
  done

  if [[ "$DIFF_SECRETS" == "true" ]]; then
    info "kubectl diff -f $SECRET_FILE"
    set +e
    kubectl diff -f "$SECRET_FILE"
    local secret_code=$?
    set -e
    if [[ $secret_code -gt 1 ]]; then
      die "kubectl diff failed for $SECRET_FILE"
    fi
  else
    info "Skipping kubectl diff for secret manifest; set DIFF_SECRETS=true to opt in"
  fi
}

rollout_status_all() {
  local dep
  for dep in $(active_deployments); do
    info "Checking rollout status for deployment/$dep"
    kubectl -n "$NAMESPACE" rollout status "deployment/$dep" --timeout "$ROLLOUT_TIMEOUT"
  done
}

patch_rollout_annotations() {
  local include_secret=$1
  local patch

  patch=$(cat <<EOF
{"spec":{"template":{"metadata":{"annotations":{"skillhub.io/config-checksum":"$CONFIG_CHECKSUM"$(if [[ "$include_secret" == "true" ]]; then printf ',"skillhub.io/secret-checksum":"%s"' "$SECRET_CHECKSUM"; fi)}}}}}
EOF
)

  printf '%s\n' "$patch"
}

apply_deployment_manifest() {
  local manifest=$1
  local deployment=$2
  local include_secret=$3

  kubectl patch --local -f "$manifest" --type=merge -p "$(patch_rollout_annotations "$include_secret")" -o yaml | \
    kubectl apply -f - >/dev/null
  kubectl -n "$NAMESPACE" rollout status "deployment/$deployment" --timeout "$ROLLOUT_TIMEOUT"
}

rollback_deployments() {
  local dep
  local previous_revision
  local current_revision
  local failures=0
  warn "Rolling back deployments to previous revision"
  for dep in $(active_deployments); do
    previous_revision=$(saved_deployment_revision "$dep")
    current_revision=$(current_deployment_revision "$dep")

    if [[ -z "$previous_revision" || "$previous_revision" == "$current_revision" ]]; then
      info "Skipping deployment/$dep rollback because this rollout did not change its revision"
      continue
    fi

    set +e
    kubectl -n "$NAMESPACE" rollout undo "deployment/$dep"
    local code=$?
    set -e
    if [[ $code -ne 0 ]]; then
      warn "rollback skipped/failed for deployment/$dep"
      failures=$((failures + 1))
    fi
  done
  return "$failures"
}

rollback_all() {
  local failures=0

  if [[ -z "$ROLLBACK_WORK_DIR" ]]; then
    die "No rollback artifacts available. Set ROLLBACK_ARTIFACT_DIR to a saved rollback directory."
  fi

  if ! rollback_managed_resources; then
    failures=$((failures + 1))
  fi

  if ! rollback_deployments; then
    failures=$((failures + 1))
  fi

  if (( failures > 0 )); then
    die "Rollback completed with errors. Inspect cluster state and rollback artifacts under $ROLLBACK_WORK_DIR"
  fi

  warn "Rollback completed successfully using artifacts in $ROLLBACK_WORK_DIR"
}

handle_apply_error() {
  local exit_code=$?
  trap - ERR
  warn "Rollout failed"
  if [[ "$AUTO_ROLLBACK" == "true" ]]; then
    rollback_all || true
    warn "Rollback artifacts preserved at $ROLLBACK_WORK_DIR"
  else
    warn "Automatic rollback disabled. Rollback artifacts preserved at $ROLLBACK_WORK_DIR"
  fi
  exit "$exit_code"
}

apply_incremental() {
  ensure_supported_namespace
  require_rollout_files
  compute_checksums
  info "Applying namespace/config/secret/services"
  kubectl apply -f "$NAMESPACE_MANIFEST"
  kubectl apply -f "$CONFIGMAP_FILE"
  kubectl apply -f "$SECRET_FILE"
  kubectl apply -f "$SERVICE_MANIFEST"

  if [[ "$DEPLOY_SCANNER" == "true" ]]; then
    info "Applying scanner deployment"
    apply_deployment_manifest "$SCANNER_MANIFEST" skillhub-scanner true
  else
    info "Skipping scanner deployment because DEPLOY_SCANNER=false"
  fi

  info "Applying backend deployment"
  apply_deployment_manifest "$SERVER_MANIFEST" skillhub-server true

  info "Applying frontend deployment"
  apply_deployment_manifest "$WEB_MANIFEST" skillhub-web false

  info "Applying ingress"
  kubectl apply -f "$INGRESS_MANIFEST"

  rollout_status_all
}

main() {
  require_cmd kubectl
  require_cmd jq
  require_cmd sha256sum
  ensure_supported_namespace
  local command=${1:-}
  case "$command" in
    precheck)
      run_precheck
      ;;
    diff)
      run_diff_all
      ;;
    apply)
      prepare_rollback_artifacts
      if [[ "$AUTO_ROLLBACK" == "true" ]]; then
        trap 'handle_apply_error' ERR
      fi
      apply_incremental
      trap - ERR
      cleanup_rollout_artifacts
      ;;
    all)
      run_precheck
      run_diff_all
      prepare_rollback_artifacts
      if [[ "$AUTO_ROLLBACK" == "true" ]]; then
        trap 'handle_apply_error' ERR
      fi
      apply_incremental
      trap - ERR
      cleanup_rollout_artifacts
      ;;
    rollback)
      use_existing_rollback_artifacts
      rollback_all
      ;;
    *)
      usage
      exit 1
      ;;
  esac

  info "Done"
}

main "$@"
