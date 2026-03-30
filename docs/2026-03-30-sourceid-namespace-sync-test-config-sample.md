# SourceID Namespace Sync 测试环境最小联调样例

> Date: 2026-03-30
> Status: Active
> Scope: 提供当前 dev 配置模型下可直接落地的 SourceID namespace sync / OSDS 最小联调样例

## 1. 使用原则

测试环境联调时，建议遵循以下原则：

1. 一次只开一条 mapping，避免多条规则同时命中
2. 优先使用 `departmentCode -> namespaceSlug` 的一对一映射
3. 先验证成功路径，再验证失败路径
4. 只有在确认 SourceID profile 本身带稳定组织字段时，才用纯 profile 样例

## 2. 推荐样例：SourceID + OSDS

这是当前最推荐的测试环境最小样例。
适用前提：

1. SourceID 登录已可用
2. OSDS `/staff/user-id/{userId}/data` 已可访问
3. 已确认 SourceID `claims.subject` 可映射到 OSDS `userId`
4. 目标 namespace 已存在，例如 `team-network-test`

Compose / `.env.release` 样例：

```dotenv
SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_ENABLED=true
SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_ACTIVE_STATUS_KEY=active
SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_ACTIVE_STATUS_VALUES_0=true

SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_MAPPINGS_0_ATTRIBUTE_KEY=departmentCode
SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_MAPPINGS_0_ATTRIBUTE_VALUE=000023002
SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_MAPPINGS_0_NAMESPACE_SLUG=team-network-test
SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_MAPPINGS_0_ROLE=MEMBER

SKILLHUB_AUTH_SOURCEID_OSDS_ENABLED=true
SKILLHUB_AUTH_SOURCEID_OSDS_BASE_URL=https://osds.example.com
SKILLHUB_AUTH_SOURCEID_OSDS_STAFF_BY_USER_ID_PATH=/staff/user-id/{userId}/data
SKILLHUB_AUTH_SOURCEID_OSDS_SYSID=your-test-sysid
SKILLHUB_AUTH_SOURCEID_OSDS_SIGN_SERVER_AUTH=your-test-sign
SKILLHUB_AUTH_SOURCEID_OSDS_FAIL_OPEN=true
```

Kubernetes ConfigMap 样例：

```yaml
skillhub-auth-sourceid-namespace-sync-enabled: "true"
skillhub-auth-sourceid-namespace-sync-active-status-key: active
skillhub-auth-sourceid-namespace-sync-active-status-values-0: "true"

skillhub-auth-sourceid-namespace-sync-mappings-0-attribute-key: departmentCode
skillhub-auth-sourceid-namespace-sync-mappings-0-attribute-value: "000023002"
skillhub-auth-sourceid-namespace-sync-mappings-0-namespace-slug: team-network-test
skillhub-auth-sourceid-namespace-sync-mappings-0-role: MEMBER

skillhub-auth-sourceid-osds-enabled: "true"
skillhub-auth-sourceid-osds-base-url: "https://osds.example.com"
skillhub-auth-sourceid-osds-staff-by-user-id-path: /staff/user-id/{userId}/data
skillhub-auth-sourceid-osds-sysid: your-test-sysid
skillhub-auth-sourceid-osds-sign-server-auth: your-test-sign
skillhub-auth-sourceid-osds-fail-open: "true"
```

字段替换说明：

1. `000023002` 替换为测试账号在 OSDS 中真实可命中的 `departmentCode`
2. `team-network-test` 替换为测试环境已创建的 namespace slug
3. `https://osds.example.com` 替换为真实 OSDS 地址
4. `your-test-sysid` 与 `your-test-sign` 替换为测试环境可用值

期望结果：

1. 用户首次 SourceID 登录后被加入 `team-network-test`
2. 用户再次登录时不重复创建成员关系
3. 若 OSDS 临时不可用，用户仍可登录，只是不触发自动补齐

## 3. 备用样例：仅 SourceID Profile

只有在 SourceID profile 本身提供稳定组织字段时，才建议使用这个样例。

Compose / `.env.release` 样例：

```dotenv
SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_ENABLED=true
SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_ACTIVE_STATUS_KEY=active
SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_ACTIVE_STATUS_VALUES_0=true

SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_MAPPINGS_0_ATTRIBUTE_KEY=WORKINGLOCATION
SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_MAPPINGS_0_ATTRIBUTE_VALUE=合肥
SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_MAPPINGS_0_NAMESPACE_SLUG=team-location-hefei-test
SKILLHUB_AUTH_SOURCEID_NAMESPACE_SYNC_MAPPINGS_0_ROLE=MEMBER

SKILLHUB_AUTH_SOURCEID_OSDS_ENABLED=false
SKILLHUB_AUTH_SOURCEID_OSDS_BASE_URL=
SKILLHUB_AUTH_SOURCEID_OSDS_STAFF_BY_USER_ID_PATH=/staff/user-id/{userId}/data
SKILLHUB_AUTH_SOURCEID_OSDS_SYSID=
SKILLHUB_AUTH_SOURCEID_OSDS_SIGN_SERVER_AUTH=
SKILLHUB_AUTH_SOURCEID_OSDS_FAIL_OPEN=true
```

典型用途：

1. 快速回归 namespace sync 主链路
2. OSDS 尚未联通，但需要先验证配置与写库行为

风险说明：

1. `WORKINGLOCATION`、`POSITION` 更适合临时联调，不建议直接作为生产团队字段上线
2. 这类样例只能证明映射机制本身可用，不能替代真实组织关系联调

## 4. 联调顺序

1. 先保留一条 `departmentCode -> namespaceSlug` 规则验证成功路径
2. 再把 `ATTRIBUTE_VALUE` 改成一个确定不命中的值，验证失败路径
3. 再把 OSDS 地址改错，验证 `FAIL_OPEN=true` 的登录容错
4. 最后再扩展到多个 mapping 或多值匹配

## 5. 相关文件

1. `deploy/k8s/01-configmap.yml`
2. `deploy/k8s/overlays/external-sourceid-only/configmap-sourceid-patch.yaml`
3. `deploy/k8s/overlays/external-s3-sourceid-only/configmap-sourceid-patch.yaml`
4. `docs/09-deployment.md`

## 5. 一次只验证一条规则

测试环境首次联调，建议只保留一条 mapping，避免多个来源值或多个 namespace 同时干扰判断。
