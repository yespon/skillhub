# SourceID Namespace 自动补齐 Phase 1 Checklist

> Date: 2026-03-29
> Status: Draft
> Goal: 完成 SourceID 登录后 namespace 自动补齐能力的测试环境验证、OSDS 组织增强联调、生产配置收敛与上线准备

## 1. 范围定义

Phase 1 聚焦当前已经落地的登录后自动补齐能力，包括可选的 OSDS 组织增强能力，不引入定时同步，不处理自动移除。

本阶段验收目标：

1. 测试环境完成登录补齐链路验证
2. 明确 SourceID subject 与 OSDS userId 的映射关系
3. 明确生产映射字段来源
4. 产出可上线的 ConfigMap 配置稿
5. 明确灰度和回滚方案

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

建议按“先静态配置校验，再成功路径，再失败路径，再留存证据”的顺序执行，避免定位问题时混淆是配置错误还是联调错误。

### 3.0 预检步骤

- [ ] 在目标环境确认 `sourceid` OAuth 登录本身已可用
- [ ] 在目标环境确认待映射 namespace 已提前创建完成
- [ ] 在 ConfigMap 中只保留一条最小化测试 mapping，避免多条规则同时命中影响判断
- [ ] 若启用 OSDS，先用对接方提供的用户样本确认该用户在 OSDS 中可以查到 `departmentCode`
- [ ] 记录本次测试使用的 SourceID 账号、预期 namespaceSlug、预期 role、预期来源字段值

### 3.1 成功路径

- [ ] 准备一个 SourceID 测试账号
- [ ] 准备一个测试 namespace
- [ ] 配置一条明确可命中的 mapping 规则
- [ ] 若启用 OSDS，验证 `claims.subject -> OSDS userId` 查询成功
- [ ] 用户首次登录后成功加入目标 namespace
- [ ] 用户再次登录时不会重复创建成员记录

建议执行步骤：

1. 在 ConfigMap 中仅开启一条测试规则，优先使用 `departmentCode -> namespaceSlug` 的一对一映射
2. 重启对应服务实例，确认新配置已生效
3. 使用测试账号完成一次完整 SourceID 登录
4. 在服务日志中检索 SourceID 登录、OSDS 查询、namespace 补齐相关日志
5. 在数据库或管理界面确认该用户已进入目标 namespace，且角色符合预期
6. 退出后再次登录同一账号，确认不会重复创建成员关系

建议核对点：

1. 若启用 OSDS，确认查询参数使用的是 SourceID `subject`，而不是 SkillHub 内部 `usr_*` 用户 id
2. 若使用 `active=true` 作为准入条件，确认 profile 或合并后的属性中确实存在该值
3. 成功路径只验证一条规则，避免一次联调覆盖过多变量

### 3.2 失败 / 边界路径

- [ ] 配置不命中时，用户不会被加入 namespace
- [ ] OSDS 查询失败时，不会影响 SourceID 登录主链路
- [ ] namespace 不存在时，系统不会中断登录主链路
- [ ] namespace 非 ACTIVE 时，系统不会写入成员关系
- [ ] 用户已是成员时，不会覆盖已有角色
- [ ] 用户状态不满足 `active=true` 时，不执行补齐

建议执行步骤：

1. 把 mapping 的来源值改成一个确定不会命中的值，再次登录验证“不加入但可登录”
2. 临时把 OSDS 地址改为错误地址，或让对接方提供一个可复现失败的联调环境，验证 fail-open
3. 把 mapping 指向一个不存在的 namespaceSlug，验证“不补齐但可登录”
4. 把 mapping 指向一个非 ACTIVE namespace，验证系统跳过写入
5. 先人工把用户加入目标 namespace 并赋予较高角色，再登录验证不会被自动覆盖
6. 将测试账号切换为不满足 `active=true` 的状态样本，验证不会触发补齐

建议核对点：

1. 所有失败路径都必须验证“登录主链路成功”与“自动补齐未发生”这两个结果
2. OSDS 异常场景要重点确认仅记录告警，不抛出阻断登录的异常
3. 角色已存在场景要重点确认没有发生降权或重复插入

### 3.3 验证证据

- [ ] 保留测试环境配置快照
- [ ] 保留一次成功登录的服务日志证据
- [ ] 保留一次不命中规则的服务日志证据
- [ ] 保留测试后 namespace 成员结果截图或查询结果

建议至少保留以下材料：

1. 触发成功路径时的 ConfigMap 片段
2. 一次成功登录的用户标识、时间点、目标 namespaceSlug
3. 一次 OSDS 查询失败但登录成功的日志片段
4. 一次 namespace 成员结果截图，证明成员已补齐或未补齐

## 4. 生产字段确认 Checklist

- [ ] 与 SourceID 对接方确认 profile 是否可返回稳定团队 / 部门字段
- [ ] 与 OSDS 对接方确认 `departmentCode`、部门链、`postCode`、`staffStatus`、`isEnable` 的业务语义
- [ ] 与 OSDS 对接方确认 `claims.subject` 是否稳定对应 OSDS `userId`
- [ ] 与 OSDS 对接方确认 `sign-server-auth` 是静态透传、固定密钥还是动态签名
- [ ] 明确 `WORKINGLOCATION`、`POSITION` 是否仅可用于临时验证，不能直接作为团队字段上线

推荐结论：

1. 能用 `departmentCode` 就不要直接用 `WORKINGLOCATION` 或 `POSITION` 上线
2. 若 `sign-server-auth` 不是静态值，需要在下一阶段补动态签名实现后再生产启用 OSDS

## 5. 生产上线准备 Checklist

- [ ] 输出生产 mappings 清单
- [ ] 审核每条 mapping 对应的 namespaceSlug
- [ ] 审核每条 mapping 对应的 role
- [ ] 确认是否存在一个来源值映射多个 namespace 的规则
- [ ] 确认是否存在一个 namespace 匹配多个来源值的规则
- [ ] 完成灰度发布步骤说明
- [ ] 完成回滚步骤说明

建议上线顺序：

1. 先在测试环境只验证一个部门映射一个 namespace
2. 再扩展到多个部门映射
3. 最后再开启一个来源值映射多个 namespace 的规则
4. 首次生产灰度时，优先只给少量测试账号覆盖的部门开启规则

## 6. 回滚 Checklist

- [ ] 回滚手段明确为关闭 `SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_ENABLED`
- [ ] 明确关闭开关后不会影响现有 SourceID 登录主链路
- [ ] 明确是否需要人工清理灰度期间新增的 namespace 成员

建议补充操作：

1. 回滚时先关自动补齐开关，再评估是否需要保留已补齐成员
2. 若本次灰度只覆盖测试 namespace，优先人工清理测试数据，避免影响正式空间

## 7. 本阶段完成标准

Phase 1 可以视为完成，当且仅当以下条件同时满足：

1. 测试环境成功验证登录补齐链路
2. SourceID subject 与 OSDS userId 映射关系已确认
3. 生产环境字段来源已确认
4. 可上线配置稿已经过 review
5. 灰度和回滚方案明确

## 8. 相关文档

1. [docs/2026-03-29-sourceid-namespace-sync-design.md](docs/2026-03-29-sourceid-namespace-sync-design.md)
2. [docs/2026-03-29-sourceid-namespace-sync-implementation-plan.md](docs/2026-03-29-sourceid-namespace-sync-implementation-plan.md)
3. [docs/11-auth-extensibility-and-private-sso.md](docs/11-auth-extensibility-and-private-sso.md)
4. [docs/2026-03-30-sourceid-namespace-sync-handoff.md](docs/2026-03-30-sourceid-namespace-sync-handoff.md)
5. [docs/2026-03-30-sourceid-namespace-sync-test-config-sample.md](docs/2026-03-30-sourceid-namespace-sync-test-config-sample.md)