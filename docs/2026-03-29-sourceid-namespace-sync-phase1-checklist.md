# SourceID Namespace 自动补齐 Phase 1 Checklist

> Date: 2026-03-29
> Status: Draft
> Goal: 完成 SourceID 登录后 namespace 自动补齐能力的测试环境验证、生产配置收敛与上线准备

## 1. 范围定义

Phase 1 聚焦当前已经落地的登录后自动补齐能力，不进入组织 / 三元组接口方案，不引入定时同步，不处理自动移除。

本阶段验收目标：

1. 测试环境完成登录补齐链路验证
2. 明确生产映射字段来源
3. 产出可上线的 ConfigMap 配置稿
4. 明确灰度和回滚方案

## 2. 配置准备 Checklist

- [ ] 确认 `SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_ENABLED` 在目标环境可配置
- [ ] 确认是否启用 `SKILLHUB_AUTH_SOURCEID_OSDS_ENABLED`
- [ ] 若启用 OSDS，确认 `SKILLHUB_AUTH_SOURCEID_OSDS_BASE_URL`、`SYSID`、`SIGN_SERVER_AUTH` 已准备完成
- [ ] 确认 `SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_ACTIVE_STATUS_KEY` 使用 `active`
- [ ] 确认 `SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_ACTIVE_STATUS_VALUES_0` 使用 `true`
- [ ] 确认待映射的 `namespaceSlug` 已存在且状态为 ACTIVE
- [ ] 确认目标 role 仅使用 `MEMBER` 或 `ADMIN`
- [ ] 确认未把 `OWNER` 作为外部自动映射角色

## 3. 测试环境验证 Checklist

### 3.1 成功路径

- [ ] 准备一个 SourceID 测试账号
- [ ] 准备一个测试 namespace
- [ ] 配置一条明确可命中的 mapping 规则
- [ ] 若启用 OSDS，验证 `claims.subject -> OSDS userId` 查询成功
- [ ] 用户首次登录后成功加入目标 namespace
- [ ] 用户再次登录时不会重复创建成员记录

### 3.2 失败 / 边界路径

- [ ] 配置不命中时，用户不会被加入 namespace
- [ ] OSDS 查询失败时，不会影响 SourceID 登录主链路
- [ ] namespace 不存在时，系统不会中断登录主链路
- [ ] namespace 非 ACTIVE 时，系统不会写入成员关系
- [ ] 用户已是成员时，不会覆盖已有角色
- [ ] 用户状态不满足 `active=true` 时，不执行补齐

### 3.3 验证证据

- [ ] 保留测试环境配置快照
- [ ] 保留一次成功登录的服务日志证据
- [ ] 保留一次不命中规则的服务日志证据
- [ ] 保留测试后 namespace 成员结果截图或查询结果

## 4. 生产字段确认 Checklist

- [ ] 与 SourceID 对接方确认 profile 是否可返回稳定团队 / 部门字段
- [ ] 与 OSDS 对接方确认 `departmentCode`、部门链、`postCode`、`staffStatus`、`isEnable` 的业务语义
- [ ] 明确 `WORKINGLOCATION`、`POSITION` 是否仅可用于临时验证，不能直接作为团队字段上线

## 5. 生产上线准备 Checklist

- [ ] 输出生产 mappings 清单
- [ ] 审核每条 mapping 对应的 namespaceSlug
- [ ] 审核每条 mapping 对应的 role
- [ ] 确认是否存在一个来源值映射多个 namespace 的规则
- [ ] 确认是否存在一个 namespace 匹配多个来源值的规则
- [ ] 完成灰度发布步骤说明
- [ ] 完成回滚步骤说明

## 6. 回滚 Checklist

- [ ] 回滚手段明确为关闭 `SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_ENABLED`
- [ ] 明确关闭开关后不会影响现有 SourceID 登录主链路
- [ ] 明确是否需要人工清理灰度期间新增的 namespace 成员

## 7. 本阶段完成标准

Phase 1 可以视为完成，当且仅当以下条件同时满足：

1. 测试环境成功验证登录补齐链路
2. 生产环境字段来源已确认
3. 可上线配置稿已经过 review
4. 灰度和回滚方案明确

## 8. 相关文档

1. [docs/2026-03-29-sourceid-namespace-sync-design.md](docs/2026-03-29-sourceid-namespace-sync-design.md)
2. [docs/2026-03-29-sourceid-namespace-sync-implementation-plan.md](docs/2026-03-29-sourceid-namespace-sync-implementation-plan.md)
3. [docs/11-auth-extensibility-and-private-sso.md](docs/11-auth-extensibility-and-private-sso.md)