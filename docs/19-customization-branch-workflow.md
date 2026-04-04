# 二开分支管理规范

> 适用于 SkillHub 从外部上游仓库拉取并进行二次开发的场景。

## 1 分支模型

```
upstream/main  ◀── 上游只读镜像 (git remote: upstream)
      │
      │  定期 rebase
      ▼
    main-r  ────────── 二开主分支 (所有二开提交叠在上游之上)
      │
      ├── feature/*    二开功能分支
      ├── fix/*        二开修复分支
      └── release-r*   二开发布分支
```

### 核心原则

| # | 原则 | 说明 |
|---|------|------|
| 1 | **上游只读** | `upstream` remote 只 fetch，永远不 push |
| 2 | **二开在上** | main-r 的二开提交永远 rebase 在 upstream/main 之上 |
| 3 | **单一真相** | `git log upstream/main..main-r` = 完整的二开差异 |
| 4 | **版本号隔离** | 二开 migration 版本号从 V1000+ 开始，避免与上游冲突 |

## 2 初始配置 (一次性)

```bash
# 添加上游 remote
git remote add upstream <上游仓库地址>
git fetch upstream

# 确认当前状态
./scripts/check-customization-drift.sh
```

## 3 日常开发流程

### 3.1 开发新功能

```bash
# 从 main-r 拉 feature 分支
git checkout main-r
git pull origin main-r
git checkout -b feature/<name>

# ... 开发 ...

# 完成后合回 main-r
git checkout main-r
git merge --no-ff feature/<name>
git push origin main-r
git branch -d feature/<name>
```

### 3.2 同步上游更新

**频率**: 每周一次，或上游发新版时立即同步。

```bash
# 方式一: 使用脚本 (推荐)
./scripts/rebase-upstream.sh --dry-run    # 先预演
./scripts/rebase-upstream.sh              # 确认后执行

# 方式二: 手动操作
git fetch upstream
git checkout main-r
MERGE_BASE=$(git merge-base main-r upstream/main)
git rebase --onto upstream/main $MERGE_BASE main-r
```

**Rebase 后**:

```bash
# 1. 跑测试
make test

# 2. force push (必须用 --force-with-lease)
git push origin main-r --force-with-lease

# 3. 通知团队 rebase 各自的 feature 分支
#    团队成员执行:
git checkout feature/<my-branch>
git fetch origin
git rebase origin/main-r
```

### 3.3 处理 Rebase 冲突

```bash
# 冲突时 git 会暂停，显示冲突文件
git status

# 编辑冲突文件，解决后:
git add <file>
git rebase --continue

# 如果某个提交完全不需要了:
git rebase --skip

# 放弃整个 rebase:
git rebase --abort
```

**常见冲突类型及处理**:

| 冲突类型 | 表现 | 处理方法 |
|----------|------|----------|
| i18n 文案 | en.json / zh.json 冲突 | 保留双方新增的 key，检查无重复 |
| 前端页面 | search.tsx / skill-detail.tsx | 先接受上游结构，再把二开改动重新应用 |
| API 类型 | schema.d.ts 冲突 | 解决后执行 `make generate-api` 重新生成 |
| Migration | V 版本号冲突 | 重命名二开 migration，参见下方策略 |

## 4 Migration 版本号策略

### 约定

- **上游 migration**: V1 - V999 (由上游管理)
- **二开 migration**: V1000+ (二开独占空间)

### 迁移步骤 (首次)

当前二开 migration 是 V39-V42，需要一次性重命名:

```bash
cd server/skillhub-app/src/main/resources/db/migration/

# 重命名 (根据实际文件调整)
git mv V39__optimize_skill_label_search_facets.sql   V1001__optimize_skill_label_search_facets.sql
git mv V40__skill_display_name_translation.sql       V1002__skill_display_name_translation.sql
git mv V41__skill_translation_task.sql               V1003__skill_translation_task.sql
git mv V42__skill_translation_task_claim.sql         V1004__skill_translation_task_claim.sql

git commit -m "chore(db): relocate custom migrations to V1000+ namespace"
```

### 已有数据库迁移

对于已经跑过 V39-V42 的环境，需要更新 Flyway 的 schema_history 表:

```sql
-- 在目标数据库执行 (备份后!)
UPDATE flyway_schema_history SET version = '1001' WHERE version = '39';
UPDATE flyway_schema_history SET version = '1002' WHERE version = '40';
UPDATE flyway_schema_history SET version = '1003' WHERE version = '41';
UPDATE flyway_schema_history SET version = '1004' WHERE version = '42';
```

## 5 CI/CD 集成

### 漂移检查 (建议加入 CI)

```yaml
# GitHub Actions 示例
- name: Check customization drift
  run: ./scripts/check-customization-drift.sh --fail-on-overlap 30
```

阈值说明:
- `--fail-on-overlap 30`: 当二开与上游重叠文件超过 30 个时 CI 失败
- 随着项目演进可调整阈值

### 定期报告

建议配置定时任务 (cron)，每周跑一次漂移报告，发送到团队群:

```bash
./scripts/check-customization-drift.sh 2>&1 | tee drift-report.txt
```

## 6 发布流程

```
main-r ──→ release-r<version> ──→ tag ──→ 部署
```

1. 从 main-r 切出 `release-r<version>` 分支
2. 在 release 分支上只做 bugfix，不加新功能
3. 打 tag 后部署
4. release 分支的 fix 合回 main-r

## 7 紧急操作

### 从上游 cherry-pick 单个修复

不需要做完整 rebase，可以直接 cherry-pick:

```bash
git checkout main-r
git cherry-pick -x <upstream-commit-hash>
git push origin main-r
```

`-x` 参数会在 commit message 中记录来源。

### 回退某次 rebase

```bash
# rebase-upstream.sh 自动创建了备份分支
git reflog                              # 找到 rebase 前的 HEAD
git reset --hard backup/main-r-before-rebase-<timestamp>
git push origin main-r --force-with-lease
```

## 8 团队协作约定

| 约定 | 内容 |
|------|------|
| **Rebase 通知** | rebase main-r 前在群里通知，给 10 分钟窗口让大家 push 未完成的工作 |
| **Feature 分支生命周期** | 不超过 2 周；超时需先 rebase main-r |
| **Commit message** | 二开提交按现有规范；从上游 cherry-pick 的保留原始 message |
| **Code review** | 所有合入 main-r 的 PR 需要至少 1 人 review |
| **Force push** | 只允许 main-r 在 rebase 后 force push，其他分支禁止 |
