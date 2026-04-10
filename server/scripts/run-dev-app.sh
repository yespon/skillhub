#!/usr/bin/env bash

set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE="${SPRING_PROFILES_ACTIVE:-local}"

# Ensure Java 21+ is used because login shells may resolve to an older system JVM.
if [[ -z "${JAVA_HOME:-}" ]]; then
  BREW_JAVA="/opt/homebrew/opt/openjdk@21/bin/java"
  if [[ -x "$BREW_JAVA" ]]; then
    JAVA_HOME="$(cd "$(dirname "$BREW_JAVA")/.." && pwd)"
  elif command -v /usr/libexec/java_home >/dev/null 2>&1; then
    candidate_java_home="$(/usr/libexec/java_home -v 21 2>/dev/null)" || true
    if [[ -n "${candidate_java_home:-}" ]] \
      && "$candidate_java_home/bin/java" -version 2>&1 | grep -q 'version "21'; then
      JAVA_HOME="$candidate_java_home"
    fi
    unset candidate_java_home
  fi
  export JAVA_HOME
fi

cd "$SERVER_DIR"

./mvnw -pl skillhub-app -am clean package -DskipTests >/dev/null

APP_JAR="$(find skillhub-app/target -maxdepth 1 -type f -name 'skillhub-app-*.jar' ! -name '*.original' | head -n 1)"
if [[ -z "$APP_JAR" ]]; then
  echo "Could not locate packaged skillhub-app jar under skillhub-app/target" >&2
  exit 1
fi

exec "${JAVA_BIN:-java}" -jar "$APP_JAR" --spring.profiles.active="$PROFILE" "$@"
