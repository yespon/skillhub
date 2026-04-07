# Protect main (upstream mirror)

此文件是 GitHub Ruleset 配置说明，目标是让 `main` 只作为上游镜像分支：

- 人工不能直接 push 到 `main`
- 人工不能把 PR 合并到 `main`
- 只有同步机器人可以更新 `main`

推荐先准备一个机器人凭证，再启用 ruleset：

1. 新建一个专用 bot 账号或 fine-grained PAT
2. 给当前仓库授予 `Contents: Read and write`
3. 把 token 保存为仓库 secret: `MIRROR_SYNC_TOKEN`
4. `sync-upstream.yml` 会优先使用这个 secret 推送到 `main`

在 GitHub 中按下面配置创建 ruleset：

- 路径：Settings → Rules → Rulesets → New branch ruleset
- Name：`protect-main-upstream`
- Enforcement status：`Active`
- Target branches：`main`

Bypass list：

- Bot 账号或对应 GitHub App
- Repository administrators

Rules：

- Enable `Restrict deletions`
- Enable `Block force pushes`
- Enable `Require linear history`
- Enable `Require a pull request before merging`
- Enable `Restrict updates`

`Restrict updates` 的效果是：

- 非 bypass actor 不能直接 push 到 `main`
- 非 bypass actor 也不能通过合并 PR 更新 `main`

这样配置后：

- 日常开发全部走 `dev`
- `main` 只接受 `sync-upstream.yml` 的 fast-forward 推送
- 如果上游和本地 `main` 发生分叉，workflow 会失败并要求人工介入

验证标准：

- 普通开发账号向 `main` 开 PR，无法 merge
- 普通开发账号直接 push `main`，被拒绝
- 手动触发 `Sync Upstream` workflow，可以成功更新 `main`
