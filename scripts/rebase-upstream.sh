#!/usr/bin/env bash
# -------------------------------------------------------------------
# rebase-upstream.sh — 将二开主分支 rebase 到上游最新版本之上
#
# 用法:
#   ./scripts/rebase-upstream.sh [--dry-run] [--upstream-ref <ref>]
#
# 前提:
#   1. 已添加 upstream remote:  git remote add upstream <URL>
#   2. 工作区干净 (无未提交变更)
#   3. 当前分支为 dev 或通过 CUSTOM_BRANCH 环境变量指定
#
# 行为:
#   1. fetch upstream
#   2. 检测二开提交范围
#   3. 预演冲突 (--dry-run 模式下到此为止)
#   4. 执行 rebase --onto upstream/<ref>
#   5. 验证 migration 版本号无冲突
# -------------------------------------------------------------------
set -euo pipefail

# ── 配置 ──────────────────────────────────────────────────────────
CUSTOM_BRANCH="${CUSTOM_BRANCH:-dev}"
UPSTREAM_REMOTE="${UPSTREAM_REMOTE:-upstream}"
UPSTREAM_REF="${UPSTREAM_REF:-main}"
MIGRATION_DIR="server/skillhub-app/src/main/resources/db/migration"
DRY_RUN=false

# ── 参数解析 ──────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=true; shift ;;
    --upstream-ref) UPSTREAM_REF="$2"; shift 2 ;;
    --branch) CUSTOM_BRANCH="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

UPSTREAM_FULL="${UPSTREAM_REMOTE}/${UPSTREAM_REF}"

# ── 工具函数 ──────────────────────────────────────────────────────
info()  { printf '\033[36m[INFO]\033[0m  %s\n' "$*"; }
warn()  { printf '\033[33m[WARN]\033[0m  %s\n' "$*"; }
error() { printf '\033[31m[ERROR]\033[0m %s\n' "$*" >&2; }
die()   { error "$@"; exit 1; }

# ── 前置检查 ──────────────────────────────────────────────────────
info "检查前置条件..."

if ! git remote get-url "$UPSTREAM_REMOTE" &>/dev/null; then
  die "未找到 remote '$UPSTREAM_REMOTE'。请先执行:\n  git remote add $UPSTREAM_REMOTE <上游仓库地址>"
fi

if ! git diff --quiet HEAD 2>/dev/null || ! git diff --cached --quiet HEAD 2>/dev/null; then
  die "工作区有未提交变更，请先 stash 或 commit"
fi

CURRENT_BRANCH=$(git symbolic-ref --short HEAD 2>/dev/null || true)
if [[ "$CURRENT_BRANCH" != "$CUSTOM_BRANCH" ]]; then
  info "切换到 $CUSTOM_BRANCH..."
  git checkout "$CUSTOM_BRANCH" || die "无法切换到 $CUSTOM_BRANCH"
fi

# ── Fetch 上游 ────────────────────────────────────────────────────
info "Fetching $UPSTREAM_REMOTE..."
git fetch "$UPSTREAM_REMOTE" --prune

# ── 检测二开范围 ──────────────────────────────────────────────────
MERGE_BASE=$(git merge-base "$CUSTOM_BRANCH" "$UPSTREAM_FULL")
CUSTOM_COUNT=$(git rev-list --count "$MERGE_BASE".."$CUSTOM_BRANCH")
UPSTREAM_NEW=$(git rev-list --count "$MERGE_BASE".."$UPSTREAM_FULL")

info "共同祖先: ${MERGE_BASE:0:10}"
info "二开独有提交: $CUSTOM_COUNT"
info "上游新增提交: $UPSTREAM_NEW"

if [[ "$UPSTREAM_NEW" -eq 0 ]]; then
  info "上游无新提交，无需 rebase"
  exit 0
fi

# ── 列出二开提交 ──────────────────────────────────────────────────
info "二开提交清单:"
git log --oneline --reverse "$MERGE_BASE".."$CUSTOM_BRANCH" | while IFS= read -r line; do
  echo "  $line"
done

# ── 检测潜在冲突文件 ──────────────────────────────────────────────
OVERLAP_FILES=$(comm -12 \
  <(git diff --name-only "$MERGE_BASE".."$CUSTOM_BRANCH" | sort) \
  <(git diff --name-only "$MERGE_BASE".."$UPSTREAM_FULL" | sort))

if [[ -n "$OVERLAP_FILES" ]]; then
  OVERLAP_COUNT=$(echo "$OVERLAP_FILES" | wc -l | tr -d ' ')
  warn "检测到 $OVERLAP_COUNT 个文件在两边都有修改 (潜在冲突):"
  echo "$OVERLAP_FILES" | while IFS= read -r f; do
    echo "  ⚠ $f"
  done
else
  info "无重叠文件，预计可以干净 rebase"
fi

# ── Migration 版本号检查 ──────────────────────────────────────────
info "检查 migration 版本号..."

UPSTREAM_MAX_V=$(git ls-tree --name-only "$UPSTREAM_FULL" -- "$MIGRATION_DIR" 2>/dev/null \
  | grep -oE 'V[0-9]+' | sed 's/V//' | sort -n | tail -1)
UPSTREAM_MAX_V="${UPSTREAM_MAX_V:-0}"

CUSTOM_MIGRATIONS=$(git diff --name-only "$MERGE_BASE".."$CUSTOM_BRANCH" -- "$MIGRATION_DIR" \
  | grep -oE 'V[0-9]+' | sed 's/V//' | sort -n)

if [[ -n "$CUSTOM_MIGRATIONS" ]]; then
  CUSTOM_MIN_V=$(echo "$CUSTOM_MIGRATIONS" | head -1)
  CUSTOM_MAX_V=$(echo "$CUSTOM_MIGRATIONS" | tail -1)
  info "上游最高 migration: V${UPSTREAM_MAX_V}"
  info "二开 migration 范围: V${CUSTOM_MIN_V} - V${CUSTOM_MAX_V}"

  if [[ "$CUSTOM_MIN_V" -le "$UPSTREAM_MAX_V" ]]; then
    warn "二开 migration 版本号 (V${CUSTOM_MIN_V}) <= 上游最高版本 (V${UPSTREAM_MAX_V})"
    warn "rebase 后需要重命名 migration 文件，建议使用 V$((UPSTREAM_MAX_V + 1000))+ 前缀"
    warn "参见操作规范中的 'Migration 版本号策略'"
  fi
else
  info "二开无新增 migration"
fi

# ── Dry-run 模式 ──────────────────────────────────────────────────
if [[ "$DRY_RUN" == true ]]; then
  info "[DRY-RUN] 预演完成，未执行实际 rebase"
  info "[DRY-RUN] 去掉 --dry-run 参数执行实际操作"
  exit 0
fi

# ── 备份当前位置 ──────────────────────────────────────────────────
BACKUP_REF="backup/${CUSTOM_BRANCH}-before-rebase-$(date +%Y%m%d-%H%M%S)"
git branch "$BACKUP_REF" "$CUSTOM_BRANCH"
info "已创建备份分支: $BACKUP_REF"

# ── 执行 rebase ───────────────────────────────────────────────────
info "开始 rebase: $CUSTOM_BRANCH --onto $UPSTREAM_FULL (基于 $MERGE_BASE)"
echo ""
echo "  如果遇到冲突:"
echo "    1. 解决冲突文件"
echo "    2. git add <冲突文件>"
echo "    3. git rebase --continue"
echo "    4. 如果无法解决: git rebase --abort"
echo "       然后恢复: git reset --hard $BACKUP_REF"
echo ""

if git rebase --onto "$UPSTREAM_FULL" "$MERGE_BASE" "$CUSTOM_BRANCH"; then
  info "Rebase 成功完成!"
else
  warn "Rebase 中有冲突需要手动解决"
  warn "解决后执行: git rebase --continue"
  warn "放弃回退: git rebase --abort && git reset --hard $BACKUP_REF"
  exit 1
fi

# ── 后置检查 ──────────────────────────────────────────────────────
NEW_COUNT=$(git rev-list --count "$UPSTREAM_FULL".."$CUSTOM_BRANCH")
info "Rebase 后二开提交数: $NEW_COUNT (原: $CUSTOM_COUNT)"

if ls "$MIGRATION_DIR"/V*.sql &>/dev/null; then
  VERSIONS=$(ls "$MIGRATION_DIR"/V*.sql | grep -oE 'V[0-9]+' | sed 's/V//' | sort -n)
  PREV=""
  while IFS= read -r v; do
    if [[ -n "$PREV" ]] && [[ "$((PREV + 1))" -ne "$v" ]]; then
      :
    fi
    PREV="$v"
  done <<< "$VERSIONS"

  DUPES=$(echo "$VERSIONS" | sort | uniq -d)
  if [[ -n "$DUPES" ]]; then
    error "检测到重复 migration 版本号: $DUPES"
    error "请手动重命名冲突的 migration 文件"
    exit 1
  fi
  info "Migration 版本号无重复"
fi

info ""
info "下一步操作:"
info "  1. 运行测试:  make test"
info "  2. force push: git push origin $CUSTOM_BRANCH --force-with-lease"
info "  3. 通知团队 rebase feature 分支:"
info "     git checkout <feature-branch>"
info "     git rebase $CUSTOM_BRANCH"
info ""
info "备份分支: $BACKUP_REF (确认无误后可删除)"