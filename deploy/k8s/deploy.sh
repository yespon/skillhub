#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd -- "$SCRIPT_DIR/../.." && pwd)

REGISTRY=${REGISTRY:-harbor.ruijie.com.cn}
PROJECT=${PROJECT:-skillhub}
TAG=${TAG:-release-r0.1.0}
HARBOR_USERNAME=${HARBOR_USERNAME:-admin}
HARBOR_PASSWORD=${HARBOR_PASSWORD:-}

SERVER_IMAGE="$REGISTRY/$PROJECT/skillhub-server:$TAG"
WEB_IMAGE="$REGISTRY/$PROJECT/skillhub-web:$TAG"

usage() {
  cat <<'EOF'
Usage: deploy/k8s/deploy.sh <command>

Commands:
  login         Log in to Harbor
  mirror-deps   Mirror postgres, redis, and minio images to Harbor
  push-app      Build and push skillhub-server and skillhub-web images
  all           Log in, mirror dependencies, then build and push app images

Environment variables:
  REGISTRY          Harbor registry, default harbor.ruijie.com.cn
  PROJECT           Harbor project, default skillhub
  TAG               App image tag, default release-r0.1.0
  HARBOR_USERNAME   Harbor username, default admin
  HARBOR_PASSWORD   Harbor password, required for login if not already logged in
EOF
}

require_password() {
  if [[ -z "$HARBOR_PASSWORD" ]]; then
    echo "HARBOR_PASSWORD is required" >&2
    exit 1
  fi
}

login() {
  require_password
  printf '%s' "$HARBOR_PASSWORD" | docker login "$REGISTRY" -u "$HARBOR_USERNAME" --password-stdin
}

mirror_one() {
  local source_image=$1
  local target_image=$2

  docker pull "$source_image"
  docker tag "$source_image" "$target_image"
  docker push "$target_image"
}

mirror_deps() {
  mirror_one "postgres:16-alpine" "$REGISTRY/$PROJECT/postgres:16-alpine"
  mirror_one "redis:7-alpine" "$REGISTRY/$PROJECT/redis:7-alpine"
  mirror_one "minio/minio:latest" "$REGISTRY/$PROJECT/minio:latest"
}

push_app() {
  docker build -t "$SERVER_IMAGE" -f "$ROOT_DIR/server/Dockerfile" "$ROOT_DIR/server"
  docker push "$SERVER_IMAGE"

  docker build -t "$WEB_IMAGE" -f "$ROOT_DIR/web/Dockerfile" "$ROOT_DIR/web"
  docker push "$WEB_IMAGE"
}

main() {
  local command=${1:-}
  case "$command" in
    login)
      login
      ;;
    mirror-deps)
      mirror_deps
      ;;
    push-app)
      push_app
      ;;
    all)
      login
      mirror_deps
      push_app
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"