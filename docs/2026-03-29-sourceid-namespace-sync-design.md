# SourceID Namespace 自动补齐设计

> Date: 2026-03-29
> Status: Draft
> Scope: 基于已接入的 SourceID OAuth 登录链路，为 SkillHub 增加登录后 namespace 成员自动补齐能力，并规划后续按真实组织团队同步的演进路径

## 1. 背景

当前 SkillHub 已接入企业 SourceID 单点登录，但 namespace 成员管理仍依赖管理员手工逐个添加。对于按企业团队批量接入的场景，现有模式存在以下问题：

1. namespace 创建后无法按企业身份自动补齐成员
2. 管理员需要逐个维护 team 成员，成本高且容易遗漏
3. 企业 SSO 已提供用户身份上下文，但尚未被用于业务侧 team 同步

本设计的目标是在不重做认证主链路、不新增数据库表的前提下，先交付一版最小侵入且可逐步演进的自动补齐能力。

## 2. 当前上下文

### 2.1 已有基础能力

当前系统已经具备以下能力：

1. SourceID 已作为标准 OAuth2 provider 接入
2. OAuth 登录成功后会进入统一身份绑定流程
3. 身份绑定完成后可拿到 `PlatformPrincipal`
4. namespace 成员模型已支持 `MEMBER` / `ADMIN` / `OWNER`
5. 系统已存在统一的 namespace 成员仓储与查询能力

因此，最小侵入切点不是前端批量操作，也不是额外的管理后台接口，而是：

1. 在 OAuth 登录完成且身份绑定成功后
2. 根据 SourceID profile 字段命中配置规则
3. 对目标 namespace 做“只增不删”的成员补齐

### 2.2 当前真实 SourceID profile 样本

当前环境拿到的真实 SourceID profile 具备以下特征：

1. 根字段存在 `active=true`
2. `attributes` 中包含：
   - `GH`
   - `RJGH`
   - `POSITION`
   - `WORKINGLOCATION`
   - `XM`
   - `RJEMAIL`
3. 当前样本中没有发现稳定的部门或团队编码字段，例如：
   - `DWM`
   - `DWH`
   - `deptCode`
   - `organizeCode`

这意味着：

1. 当前阶段可直接使用 `active` 作为在任判断字段
2. 当前阶段不适合把 `WORKINGLOCATION`、`POSITION` 直接等价为企业 team
3. 如果要做稳定的 team 自动入组，后续需要：
   - SourceID profile 暴露组织字段，或
   - 接入 SourceID 组织 / 三元组接口补足组织关系

### 2.3 OSDS 组织关系接口补充

结合 OSDS 接口文档，当前已经确认 OSDS 可以提供足够的组织关系数据来支撑真实 team 映射，至少包括：

1. 根据 `userId` 获取员工信息
2. 员工信息中直接返回：
   - `departmentCode`
   - `departmentName`
   - `oneDepartmentCode` 到 `tenDepartmentCode`
   - `postCode`
   - `postName`
   - `organizationTypeCode`
   - `staffStatus`
3. 获取部门详情
4. 获取子级部门
5. 获取部门下员工
6. 获取部门及其子部门下全部员工

这意味着：

1. 登录后单用户自动补齐可以直接按 `claims.subject -> OSDS userId` 查询实现
2. 按部门树映射 namespace 不一定需要登录时遍历部门树，只需利用 `oneDepartmentCode` 到 `tenDepartmentCode` 做匹配
3. 若后续要做批量初始化或定时对账，OSDS 也已提供部门下员工与子树员工接口支撑

## 3. 设计目标

### 3.1 本阶段目标

本阶段只做登录后自动补齐，目标如下：

1. 用户通过 SourceID 登录成功后，按配置自动加入目标 namespace
2. 支持按单个 profile 字段匹配
3. 支持一个 namespace 匹配多个来源值
4. 支持一个来源值映射多个 namespace
5. 保持系统对现有 OAuth 主链路、数据库模型、前端登录流程的兼容

### 3.2 非目标

本阶段明确不做：

1. 不做全量组织同步
2. 不做离职 / 转岗后的自动移除
3. 不做登录外的定时对账任务
4. 不做新的 team 绑定表、外部组织镜像表或组织缓存表
5. 不做前端配置界面或管理后台映射维护页

## 4. 总体方案

## 4.1 核心思路

在 OAuth 登录流程中，身份绑定成功后触发 SourceID namespace 自动补齐。

流程如下：

1. 上游 OAuth2 provider 返回 SourceID 用户信息
2. 系统完成 access policy 判定
3. 系统完成 `bindOrCreate`
4. 进入 SourceID namespace 自动补齐服务
5. 可选调用 OSDS，根据 `claims.subject` 加载用户组织属性
6. 合并 SourceID profile 与 OSDS 组织属性
7. 按配置匹配 namespace
8. 对命中的 namespace 做增量补齐

当前落地入口位于：

- [server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/OAuthLoginFlowService.java](server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/OAuthLoginFlowService.java)

## 4.2 配置模型

配置模型支持以下能力：

1. 总开关 `enabled`
2. provider 限定，默认 `sourceid`
3. 嵌套属性根键，默认 `attributes`
4. 在任状态字段与可接受状态值列表
5. 多条 mappings
6. 可选的 OSDS 组织关系增强配置

每条 mapping 支持：

1. `attributeKey`
2. `attributeValue`
3. `attributeValues`
4. `namespaceSlug`
5. `role`

匹配语义：

1. 若 `attributeValues` 存在，则任一值命中即匹配
2. 若仅配置 `attributeValue`，则按单值兼容匹配
3. 同一来源值可通过多条 mapping 映射到多个 namespace
4. 同一 namespace 可配置多个来源值

## 4.3 写入语义

当前版本采用“只增不删”的写入策略：

1. OSDS 查询失败时默认 fail-open，跳过组织增强但不影响登录主链路
2. namespace 不存在则跳过
3. namespace 非 ACTIVE 则跳过
4. 用户已存在成员关系则跳过
5. 仅在不存在成员关系时新增 `NamespaceMember`
6. 不覆盖已有角色
7. 不做降权

这是当前最稳妥的上线方式，因为它避免了：

1. 错误映射导致的大规模误删
2. 组织字段不稳定时的错误回收
3. 登录瞬时数据与历史人工治理数据之间的冲突

## 5. 当前已实现能力

本轮已经完成：

1. 登录成功后接入 SourceID namespace 自动补齐切点
2. 新增 `SourceIdNamespaceSyncProperties`
3. 新增 `SourceIdNamespaceMembershipSyncService`
4. 支持按单值字段匹配 namespace
5. 支持同一来源值映射多个 namespace
6. 支持同一 namespace 配置多个来源值
7. 保持旧 `attributeValue` 单值配置兼容
8. 新增单元测试覆盖主要匹配场景
9. 更新 K8s ConfigMap 示例
10. 根据真实样本将在任字段示例调整为根字段 `active`
11. 增加可选的 OSDS 组织关系 client 与配置模型
12. 支持登录后使用 `claims.subject` 调用 OSDS 用户接口
13. 支持把 OSDS 的 `departmentCode` / 部门链 / 岗位 / 状态字段并入现有映射上下文
14. 默认以 fail-open 方式处理 OSDS 查询失败

## 6. 当前限制与风险

### 6.1 组织字段不足

当前真实 SourceID profile 样本未包含稳定的团队 / 部门编码，但 OSDS 已可提供组织字段，因此：

1. 现在更推荐优先使用 OSDS 的 `departmentCode` 与部门链字段做映射
2. 暂不建议把 `WORKINGLOCATION`、`POSITION` 直接当作企业 team 使用
3. 需要确认 SourceID `claims.subject` 与 OSDS `userId` 的稳定对应关系
4. 需要确认 OSDS 的 `sysid` 与 `sign-server-auth` 调用约束

### 6.2 只增不删

当前不会处理以下情况：

1. 用户离开原 team 后自动移除 namespace 成员关系
2. 用户从 ADMIN 自动降级为 MEMBER
3. 人工添加成员与外部映射规则之间的冲突治理

### 6.3 配置维护成本

当前 mappings 基于配置文件维护，适合：

1. 初期验证
2. 少量 team 映射
3. 稳定规则的生产落地

不适合：

1. 大规模组织树映射
2. 高频变更的团队关系
3. 非技术管理员自助维护

## 7. 推荐演进路径

推荐分三个阶段推进：

1. Phase 1：登录时按 profile 字段或少量稳定字段做 namespace 自动补齐
2. Phase 2：登录时接入 OSDS 用户组织信息，支持按真实 team / dept 关系补齐
3. Phase 3：增加可观测性、治理能力与后台配置界面

### 7.1 Phase 2 重点

若后续进入 Phase 2，建议增加：

1. OSDS 组织关系客户端
2. 用户组织关系查询与解析服务
3. team / dept 到 namespace 的稳定映射模型
4. 可选的缓存与对账机制

与此前相比，Phase 2 现在已具备接口前提，不再只是探索项，而是可以进入具体实现设计的下一阶段。

## 8. 相关文档

1. [docs/03-authentication-design.md](docs/03-authentication-design.md)
2. [docs/11-auth-extensibility-and-private-sso.md](docs/11-auth-extensibility-and-private-sso.md)
3. [docs/12-private-sso-integration-playbook.md](docs/12-private-sso-integration-playbook.md)
4. [docs/2026-03-29-sourceid-namespace-sync-implementation-plan.md](docs/2026-03-29-sourceid-namespace-sync-implementation-plan.md)
5. [docs/2026-03-29-sourceid-namespace-sync-phase1-checklist.md](docs/2026-03-29-sourceid-namespace-sync-phase1-checklist.md)