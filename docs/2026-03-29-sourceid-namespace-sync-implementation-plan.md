# SourceID Namespace 自动补齐实施计划

> Date: 2026-03-29
> Status: In Progress
> Goal: 为 SourceID 登录用户提供登录后 namespace 成员自动补齐能力，并形成后续组织同步的阶段化实施路线

## 1. 范围定义

本计划分为已完成能力、近期待落实能力和中长期演进能力三部分。

当前阶段只覆盖：

1. 登录时自动补齐
2. 配置驱动的字段映射
3. 只增不删的成员写入策略
4. 可选的 OSDS 组织属性增强

当前阶段不覆盖：

1. 组织接口拉取
2. 定时全量同步
3. 自动移除与角色回收
4. 后台可视化配置

## 2. 已完成能力

- [x] 在 OAuth 登录成功且身份绑定完成后接入 namespace 自动补齐切点
- [x] 新增 SourceID namespace-sync 配置模型
- [x] 支持 `activeStatusKey` 与 `activeStatusValues`
- [x] 支持单值字段匹配 `attributeValue`
- [x] 支持多值字段匹配 `attributeValues`
- [x] 支持一个来源值映射多个 namespace
- [x] 支持一个 namespace 匹配多个来源值
- [x] 保持旧单值配置兼容
- [x] 补充 SourceID namespace sync 单元测试
- [x] 更新 `deploy/k8s/01-configmap.yml` 配置示例
- [x] 根据真实 SourceID profile 样本调整在任判定示例为 `active=true`
- [x] 创建 feature 分支并完成首轮提交、推送远端
- [x] 增加 OSDS 配置模型与组织关系查询 client
- [x] 支持登录后基于 `claims.subject` 查询 OSDS 用户组织属性
- [x] 支持把 OSDS 的 `departmentCode` / 部门链 / `postCode` / `staffStatus` / `isEnable` 并入映射上下文
- [x] 默认以 fail-open 方式处理 OSDS 查询失败

## 3. 当前待落地能力

### 3.1 Phase 1.1：测试环境验证

- [ ] 在测试环境开启 `SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_ENABLED`
- [ ] 选取测试 namespace，基于一个稳定字段完成链路验证
- [ ] 验证登录成功后自动补齐行为是否符合预期
- [ ] 验证已有成员不会被覆盖或重复写入

交付标准：

1. 至少完成一条成功样例
2. 至少完成一条不命中规则样例
3. 至少完成一条已存在成员样例

### 3.2 Phase 1.2：真实映射字段确认

- [ ] 验证 SourceID `claims.subject` 是否稳定对应 OSDS `userId`
- [ ] 与 OSDS / 网关对接方确认 `sysid` 与 `sign-server-auth` 的调用约束
- [ ] 确认 `staffStatus` 与 `isEnable` 的业务语义
- [ ] 确认是否存在多部门 / 多岗位归属场景

交付标准：

1. 明确 SourceID 到 OSDS 的标识映射关系
2. 明确生产映射字段优先使用 `departmentCode` / 部门链 / `postCode`

### 3.3 Phase 1.3：生产配置收敛

- [ ] 输出生产环境 mappings 清单
- [ ] 审核 namespace slug 与目标角色是否正确
- [ ] 完成灰度发布方案
- [ ] 制定回滚方案

交付标准：

1. 形成可直接应用的 ConfigMap 配置稿
2. 形成灰度与回滚步骤说明

## 4. 后续待实现能力

### 4.1 Phase 2：基于 OSDS 真实组织关系的 team 同步

- [ ] 扩展 OSDS 组织关系解析服务
- [ ] 定义 `departmentCode` / 部门链 到 namespace 的映射模型
- [ ] 支持根据真实组织关系补齐 namespace 成员
- [ ] 评估是否需要缓存组织关系查询结果
- [ ] 评估是否需要部门树初始化导入能力

建议原则：

1. 仍尽量复用现有登录切点
2. 新增能力优先封装在 provider / sourceid 层
3. 不直接把外部组织模型渗透到通用 domain 层
4. 登录链路中的 OSDS 查询默认保持 fail-open

### 4.2 Phase 3：治理与可观测性增强

- [ ] 增加同步结果日志与审计字段
- [ ] 增加同步失败原因分类
- [ ] 增加配置校验与启动期告警
- [ ] 增加后台维护界面或导入式映射维护能力
- [ ] 评估是否需要定时全量对账

## 5. 风险清单

### 5.1 字段语义风险

- [ ] 避免把 `WORKINGLOCATION`、`POSITION` 等非团队字段直接当作稳定 team 字段上线

### 5.2 数据治理风险

- [ ] 当前为只增不删，生产启用前需要确认这符合业务预期
- [ ] 若人工治理与自动补齐并存，需要确认优先级策略

### 5.3 组织接口依赖风险

- [ ] 若进入 Phase 2，需要确认组织 / 三元组接口的可用性、权限与稳定性

## 6. 推荐执行顺序

1. 先完成测试环境链路验证
2. 再确认 SourceID 到 OSDS 的标识映射关系
3. 再基于 OSDS 的 `departmentCode` / 部门链收敛生产 mappings
4. 若 OSDS 语义确认完备，再评估部门树初始化与定时对账

## 7. 相关文档

1. [docs/2026-03-29-sourceid-namespace-sync-design.md](docs/2026-03-29-sourceid-namespace-sync-design.md)
2. [docs/03-authentication-design.md](docs/03-authentication-design.md)
3. [docs/11-auth-extensibility-and-private-sso.md](docs/11-auth-extensibility-and-private-sso.md)
4. [docs/12-private-sso-integration-playbook.md](docs/12-private-sso-integration-playbook.md)
5. [docs/2026-03-29-sourceid-namespace-sync-phase1-checklist.md](docs/2026-03-29-sourceid-namespace-sync-phase1-checklist.md)
6. [docs/2026-03-30-sourceid-namespace-sync-handoff.md](docs/2026-03-30-sourceid-namespace-sync-handoff.md)