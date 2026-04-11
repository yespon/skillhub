#!/usr/bin/env bash
# -------------------------------------------------------------------
# check-customization-drift.sh — 检测二开分支与上游的漂移状况
#
# 用于 CI 或手动检查，输出:
#   1. 二开提交数和文件数
#   2. 与上游重叠的文件 (冲突热点)
#   3. Migration 版本号合规性
#   4. 上游未合入的新提交数
#
# 用法:
#   ./scripts/check-customization-drift.sh [--fail-on-overlap <N>]
#
# 退出码:
#   0 = 正常
#   1 = 超出阈值或版本号冲突
# -------------------------------------------------------------------
set -euo pipefail

CUSTOM_BRANCH="${CUSTOM_BRANCH:-dev}"
UPSTREAM_REMOTE="${UPSTREAM_REMOTE:-upstream}"
UPSTREAM_REF="${UPSTREAM_REF:-main}"
MIGRATION_DIR="server/skillhub-app/src/main/resources/db/migration"
FAIL_ON_OVERLAP=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --fail-on-overlap) FAIL_ON_OVERLAP="$2"; shift 2 ;;
    *) shift ;;
  esac
done

UPSTREAM_FULL="${UPSTREAM_REMOTE}/${UPSTREAM_REF}"

info()  { printf '\033[36m[INFO]\033[0m  %s\n' "$*"; }
warn()  { printf '\033[33m[WARN]\033[0m  %s\n' "$*"; }
error() { printf '\033[31m[ERROR]\033[0m %s\n' "$*" >&2; }

EXIT_CODE=0

if git remote get-url "$UPSTREAM_REMOTE" &>/dev/null; then
  git fetch "$UPSTREAM_REMOTE" --prune --quiet
else
  warn "未配置 upstream remote，使用 origin/main 作为上游参考"
  UPSTREAM_FULL="origin/main"
fi

MERGE_BASE=$(git merge-base "$CUSTOM_BRANCH" "$UPSTREAM_FULL" 2>/dev/null || echo "")
if [[ -z "$MERGE_BASE" ]]; then
  error "无法找到 $CUSTOM_BRANCH 与 $UPSTREAM_FULL 的共同祖先"
  exit 1
fi

CUSTOM_COMMITS=$(git rev-list --count "$MERGE_BASE".."$CUSTOM_BRANCH")
CUSTOM_FILES=$(git diff --name-only "$MERGE_BASE".."$CUSTOM_BRANCH" | wc -l | tr -d ' ')
UPSTREAM_PENDING=$(git rev-list --count "$MERGE_BASE".."$UPSTREAM_FULL")

echo "========================================"
echo "  SkillHub 二开漂移报告"
echo "  $(date '+%Y-%m-%d %H:%M')"
echo "========================================"
echo ""
echo "  二开分支: $CUSTOM_BRANCH"
echo "  上游参考: $UPSTREAM_FULL"
echo "  共同祖先: ${MERGE_BASE:0:10}"
echo ""
echo "  二开独有提交: $CUSTOM_COMMITS"
echo "  二开改动文件: $CUSTOM_FILES"
echo "  上游待合入:   $UPSTREAM_PENDING"
echo ""

OVERLAP=$(comm -12 \
  <(git diff --name-only "$MERGE_BASE".."$CUSTOM_BRANCH" | sort) \
  <(git diff --name-only "$MERGE_BASE".."$UPSTREAM_FULL" | sort))

if [[ -n "$OVERLAP" ]]; then
  OVERLAP_COUNT=$(echo "$OVERLAP" | wc -l | tr -d ' ')
  echo "  冲突热点文件: $OVERLAP_COUNT"
  echo "  ────────────────────────────────"
  echo "$OVERLAP" | while IFS= read -r f; do
    echo "    ⚠ $f"
  done
  echo ""

  if [[ "$FAIL_ON_OVERLAP" -gt 0 ]] && [[ "$OVERLAP_COUNT" -gt "$FAIL_ON_OVERLAP" ]]; then
    error "重叠文件数 ($OVERLAP_COUNT) 超过阈值 ($FAIL_ON_OVERLAP)"
    EXIT_CODE=1
  fi
else
  echo "  冲突热点文件: 0 ✓"
  echo ""
fi

echo "  二开改动分布:"
echo "  ────────────────────────────────"
git diff --name-only "$MERGE_BASE".."$CUSTOM_BRANCH" | awk -F/ '
  /^server\/skillhub-domain/    { domain++ }
  /^server\/skillhub-app/       { app++ }
  /^server\/skillhub-auth/      { auth++ }
  /^server\/skillhub-search/    { search++ }
  /^server\/skillhub-infra/     { infra++ }
  /^server\/skillhub-storage/   { storage++ }
  /^web\//                      { web++ }
  /^deploy\//                   { deploy++ }
  /^scripts\//                  { scripts++ }
  /^docs\//                     { docs++ }
  END {
    if (domain)  printf "    server/domain:   %d files\n", domain
    if (app)     printf "    server/app:      %d files\n", app
    if (auth)    printf "    server/auth:     %d files\n", auth
    if (search)  printf "    server/search:   %d files\n", search
    if (infra)   printf "    server/infra:    %d files\n", infra
    if (storage) printf "    server/storage:  %d files\n", storage
    if (web)     printf "    web/:            %d files\n", web
    if (deploy)  printf "    deploy/:         %d files\n", deploy
    if (scripts) printf "    scripts/:        %d files\n", scripts
    if (docs)    printf "    docs/:           %d files\n", docs
  }
'
echo ""

echo "  Migration 版本号:"
echo "  ────────────────────────────────"

UPSTREAM_MAX_V=$(git ls-tree --name-only "$UPSTREAM_FULL" -- "$MIGRATION_DIR" 2>/dev/null \
  | grep -oE 'V[0-9]+' | sed 's/V//' | sort -n | tail -1)
UPSTREAM_MAX_V="${UPSTREAM_MAX_V:-0}"

CUSTOM_ONLY_MIGRATIONS=$(git diff --name-only "$MERGE_BASE".."$CUSTOM_BRANCH" -- "$MIGRATION_DIR" \
  | grep -oE 'V[0-9]+' | sed 's/V//' | sort -n)

echo "    上游最高版本: V${UPSTREAM_MAX_V}"

if [[ -n "$CUSTOM_ONLY_MIGRATIONS" ]]; then
  CUSTOM_MIN_V=$(echo "$CUSTOM_ONLY_MIGRATIONS" | head -1)
  CUSTOM_MAX_V=$(echo "$CUSTOM_ONLY_MIGRATIONS" | tail -1)
  echo "    二开新增范围: V${CUSTOM_MIN_V} - V${CUSTOM_MAX_V}"

  if [[ "$CUSTOM_MIN_V" -le "$UPSTREAM_MAX_V" ]]; then
    error "  版本号冲突! 二开 V${CUSTOM_MIN_V} <= 上游 V${UPSTREAM_MAX_V}"
    error "  建议重命名二开 migration 到 V$((UPSTREAM_MAX_V + 1000))+"
    EXIT_CODE=1
  else
    echo "    状态: ✓ 无冲突"
  fi
else
  echo "    二开无新增 migration"
fi

echo ""

if [[ "$UPSTREAM_PENDING" -gt 0 ]]; then
  echo "  上游待合入提交:"
  echo "  ────────────────────────────────"
  git log --oneline --reverse "$MERGE_BASE".."$UPSTREAM_FULL" | head -20 | while IFS= read -r line; do
    echo "    $line"
  done
  REMAINING=$((UPSTREAM_PENDING - 20))
  if [[ "$REMAINING" -gt 0 ]]; then
    echo "    ... 还有 $REMAINING 个提交"
  fi
  echo ""
fi

echo "========================================"
if [[ "$EXIT_CODE" -eq 0 ]]; then
  echo "  状态: ✓ 通过"
else
  echo "  状态: ✗ 需要处理"
fi
echo "========================================"

exit "$EXIT_CODE"