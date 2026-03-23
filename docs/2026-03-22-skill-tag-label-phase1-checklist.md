# Skill Tag / Label Phase 1 开发 Checklist

> Date: 2026-03-22
> Status: Draft
> Goal: 在不改动现有 `skill_tag` 语义的前提下，为搜索接口补齐多标签过滤、`labelMode=any|all` 与 facet 聚合返回

## 1. 范围定义

Phase 1 只做后端协议与搜索能力补齐，不做前端多选筛选 UI 重构，不做 label 治理模型扩展，不做统计投影。

本阶段交付目标：

1. 搜索接口支持多标签过滤
2. 搜索接口支持 `labelMode=any|all`
3. 搜索接口支持返回 facet 聚合
4. 保持现有单标签调用兼容

## 2. 必改文件

### 2.1 Controller 层

#### [server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillSearchController.java](server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillSearchController.java)

需要改动：

1. 新增 `labelMode` 请求参数
2. 新增 `includeFacets` 请求参数
3. 保持现有 `label` 多值参数兼容
4. 将新参数透传给应用服务

建议签名变化：

- 保留 `@RequestParam(name = "label", required = false) List<String> labels`
- 新增 `@RequestParam(defaultValue = "any") String labelMode`
- 新增 `@RequestParam(defaultValue = "true") boolean includeFacets`

实现注意点：

1. `labelMode` 非法值应在 controller 或 app service 层统一拒绝
2. 未传 `labelMode` 时默认 `any`
3. 未传 `includeFacets` 时默认 `true`

### 2.2 应用服务层

#### [server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillSearchAppService.java](server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillSearchAppService.java)

需要改动：

1. 扩展 `SearchResponse`
2. 扩展 `search(...)` 方法签名
3. 下传 `labelMode` 与 `includeFacets`
4. 组装 `facets` 与 `appliedFilters`

建议新增 record：

1. `SearchFacetResponse`
2. `LabelFacetGroupResponse`
3. `LabelFacetItemResponse`
4. `AppliedFiltersResponse`

建议 `SearchResponse` 结构：

- `items`
- `total`
- `page`
- `size`
- `facets`
- `appliedFilters`

实现注意点：

1. 旧调用方如果只关心 `items/total/page/size` 不应被破坏
2. `labelSlugs` 在这里做归一化：去空、trim、小写、去重
3. `labelMode` 在这里统一标准化并校验

### 2.3 搜索请求模型

#### [server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchQuery.java](server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchQuery.java)

需要改动：

1. 增加 `labelMode`
2. 增加 `includeFacets`

建议新增字段：

- `String labelMode`
- `boolean includeFacets`

兼容要求：

1. 保留当前便捷构造方法
2. 未传值时默认 `labelMode=any`、`includeFacets=true`

### 2.4 搜索查询 SPI 返回模型

#### [server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchResult.java](server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchResult.java)

需要改动：

1. 扩展返回结构以包含 facets

建议新增：

- `List<LabelFacet>` 或 `Map<String, List<...>>`

建议不要把 `facets` 留在 `SkillSearchAppService` 里单独查第二次，优先让 `SearchQueryService` 一次返回结果与聚合，避免应用层再拼接 SQL。

### 2.5 PostgreSQL 搜索实现

#### [server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/PostgresFullTextQueryService.java](server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/PostgresFullTextQueryService.java)

这是 Phase 1 的核心改造文件。

需要改动：

1. 支持 `labelMode=all`
2. 支持 facet 聚合查询
3. 将当前大方法拆出私有 helper，降低复杂度

建议拆分为以下私有方法：

1. `appendVisibilityFilters(...)`
2. `appendNamespaceFilter(...)`
3. `appendLabelFilter(...)`
4. `appendKeywordFilter(...)`
5. `applyCommonParameters(...)`
6. `buildCountSql(...)`
7. `queryLabelFacets(...)`

#### `labelMode=all` 的 SQL 要点

建议逻辑：

- `any`：保留现有 `IN (SELECT ... WHERE slug in :labelSlugs)`
- `all`：改为 `GROUP BY skill_id HAVING COUNT(DISTINCT lower(ld.slug)) = :labelCount`

#### facet 查询要点

建议基于当前 matched skill 集做聚合，而不是全库聚合。

建议流程：

1. 先构造与主搜索同条件的基础 where 片段
2. 用该 where 片段生成一个 `WITH matched_skills AS (...)`
3. 在 matched_skills 上 join `skill_label`、`label_definition`、`label_translation`

实现注意点：

1. facet count 一期采用 narrow facets
2. 只统计当前可用于公开筛选的标签
3. locale fallback 顺序为：当前 locale -> en -> slug

### 2.6 可能需要的附属文件

#### [server/skillhub-app/src/main/java/com/iflytek/skillhub/service/LabelLocalizationService.java](server/skillhub-app/src/main/java/com/iflytek/skillhub/service/LabelLocalizationService.java)

如果当前已有 label 本地化工具方法可复用，优先复用；否则在搜索层新增轻量聚合 DTO 时也可以直接通过 SQL fallback 实现。

#### [server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillLabelJpaRepository.java](server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillLabelJpaRepository.java)

Phase 1 原则上不依赖新增 repository 方法，因为 facet 建议直接在搜索层用 native SQL 完成。但如果需要补索引或特殊查询，可同步评估。

## 3. 需要新增的类或 DTO

本阶段建议新增最小 DTO，而不是提前做过度抽象。

建议新增位置：

1. `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/`
2. 或作为 `SearchResult` 的嵌套 record

建议新增结构：

1. `LabelFacet`
   - `slug`
   - `displayName`
   - `count`
   - `selected`
   - `type`

2. `SearchFacets`
   - `labels`

3. `AppliedSearchFilters`
   - `labels`
   - `labelMode`
   - `namespace`
   - `sort`

原则：

1. 一期不要加入 `usageScope`、`status` 等 Phase 3 字段
2. 一期只返回当前搜索页真正会用到的结构

## 4. 需要修改的测试文件

### 4.1 Controller 测试

#### [server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillSearchControllerTest.java](server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillSearchControllerTest.java)

补充用例：

1. 传入多个 `label` 参数时正确透传
2. 传入 `labelMode=all` 时正确透传
3. 传入 `includeFacets=false` 时正确透传
4. 缺省参数时默认为 `labelMode=any`、`includeFacets=true`

### 4.2 应用服务测试

#### [server/skillhub-app/src/test/java/com/iflytek/skillhub/service/SkillSearchAppServiceTest.java](server/skillhub-app/src/test/java/com/iflytek/skillhub/service/SkillSearchAppServiceTest.java)

补充用例：

1. label 列表归一化
2. label 去重与空值过滤
3. `labelMode` 标准化
4. `appliedFilters` 正确回填
5. `SearchResult` 中 facets 正确映射到 response

### 4.3 PostgreSQL 搜索实现测试

#### [server/skillhub-search/src/test/java/com/iflytek/skillhub/search/postgres/PostgresFullTextQueryServiceTest.java](server/skillhub-search/src/test/java/com/iflytek/skillhub/search/postgres/PostgresFullTextQueryServiceTest.java)

这是 Phase 1 的重点测试文件。

必须新增用例：

1. 多标签 `any` 语义
2. 多标签 `all` 语义
3. facet 聚合返回 count
4. facet selected 标记正确
5. locale fallback 到 `en`
6. locale fallback 到 `slug`
7. `includeFacets=false` 时不执行 facet 查询或返回空 facets

### 4.4 兼容层测试

以下文件可能因 `SearchResponse` 构造签名变化而需要调整：

1. [server/skillhub-app/src/test/java/com/iflytek/skillhub/compat/ClawHubCompatControllerTest.java](server/skillhub-app/src/test/java/com/iflytek/skillhub/compat/ClawHubCompatControllerTest.java)
2. [server/skillhub-app/src/test/java/com/iflytek/skillhub/compat/ClawHubRegistryFacadeTest.java](server/skillhub-app/src/test/java/com/iflytek/skillhub/compat/ClawHubRegistryFacadeTest.java)

处理方式：

1. 如果 `SearchResponse` record 构造参数增加，需要同步补默认 facets 和 appliedFilters
2. 若不想波及太多测试，可优先保留兼容构造器

## 5. 需要同步更新的文档

### 5.1 搜索架构文档

#### [docs/04-search-architecture.md](docs/04-search-architecture.md)

需要更新：

1. `SearchQuery` 结构
2. `labelMode=any|all`
3. facet 聚合语义

### 5.2 统一设计文档

#### [docs/2026-03-22-skill-tag-label-unification-design.md](docs/2026-03-22-skill-tag-label-unification-design.md)

在代码落地后建议把“建议设计”补充为“已落地范围”，防止文档与实现偏离。

## 6. 数据库与索引 Checklist

Phase 1 不强制引入新的业务表，但建议评估并补齐索引。

### 6.1 索引建议

建议检查是否已存在以下索引：

1. `skill_label(label_id, skill_id)`
2. `skill_label(skill_id, label_id)`
3. `label_translation(label_id, locale)`

如果未存在：

1. 增加 Flyway migration
2. 补 explain plan 验证

### 6.2 不在 Phase 1 处理的事项

以下内容明确不在本阶段处理：

1. `label_definition.status`
2. `label_definition.usage_scope`
3. `skill_label.source`
4. 统计投影表

## 7. 开发顺序 Checklist

建议严格按以下顺序做，减少返工。

### Step 1：扩展返回模型

1. 改 `SearchResult`
2. 改 `SkillSearchAppService.SearchResponse`
3. 保持兼容构造方式

### Step 2：扩展请求模型

1. 改 `SearchQuery`
2. 改 `SkillSearchAppService.search(...)`
3. 改 `SkillSearchController`

### Step 3：补 `labelMode=all`

1. 改 `PostgresFullTextQueryService`
2. 先只跑搜索结果，不加 facets
3. 补单元测试

### Step 4：补 facets 查询

1. 在 `PostgresFullTextQueryService` 增加 facet 查询
2. 组装到 `SearchResult`
3. 让 app service 输出 `facets` 和 `appliedFilters`

### Step 5：补回归测试

1. Controller 测试
2. AppService 测试
3. QueryService 测试
4. Compat 测试

### Step 6：更新文档

1. 更新搜索架构文档
2. 在统一设计文档标注 Phase 1 已覆盖内容

## 8. 验收清单

### 8.1 功能验收

以下请求应通过：

1. `/api/web/skills?q=agent&label=rag`
2. `/api/web/skills?q=agent&label=rag&label=official`
3. `/api/web/skills?q=agent&label=rag&label=official&labelMode=any`
4. `/api/web/skills?q=agent&label=rag&label=official&labelMode=all`
5. `/api/web/skills?q=agent&label=rag&includeFacets=false`

### 8.2 返回结构验收

响应中应包含：

1. `items`
2. `total`
3. `page`
4. `size`
5. `facets.labels.items`
6. `appliedFilters.labels`
7. `appliedFilters.labelMode`

### 8.3 回归验收

必须确认以下行为未退化：

1. 单标签过滤仍可用
2. 无标签过滤时搜索结果不变
3. relevance、downloads、newest 排序不变
4. 兼容层搜索调用不因响应扩展而失败

## 9. 建议运行的验证命令

优先跑最小相关测试：

1. `cd server && ./mvnw -pl skillhub-search -Dtest=PostgresFullTextQueryServiceTest test`
2. `cd server && ./mvnw -pl skillhub-app -Dtest=SkillSearchAppServiceTest,SkillSearchControllerTest test`

完成后再跑一轮更宽的回归：

1. `make test`

## 10. 实施边界提醒

Phase 1 不要顺手做以下事情：

1. 不要把 `label` 改名为 `tag`
2. 不要修改 `skill_tag` 表或 `/tags` 路径语义
3. 不要提前引入 `USER_DEFINED` 标签
4. 不要把统计分析逻辑塞进搜索 SQL

## 11. 完成定义

Phase 1 完成的标准是：

1. 搜索后端协议支持多标签、`any|all`、facets
2. 单元测试与控制器测试覆盖关键路径
3. 文档已同步更新
4. 兼容调用未被破坏

达到以上条件后，才进入 Phase 2 前端多选筛选改造。