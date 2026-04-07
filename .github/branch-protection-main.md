name: Protect main (upstream mirror)

# 应用于 main 分支
on:
  branch_protection_rule:

# 此文件仅作为文档记录，实际保护需在 GitHub UI 配置。
# 下面是需要在 GitHub Settings → Rules → Rulesets 中配置的内容：
#
# ┌─────────────────────────────────────────────────────────────┐
# │  Ruleset: protect-main-upstream                             │
# │  Target: main                                               │
# │  Enforcement: Active                                        │
# │                                                             │
# │  Rules:                                                     │
# │  ✅ Restrict deletions          — 禁止删除 main            │
# │  ✅ Block force pushes          — 禁止 force push          │
# │  ✅ Require pull request        — 禁止直接 push            │
# │     • Required approvals: 0     (机器人同步不需要审批)      │
# │     • Dismiss stale reviews: ✅                             │
# │  ✅ Restrict pushes             — 只允许 github-actions    │
# │     • Actors: github-actions[bot]                           │
# │                                                             │
# │  Bypass list:                                               │
# │     • github-actions[bot]       (自动同步 workflow 需要)    │
# │     • Repository admin          (紧急修复)                  │
# └─────────────────────────────────────────────────────────────┘
#
# 配置路径: GitHub → Settings → Rules → Rulesets → New ruleset
