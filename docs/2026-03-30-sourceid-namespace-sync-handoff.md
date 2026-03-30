# SourceID Namespace Sync 跨机器续做说明

> Date: 2026-03-30
> Status: Active
> Scope: 说明如何在另一台机器上恢复当前 SourceID + OSDS namespace 自动补齐工作流，并继续后续开发与验证

## 1. 当前进度快照

当前工作已经保存到远端分支：

1. 分支：`feature/sourceid-namespace-sync`
2. 工作区状态：干净，无未提交改动
3. 远端分支：`origin/feature/sourceid-namespace-sync`

最近关键提交如下：

1. `914525b` `feat(auth): add osds org sync for sourceid`
2. `559d486` `docs(auth): add sourceid namespace sync docs`
3. `418e734` `feat(auth): add sourceid namespace sync`

这意味着另一台机器不需要手工拷贝任何代码目录，只需拉取并切换到对应分支即可恢复当前开发基线。

## 2. 恢复步骤

在另一台机器上执行：

```bash
git clone git@github.com:yespon/skillhub.git
cd skillhub
git fetch origin
git checkout feature/sourceid-namespace-sync
git pull
```

如果仓库已经存在本地，只需要：

```bash
cd skillhub
git fetch origin
git checkout feature/sourceid-namespace-sync
git pull
```

执行完成后，可通过以下命令确认恢复成功：

```bash
git branch --show-current
git --no-pager log --oneline -3
git --no-pager status --short
```

期望结果：

1. 当前分支为 `feature/sourceid-namespace-sync`
2. 最新提交包含 `914525b`
3. `git status --short` 无输出

## 3. 需要优先阅读的文档

恢复后，建议按以下顺序阅读：

1. 方案设计：[docs/2026-03-29-sourceid-namespace-sync-design.md](docs/2026-03-29-sourceid-namespace-sync-design.md)
2. 实施计划：[docs/2026-03-29-sourceid-namespace-sync-implementation-plan.md](docs/2026-03-29-sourceid-namespace-sync-implementation-plan.md)
3. Phase 1 清单：[docs/2026-03-29-sourceid-namespace-sync-phase1-checklist.md](docs/2026-03-29-sourceid-namespace-sync-phase1-checklist.md)

这三份文档已经覆盖：

1. 当前方案边界
2. 已完成能力
3. 当前待验证事项
4. 下一阶段实现方向

## 4. 当前代码状态

当前实现已经包括：

1. SourceID 登录后 namespace 自动补齐
2. 基于配置的单值 / 多值 / 多 namespace 映射
3. OSDS 组织关系增强入口
4. 登录后使用 `claims.subject` 查询 OSDS 用户组织信息
5. 将 OSDS 的 `departmentCode` / 部门链 / `postCode` / `staffStatus` / `isEnable` 并入映射上下文
6. OSDS 查询默认 fail-open，不影响主登录链路

关键实现文件：

1. [server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/OAuthLoginFlowService.java](server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/OAuthLoginFlowService.java)
2. [server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/sourceid/SourceIdNamespaceMembershipSyncService.java](server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/sourceid/SourceIdNamespaceMembershipSyncService.java)
3. [server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/sourceid/SourceIdOsdsOrganizationClient.java](server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/sourceid/SourceIdOsdsOrganizationClient.java)
4. [server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/sourceid/SourceIdOsdsProperties.java](server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/sourceid/SourceIdOsdsProperties.java)
5. [deploy/k8s/01-configmap.yml](deploy/k8s/01-configmap.yml)

## 5. 如何继续验证

当前最值得继续推进的是环境验证，而不是继续扩展功能。

建议优先做以下验证：

1. 验证 SourceID `claims.subject` 是否稳定对应 OSDS `userId`
2. 确认 OSDS 的 `sysid` 与 `sign-server-auth` 的真实调用规则
3. 确认 `staffStatus` 与 `isEnable` 的业务语义
4. 选一个测试 namespace 做测试环境链路验证

当前推荐使用的定向测试命令：

```bash
cd server && JDK_JAVA_OPTIONS="-XX:+EnableDynamicAgentLoading" ./mvnw -pl skillhub-auth -am test -Dtest=OAuthLoginFlowServiceTest,OAuth2LoginHandlersTest,OAuth2AuthorizationRequestResolverTest,SourceIdNamespaceMembershipSyncServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

## 6. 需要额外保存的非仓库上下文

以下信息不一定都在仓库内，需要在换机器时额外确认：

1. OSDS 原始接口文档来源
2. 测试环境可用的 SourceID 账号
3. OSDS 网关 / 服务地址
4. `sysid` 与 `sign-server-auth` 的生成方式
5. 目标环境的 namespace slug 列表

说明：

当前关于 OSDS 的关键结论已经沉淀进仓库文档，但原始 OSDS 文档文件本身如果只存在于本地下载目录，则不会随 git 一起同步到另一台机器。

## 7. 下一步建议

恢复到另一台机器后，建议按以下顺序继续：

1. 拉取分支并确认工作区干净
2. 阅读设计、实施计划和 checklist
3. 准备 OSDS 环境参数
4. 验证 SourceID subject 与 OSDS userId 映射
5. 在测试环境开启 namespace sync 与 OSDS 增强
6. 完成一条成功路径和一条失败路径验证

## 8. 相关文档

1. [docs/2026-03-29-sourceid-namespace-sync-design.md](docs/2026-03-29-sourceid-namespace-sync-design.md)
2. [docs/2026-03-29-sourceid-namespace-sync-implementation-plan.md](docs/2026-03-29-sourceid-namespace-sync-implementation-plan.md)
3. [docs/2026-03-29-sourceid-namespace-sync-phase1-checklist.md](docs/2026-03-29-sourceid-namespace-sync-phase1-checklist.md)