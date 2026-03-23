# Skill Tag / Label 统一实施计划

> Date: 2026-03-22
> Status: Draft
> Scope: 将 skill 分类能力统一到现有 label 架构，并补齐搜索过滤与聚合能力

## 1. 实施目标

本计划基于《Skill Tag / Label 统一设计》，将设计拆解为可执行任务，支持：

1. 统一 skill 分类标签语义
2. 补齐搜索 facet 聚合
3. 将前端从单选 label 过滤升级为多选 facet 过滤
4. 为后续治理与统计分析留出演进路径

## 2. 总体实施策略

推荐分四个阶段推进：

1. Phase 1：补齐搜索 facet 与后端协议
2. Phase 2：升级前端多标签筛选体验
3. Phase 3：增强标签治理模型
4. Phase 4：建设标签统计投影

每个阶段都应保持：

1. 可独立上线
2. 不破坏现有 `skill_tag` 版本别名能力
3. 不引入大规模架构重做

## 3. Phase 1：搜索 facet 与协议扩展

### 3.1 目标

在不改变现有 label 核心模型的前提下，让搜索接口支持：

1. 多标签过滤
2. `labelMode=any|all`
3. facet 聚合返回

### 3.2 后端任务

#### Task 1：扩展搜索请求模型

修改点：

1. 扩展 `SearchQuery`
2. 增加 `labelMode`
3. 增加 `includeFacets`

建议字段：

- `List<String> labelSlugs`
- `String labelMode`
- `boolean includeFacets`

验收标准：

1. 默认行为与当前兼容
2. 未传参数时仍保持现有单标签过滤能力

#### Task 2：扩展搜索控制器参数解析

修改点：

1. 扩展 [server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillSearchController.java](server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillSearchController.java)

需要支持：

1. 多个 `label`
2. `labelMode`
3. `includeFacets`

验收标准：

1. 现有请求兼容
2. 参数非法时返回统一 bad request

#### Task 3：扩展搜索应用服务响应结构

修改点：

1. 扩展 [server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillSearchAppService.java](server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillSearchAppService.java)

新增响应字段：

1. `facets`
2. `appliedFilters`

验收标准：

1. items、total、page、size 保持不变
2. facets 为空时仍返回稳定结构

#### Task 4：实现 `labelMode=all`

修改点：

1. 扩展 [server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/PostgresFullTextQueryService.java](server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/PostgresFullTextQueryService.java)

要求：

1. `any` 使用现有 `IN` 语义
2. `all` 使用 `GROUP BY skill_id HAVING COUNT(DISTINCT ...) = :labelCount`

验收标准：

1. `any` 与当前行为一致
2. `all` 在多标签场景下结果正确

#### Task 5：实现 facet 聚合查询

修改点：

1. 在 PostgreSQL 搜索实现中新增 facet 聚合逻辑
2. 增加 facet DTO

要求：

1. facet 基于当前结果集统计
2. 仅统计可用于发现筛选的标签
3. 标签显示名遵循 locale fallback

验收标准：

1. facet count 与结果集一致
2. selected 状态与请求参数一致

#### Task 6：补充数据库索引

建议索引：

1. `skill_label(label_id, skill_id)`
2. `skill_label(skill_id, label_id)`
3. `label_definition(status, usage_scope, sort_order)` 或第一阶段先以 `visible_in_filter` 相关索引替代
4. `label_translation(label_id, locale)`

验收标准：

1. explain plan 不出现明显全表扫描退化
2. 搜索与 facet 查询响应可接受

### 3.3 测试任务

#### Task 7：搜索控制器测试

覆盖点：

1. 单标签过滤
2. 多标签过滤
3. `labelMode=any`
4. `labelMode=all`
5. `includeFacets=true|false`

#### Task 8：搜索查询服务测试

覆盖点：

1. facet count 正确性
2. locale fallback 正确性
3. 无标签时 facets 为空
4. 标签不存在时结果与 count 行为正确

### 3.4 文档任务

#### Task 9：更新搜索架构文档

修改点：

1. 更新 [docs/04-search-architecture.md](docs/04-search-architecture.md)

内容包括：

1. `SearchQuery` 新字段
2. facet 聚合语义
3. `labelMode` 行为说明

### 3.5 交付结果

Phase 1 完成后，系统应具备：

1. 多标签搜索过滤能力
2. facet 聚合返回能力
3. 向前兼容现有搜索页调用方式

## 4. Phase 2：前端多标签筛选升级

### 4.1 目标

把当前搜索页从单选 label 按钮升级为支持多选与计数展示的 facet 交互。

### 4.2 前端任务

#### Task 10：升级前端搜索参数模型

修改点：

1. 修改 [web/src/api/types.ts](web/src/api/types.ts)
2. 将 `label?: string` 升级为：
   - `labels?: string[]`
   - `labelMode?: 'any' | 'all'`

验收标准：

1. 前端请求类型可表达多选场景
2. 旧链接仍可兼容迁移

#### Task 11：改造 URL 构建逻辑

修改点：

1. 修改 [web/src/shared/hooks/skill-query-helpers.ts](web/src/shared/hooks/skill-query-helpers.ts)

要求：

1. 支持重复 `label` query parameter
2. 参数归一化：去空、去重、排序、小写

#### Task 12：改造路由 search 校验

修改点：

1. 修改 [web/src/app/router.tsx](web/src/app/router.tsx)

要求：

1. 支持 `labels[]`
2. 支持 `labelMode`
3. 保持旧 `label` 参数的兼容读取

#### Task 13：改造搜索页为 facet 面板

修改点：

1. 修改 [web/src/pages/search.tsx](web/src/pages/search.tsx)

交互要求：

1. 展示 facet 标签及 count
2. 支持多选
3. 支持 `any|all` 模式切换
4. 切换过滤时重置分页
5. 与关键字、排序、收藏过滤状态兼容

#### Task 14：命名空间页接入标签过滤

修改点：

1. 修改 [web/src/pages/namespace.tsx](web/src/pages/namespace.tsx)

要求：

1. 只展示当前 namespace 内有结果的标签
2. 与 namespace skill 列表联动

#### Task 15：详情页标签跳转增强

要求：

1. 点击 skill label 可跳转搜索页并自动带入过滤条件
2. `PRIVILEGED` 标签具备更明确视觉样式

### 4.3 测试任务

#### Task 16：搜索页 URL 状态测试

覆盖点：

1. 多标签初始渲染
2. 多标签切换
3. `labelMode` 切换
4. 排序和分页不丢失标签状态
5. 收藏过滤与 facet 状态兼容

#### Task 17：命名空间页筛选测试

覆盖点：

1. 命名空间内 facet 渲染
2. 标签切换后的结果联动

### 4.4 交付结果

Phase 2 完成后，用户可在搜索与 namespace 页面使用完整的多标签 facet 过滤体验。

## 5. Phase 3：标签治理模型增强

### 5.1 目标

增强标签定义与关系模型，使其能够支撑状态管理、展示控制和后续运营治理。

### 5.2 后端任务

#### Task 18：扩展 `LabelDefinition`

修改点：

1. 修改 [server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/label/LabelDefinition.java](server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/label/LabelDefinition.java)
2. 新增 `status`
3. 新增 `usageScope`

#### Task 19：扩展标签枚举与校验

新增：

1. `LabelStatus`
2. `LabelUsageScope`

要求：

1. 非 ACTIVE 标签不参与公开筛选
2. 非 `DISCOVERY_FILTER` 标签不出现在 facet 中

#### Task 20：扩展 `SkillLabel`

新增：

1. `source`
2. `updatedAt`

#### Task 21：增强后台标签管理接口

要求：

1. 支持状态修改
2. 支持用途修改
3. 对前台展示和搜索聚合的影响明确定义

### 5.3 数据迁移任务

#### Task 22：编写数据库迁移脚本

包含：

1. 新增列
2. 历史数据回填
3. 默认值设置

建议回填策略：

1. 现有可筛选标签默认 `status=ACTIVE`
2. 现有 `visible_in_filter=true` 的标签默认 `usageScope=DISCOVERY_FILTER`
3. 其余标签默认 `usageScope=DETAIL_BADGE`

### 5.4 测试任务

#### Task 23：治理规则测试

覆盖点：

1. `DISABLED` 标签不可新绑定
2. `ARCHIVED` 标签不出现在前台
3. `DISCOVERY_FILTER` 以外标签不参与 facet

### 5.5 交付结果

Phase 3 完成后，label 将具备完整的治理维度，支持更细粒度的发现、展示与权限控制。

## 6. Phase 4：标签统计投影

### 6.1 目标

为标签建立搜索之外的统计分析能力，支持运营、治理和产品洞察。

### 6.2 后端任务

#### Task 24：建设当前统计投影表

新增：

1. `label_skill_stats`

字段建议：

1. `label_id`
2. `skill_count`
3. `download_count_sum`
4. `star_count_sum`
5. `rating_avg`
6. `updated_at`

#### Task 25：建设日级统计投影表

新增：

1. `label_daily_metrics`

字段建议：

1. `label_id`
2. `metric_date`
3. `skill_count`
4. `new_skill_count`
5. `download_count`
6. `star_count`

#### Task 26：实现统计作业

建议先使用：

1. 定时重算

后续可扩展为：

1. 事件驱动增量更新

### 6.3 应用层任务

#### Task 27：提供后台统计查询接口

支持：

1. 按标签查看总量
2. 按标签查看趋势
3. 标签维度排序与过滤

### 6.4 测试任务

#### Task 28：统计正确性测试

覆盖点：

1. 标签绑定变更后的投影更新
2. 下载、收藏变更后的统计更新
3. 日级趋势聚合正确性

### 6.5 交付结果

Phase 4 完成后，系统将同时具备：

1. 搜索层 facet 聚合
2. 运营层统计聚合

## 7. 任务依赖关系

### 7.1 强依赖

1. Phase 1 是 Phase 2 的前置条件
2. Phase 3 不阻塞 Phase 2，但会影响 facet 展示逻辑的最终规则
3. Phase 4 独立于 Phase 2，可在治理模型明确后并行推进

### 7.2 推荐实施顺序

推荐顺序：

1. Task 1-9
2. Task 10-17
3. Task 18-23
4. Task 24-28

## 8. 建议 issue 拆分

建议按以下 issue 粒度管理：

### Issue A：搜索后端支持 facets 与多标签过滤

包含：

- Task 1-6
- Task 7-8

### Issue B：搜索协议与文档同步

包含：

- Task 3
- Task 9

### Issue C：搜索页 facet 前端改造

包含：

- Task 10-13
- Task 16

### Issue D：namespace 页标签筛选改造

包含：

- Task 14
- Task 17

### Issue E：标签治理模型增强

包含：

- Task 18-23

### Issue F：标签统计投影

包含：

- Task 24-28

## 9. 验收标准

项目整体完成后，应满足以下验收标准：

1. skill 分类、过滤、聚合统一使用 label 体系
2. `skill_tag` 继续只承担版本别名职责
3. 搜索接口支持多标签过滤和 facet 聚合
4. 前端支持多选标签和 `any|all` 模式
5. 标签治理规则可配置、可测试
6. 标签统计聚合与搜索聚合职责分离

## 10. 结论

本实施计划确保 SkillHub 在不推翻现有架构的前提下，逐步完成：

1. 术语统一
2. 搜索能力完善
3. 治理能力增强
4. 统计分析扩展

建议先以 Phase 1 和 Phase 2 为主线推进，优先交付用户可感知的搜索与筛选能力，再进入治理和统计层建设。