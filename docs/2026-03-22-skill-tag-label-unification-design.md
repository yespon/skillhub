# Skill Tag / Label 统一设计

> Date: 2026-03-22
> Status: Draft
> Scope: Skill 归类、过滤、聚合能力统一到现有 label 架构

## 1. 背景

当前 SkillHub 已存在两套相近但语义不同的“标签”概念：

- `skill_tag`：版本别名，指向某个 skill version，用于分发通道，如 `latest`、`beta`
- `label_definition` / `skill_label`：skill 级分类标签，用于展示、筛选和治理

产品侧如果继续使用“给 skill 加 tag”这一表述，容易导致领域语义混淆：

- 对用户来说，tag 常常被理解为分类标签
- 对当前代码来说，tag 已经是版本别名，不是 skill 分类

如果在现有架构上再引入一套新的 skill tag 模型，会造成：

- 领域概念重复
- 搜索索引与过滤链路重复建设
- 权限模型重复建设
- 后续 Elasticsearch / OpenSearch 迁移成本增加

因此需要明确一条统一设计：

- 技术实现层继续使用现有 label 模型承载 skill 分类能力
- 产品展示层可以使用“标签”或“Tag”文案
- `skill_tag` 继续只承担版本别名职责，不参与 skill 分类

## 2. 设计目标

本设计解决以下问题：

1. 为 skill 提供稳定的分类能力
2. 支持搜索过滤与结果聚合
3. 支持后续运营分析维度扩展
4. 保持与当前仓库架构兼容，避免重做已有搜索与权限链路

## 3. 非目标

本设计当前不包含以下内容：

1. 修改现有 `skill_tag` 的语义或 API 路径
2. 引入自由输入、无限制的用户自定义标签
3. 一次性实现完整 BI 报表系统
4. 在本阶段切换到 Elasticsearch / OpenSearch

## 4. 现状分析

### 4.1 已有能力

当前代码库中，skill 分类标签相关能力已经部分落地：

1. 后端已有 `LabelDefinition`、`LabelTranslation`、`SkillLabel` 模型
2. 搜索接口已经支持 `label` query parameter
3. 搜索应用服务已经将 `labelSlugs` 下传到 `SearchQuery`
4. PostgreSQL 搜索实现已经通过 `skill_label` + `label_definition` 做过滤
5. 前端搜索页已经存在基于单个 label 的筛选交互

这说明现有 label 架构已经具备“skill 分类标签”的核心链路。

### 4.2 现有问题

尽管主链路已存在，当前实现仍有以下缺口：

1. 术语不统一：产品侧容易把 label 和 tag 混用
2. 搜索返回只有结果列表，没有 facet 聚合信息
3. 前端只支持单选 label，不支持多选和聚合展示
4. 标签定义维度仍偏简单，难以支撑展示、过滤、推荐、分析等多种用途
5. 运营分析与搜索过滤尚未分层设计

## 5. 核心决策

### 5.1 术语决策

统一采用以下术语：

- 版本别名：`tag`
- skill 分类标签：`label`
- 产品界面文案：允许显示为“标签”或“Tag”

这意味着：

- 数据表、实体、服务、控制器、DTO、搜索字段继续使用 `label`
- `skill_tag` 及 `/tags` 相关路径保持不变
- 前端页面文案可以对用户显示“标签”或“Tag”

### 5.2 模型决策

skill 的归类、过滤、聚合统一建立在现有 label 体系上，不新增第二套 skill tag 领域模型。

### 5.3 架构分层决策

将标签能力拆成三层：

1. 分类层：skill 绑定哪些 label
2. 搜索层：基于 label 做过滤与 facet 聚合
3. 分析层：基于 label 做下载、收藏、评分、趋势等统计

其中：

- 分类层和搜索层直接依赖现有 label 架构
- 分析层单独建设统计投影，不直接塞进搜索文档

## 6. 数据模型设计

### 6.1 保持现有主模型不变

继续使用以下表与实体：

1. `label_definition`
2. `label_translation`
3. `skill_label`

保留原则：

- label 挂在 skill 级别，不挂在 version 级别
- `skill_tag` 不参与 skill 分类

### 6.2 `label_definition` 增量扩展

当前 `label_definition` 仅包含：

- `slug`
- `type`
- `visible_in_filter`
- `sort_order`

为支撑后续过滤、展示、推荐和分析，建议新增以下字段。

#### 6.2.1 `status`

建议值：

- `ACTIVE`
- `DISABLED`
- `ARCHIVED`

语义：

- `ACTIVE`：可绑定、可展示、可参与过滤
- `DISABLED`：历史关系保留，但不允许新绑定，也不出现在公开筛选面板
- `ARCHIVED`：治理用途，不参与前台展示

#### 6.2.2 `usage_scope`

建议值：

- `DISCOVERY_FILTER`
- `DETAIL_BADGE`
- `RECOMMENDATION_SIGNAL`
- `ANALYTICS_DIMENSION`

语义：

- `DISCOVERY_FILTER`：允许出现在搜索与命名空间页筛选面板
- `DETAIL_BADGE`：允许在 skill 详情页展示 badge
- `RECOMMENDATION_SIGNAL`：允许参与排序加权或推荐模型
- `ANALYTICS_DIMENSION`：允许进入运营统计维度

注：一期可以将 `usage_scope` 实现为单值枚举，避免过早引入多值复杂度。

#### 6.2.3 `owner_type`（可选，预留）

建议值：

- `SYSTEM`
- `USER`

该字段用于未来支持用户自定义标签时做来源区分。本阶段可只在设计中预留，不强制首批落地。

### 6.3 `LabelType` 扩展建议

当前 `LabelType`：

- `RECOMMENDED`
- `PRIVILEGED`

建议保持当前实现不变；若未来开放用户自定义标签，再增加：

- `USER_DEFINED`

这项扩展不建议在当前阶段直接落地，避免提前引入审核、治理和垃圾标签控制复杂度。

### 6.4 `skill_label` 增量扩展

建议对关系表增加治理与审计字段：

#### 6.4.1 `source`

建议值：

- `MANUAL`
- `SYSTEM`
- `IMPORTED`
- `PROMOTED`

作用：

- 标识关联关系的来源
- 为后续批量导入、自动打标、promotion 复制策略提供审计基础

#### 6.4.2 `updated_at`

当前关系表只有创建时间，不利于后续清理、回放和审计。建议增加更新时间。

### 6.5 数量约束

保留当前“单个 skill 最多 10 个 label”的约束，同时建议新增业务规则：

1. 单个 skill 最多 3 个 `DISCOVERY_FILTER` 标签
2. 单个 skill 最多 2 个 `PRIVILEGED` 标签

目的是避免：

- 搜索面板噪音过大
- 特权标签泛化，降低治理信号价值

## 7. 搜索接口设计

### 7.1 请求参数设计

当前搜索接口已经支持 `label` 参数，但前端仍按单值使用。

建议正式扩展为：

- `q`：关键字
- `namespace`：空间过滤
- `label`：多值标签过滤参数，可重复传递
- `labelMode`：`any` 或 `all`
- `sort`：排序字段
- `page`
- `size`
- `includeFacets`：是否返回聚合信息，默认 `true`

示例：

```http
GET /api/web/skills?q=agent&label=rag&label=official&labelMode=all&sort=relevance&page=0&size=20
```

### 7.2 响应结构设计

当前搜索返回：

- `items`
- `total`
- `page`
- `size`

建议扩展为：

```json
{
  "items": [],
  "total": 128,
  "page": 0,
  "size": 20,
  "facets": {
    "labels": {
      "mode": "any",
      "items": [
        {
          "slug": "rag",
          "displayName": "RAG",
          "count": 28,
          "selected": true,
          "type": "RECOMMENDED",
          "usageScope": "DISCOVERY_FILTER"
        }
      ]
    }
  },
  "appliedFilters": {
    "labels": ["rag", "official"],
    "labelMode": "all",
    "namespace": "team-ai",
    "sort": "relevance"
  }
}
```

### 7.3 Facet 聚合语义

推荐一期采用 `narrow facets` 语义：

- facet count 基于当前全部过滤条件之后的结果集计算

优点：

- 语义直观
- 用户更容易理解
- SQL 实现复杂度较低

后续如果需要做“除 label 外其他条件固定”的宽聚合，可再单独扩展。

## 8. 搜索实现设计

### 8.1 过滤实现

当前基于 `skill_label` + `label_definition` 的过滤实现可以继续保留。

#### `labelMode=any`

语义：命中任意标签即可。

实现方式：

```sql
AND d.skill_id IN (
  SELECT sl.skill_id
  FROM skill_label sl
  JOIN label_definition ld ON ld.id = sl.label_id
  WHERE LOWER(ld.slug) IN (:labelSlugs)
)
```

#### `labelMode=all`

语义：必须同时具备所有标签。

实现方式：

```sql
AND d.skill_id IN (
  SELECT sl.skill_id
  FROM skill_label sl
  JOIN label_definition ld ON ld.id = sl.label_id
  WHERE LOWER(ld.slug) IN (:labelSlugs)
  GROUP BY sl.skill_id
  HAVING COUNT(DISTINCT LOWER(ld.slug)) = :labelCount
)
```

### 8.2 Facet 聚合实现

facet 聚合应当基于“当前结果集”而不是全库。

实现建议：

1. 先构造基础候选 skill 集
2. 在候选 skill 集上与 `skill_label`、`label_definition` 做聚合
3. 只返回 `DISCOVERY_FILTER` 且 `status=ACTIVE` 的标签

示意 SQL：

```sql
WITH matched_skills AS (
  -- 当前搜索条件命中的 skill_id 集合
)
SELECT
  ld.slug,
  COALESCE(lt_current.display_name, lt_en.display_name, ld.slug) AS display_name,
  ld.type,
  COUNT(DISTINCT ms.skill_id) AS skill_count
FROM matched_skills ms
JOIN skill_label sl ON sl.skill_id = ms.skill_id
JOIN label_definition ld ON ld.id = sl.label_id
LEFT JOIN label_translation lt_current
  ON lt_current.label_id = ld.id AND lt_current.locale = :locale
LEFT JOIN label_translation lt_en
  ON lt_en.label_id = ld.id AND lt_en.locale = 'en'
WHERE ld.status = 'ACTIVE'
  AND ld.usage_scope = 'DISCOVERY_FILTER'
GROUP BY ld.slug, display_name, ld.type, ld.sort_order
ORDER BY ld.sort_order ASC, ld.slug ASC
```

### 8.3 索引建议

为保证过滤与聚合性能，建议补充以下索引：

1. `skill_label(label_id, skill_id)`
2. `skill_label(skill_id, label_id)`
3. `label_definition(status, usage_scope, sort_order)`
4. `label_translation(label_id, locale)`

### 8.4 搜索文档冗余策略

一期不建议立即把 label 冗余写入 `skill_search_document`。

原因：

- 当前 PostgreSQL join 成本可控
- 现有实现已经可用
- 冗余字段会带来更多重建复杂度

但应在二期优化中预留：

- 将 `labelSlugs` 冗余为搜索文档字段
- 或在 ES / OpenSearch 中直接写入 keyword array

## 9. 前端交互设计

### 9.1 搜索页

当前搜索页使用单个 label 按钮做互斥切换。建议升级为 facet 面板。

交互目标：

1. 展示标签名称与计数
2. 支持多选
3. 支持 `any` / `all` 模式切换
4. URL 状态可分享与回放

推荐交互：

- 顶部保留关键字搜索
- 筛选区域增加“标签” facet 面板
- 每个标签项展示 `displayName + count`
- 已选标签高亮
- 模式切换使用 `任一命中` / `全部命中` 开关

### 9.2 命名空间页

命名空间页同样应支持标签筛选，但 facet 数据仅基于当前 namespace 内可见 skill 聚合。

### 9.3 Skill 详情页

详情页继续展示已绑定 label，建议增强：

1. badge 点击可跳转搜索页并自动带入过滤参数
2. `PRIVILEGED` 与普通标签在视觉上区分

### 9.4 URL 状态设计

建议将前端搜索参数从单个 `label` 升级为：

- `labels: string[]`
- `labelMode: 'any' | 'all'`

同时需要对 URL 状态做归一化处理：

1. 去空值
2. 小写化
3. 去重
4. 排序

目的是保证：

- URL 稳定
- Query Key 稳定
- 页面分享一致

## 10. 统计与分析设计

### 10.1 搜索聚合与运营聚合分层

必须区分两类聚合：

#### 10.1.1 搜索聚合

定义：

- 当前搜索结果集中，各 label 的命中数量

特点：

- 实时
- 面向交互界面
- 由搜索服务返回

#### 10.1.2 运营聚合

定义：

- 按 label 汇总 skill 数、下载量、收藏量、评分、趋势等指标

特点：

- 面向运营与治理
- 不应直接依赖搜索 SQL
- 应建设独立统计投影

### 10.2 统计投影建议

建议引入独立统计表：

#### `label_skill_stats`

字段建议：

- `label_id`
- `skill_count`
- `download_count_sum`
- `star_count_sum`
- `rating_avg`
- `updated_at`

#### `label_daily_metrics`

字段建议：

- `label_id`
- `metric_date`
- `skill_count`
- `new_skill_count`
- `download_count`
- `star_count`

更新策略：

- 一期可通过定时任务日级重算
- 后续再考虑事件驱动增量更新

## 11. 权限与治理

建议在现有权限模型基础上明确以下规则：

1. 标签定义管理：仅超级管理员
2. `RECOMMENDED` 标签绑定：超级管理员、空间管理员、skill owner
3. `PRIVILEGED` 标签绑定：仅超级管理员
4. `DISABLED` 标签：允许历史展示，不允许新增绑定
5. `ARCHIVED` 标签：仅后台可见，不出现在前台与搜索聚合中

未来若引入 `USER_DEFINED` 标签，再增加：

1. 提交流程
2. 审核状态
3. 垃圾标签治理

## 12. 兼容性与迁移策略

### 12.1 对现有代码的兼容性

本设计避免以下破坏性变更：

1. 不修改 `skill_tag` 表语义
2. 不修改 `/tags` API 路径
3. 不推翻现有 `label` 相关实体与服务

### 12.2 渐进演进路径

#### 阶段 1：统一语义并补 facet

目标：

- 明确 skill 分类统一走 label
- 搜索接口返回 facet 聚合
- 前端展示计数

#### 阶段 2：前端升级多选筛选

目标：

- 支持多选 label
- 支持 `labelMode=any|all`
- namespace 页接入 facet

#### 阶段 3：标签治理增强

目标：

- 引入 `status`
- 引入 `usage_scope`
- 引入关系来源与治理字段

#### 阶段 4：统计投影

目标：

- 建设按标签的运营统计表
- 支撑下载、收藏、评分和趋势分析

## 13. 实施清单

### 13.1 后端

1. 扩展 `SearchQuery` 支持 `labelMode` 与 `includeFacets`
2. 扩展 `SkillSearchAppService.SearchResponse` 增加 `facets` 与 `appliedFilters`
3. 在 PostgreSQL 搜索实现中补齐：
   - `labelMode=all`
   - facet 聚合查询
4. 为 label 过滤与聚合补充索引
5. 视阶段推进情况扩展 `LabelDefinition` 与 `SkillLabel` 字段

### 13.2 前端

1. 将搜索参数从单个 `label` 升级为 `labels[] + labelMode`
2. 改造搜索页为 facet 面板
3. 展示 facet count
4. 支持 namespace 页内标签过滤
5. 调整 Query Key 归一化逻辑

### 13.3 测试

需要补充：

1. 多标签过滤测试
2. `labelMode=any|all` 测试
3. facet 聚合统计测试
4. 权限边界测试
5. 前端 URL 状态同步测试

### 13.4 文档

以下文档需要在实现时同步更新：

1. 搜索架构文档
2. API 设计文档
3. 前端架构说明中关于搜索筛选的部分

## 14. 风险与权衡

### 14.1 不新增第二套 skill tag 模型的收益

1. 领域边界清晰
2. 避免重复建设搜索链路
3. 避免重复建设权限模型
4. 后续迁移到 ES 时成本更低

### 14.2 一期不直接做用户自定义标签的原因

1. 会同时引入审核与治理复杂度
2. 会显著增加前后台权限分支
3. 与当前“受控标签体系”目标不一致

### 14.3 一期不直接做搜索文档 label 冗余的原因

1. 当前 join 成本可接受
2. 现有实现已经可用
3. 冗余索引会增加重建复杂度

## 15. 结论

SkillHub 应当采用如下统一策略：

1. `skill_tag` 保持“版本别名”定位不变
2. skill 的归类、过滤、聚合统一使用现有 label 体系
3. 产品界面可展示“标签”或“Tag”文案，但技术实现继续使用 label 命名
4. 搜索层优先补 facet 聚合与多标签过滤
5. 运营层另建统计投影，不与搜索索引耦合

该方案能够在最小改动下复用现有架构，同时为未来的多选筛选、治理和分析能力留出清晰演进路径。