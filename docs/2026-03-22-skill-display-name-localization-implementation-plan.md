# Skill 中文显示名能力实施计划

> Date: 2026-03-22
> Status: Draft
> Scope: 为 skill 增加中文显示名能力，支持手工填写、locale 优先展示、搜索命中增强，并为后续异步模型回填预留实现路径

## 1. 实施目标

本计划基于《Skill 中文显示名能力设计》，将设计拆解为可执行任务，支持：

1. skill 展示名称支持 locale 化优先显示
2. 发布时支持可选填写中文显示名
3. 搜索与列表页可命中、可展示中文显示名
4. 后续接入异步大模型翻译回填
5. 保证用户手工修改优先于模型结果

## 2. 总体实施策略

推荐分三个阶段推进：

1. Phase 1：落地手工中文显示名与展示链路
2. Phase 2：接入异步自动翻译回填
3. Phase 3：补齐存量回填与治理能力

每个阶段都应满足：

1. 可独立上线
2. 不改变现有 slug 语义
3. 不把外部模型依赖塞进同步发布路径
4. 不破坏现有兼容 API 对 `displayName` 的依赖

## 3. Phase 1：手工中文显示名与展示链路

### 3.1 目标

在不引入外部模型依赖的前提下，让系统具备：

1. 发布时可选填写中文显示名
2. skill 查询链路返回 locale 优先展示名
3. 搜索索引可纳入翻译名
4. owner 后续可编辑中文显示名

### 3.2 后端任务

#### Task 1：新增 skill 翻译数据模型

修改点：

1. 新增数据库迁移脚本
2. 新增 `SkillTranslation` 实体
3. 新增 `SkillTranslationRepository`

建议表结构：

1. `skill_id`
2. `locale`
3. `display_name`
4. `source_type`
5. `source_display_name`
6. `source_hash`
7. `stale`
8. `created_by`
9. `updated_by`

验收标准：

1. `(skill_id, locale)` 唯一约束生效
2. 支持按 `skill_id` 和 `locale` 快速查询

#### Task 2：新增 skill 本地化解析服务

修改点：

1. 新增 `SkillLocalizationService`

职责：

1. 根据当前 locale 解析 `preferredDisplayName`
2. 实现 `zh-CN -> zh -> canonicalDisplayName` 回退
3. 提供规范名 hash 计算工具

验收标准：

1. 无翻译时回退到规范名
2. `zh-CN` 与 `zh` 均能按预期命中

#### Task 3：扩展发布接口参数

修改点：

1. 修改 [server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillPublishController.java](server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillPublishController.java)
2. 修改 [server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java](server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java)

建议新增请求参数：

1. `localizedDisplayNameZhCn`

写入规则：

1. 规范名继续写 `skill.display_name`
2. 若传入中文显示名，则 upsert `zh-CN` 翻译，`source_type=USER`
3. 若未传入，则不清空已有人工翻译

验收标准：

1. 重复发布留空不覆盖已有 `USER` 翻译
2. 手工填写时可更新原有翻译值

#### Task 4：扩展 skill 查询 DTO

修改点：

1. 修改 [server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillSummaryResponse.java](server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillSummaryResponse.java)
2. 修改 [server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillDetailResponse.java](server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillDetailResponse.java)
3. 修改 [server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillQueryService.java](server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillQueryService.java)
4. 修改 [server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillSearchAppService.java](server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillSearchAppService.java)

建议新增字段：

1. `preferredDisplayName`
2. `canonicalDisplayName`

兼容策略：

1. `displayName` 保留现有语义
2. 前端逐步切换使用 `preferredDisplayName`

验收标准：

1. 现有调用方不因 DTO 扩展而中断
2. locale 命中时 `preferredDisplayName` 返回中文名

#### Task 5：新增 skill 翻译管理接口

修改点：

1. 新增 portal controller
2. 新增 app service

建议接口：

1. `GET /api/web/skills/{namespace}/{slug}/translations`
2. `PUT /api/web/skills/{namespace}/{slug}/translations/{locale}`
3. `DELETE /api/web/skills/{namespace}/{slug}/translations/{locale}`

权限要求：

1. skill owner
2. namespace ADMIN
3. namespace OWNER

验收标准：

1. 无权限用户不能写
2. 可手工新增、修改、删除 `zh-CN` 翻译

#### Task 6：扩展搜索重建逻辑，纳入翻译名

修改点：

1. 修改 [server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/PostgresSearchRebuildService.java](server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/PostgresSearchRebuildService.java)

要求：

1. `title` 继续写规范名
2. `keywords` 补入 skill 翻译名
3. `search_text` 补入 skill 翻译名

验收标准：

1. 中文关键词能命中英文规范名 skill
2. 无翻译时行为与现状一致

#### Task 7：翻译变更后触发搜索重建

修改点：

1. 新增 `SkillTranslationChangedEvent`
2. 新增监听器或复用现有同步服务

要求：

1. 手工新增翻译后重建 search document
2. 修改翻译后重建 search document
3. 删除翻译后重建 search document

验收标准：

1. 搜索展示与详情展示不出现长期不一致

#### Task 8：补充审计日志

记录动作：

1. 新增翻译
2. 修改翻译
3. 删除翻译

验收标准：

1. 审计记录能关联 skill、操作者、locale 与变更值

### 3.3 前端任务

#### Task 9：发布页增加中文显示名输入

修改点：

1. 修改 [web/src/pages/dashboard/publish.tsx](web/src/pages/dashboard/publish.tsx)
2. 修改 [web/src/shared/hooks/use-skill-queries.ts](web/src/shared/hooks/use-skill-queries.ts)

交互要求：

1. 字段可选
2. 留空不阻止发布
3. 提示“留空时系统可后续自动补全”

验收标准：

1. 表单提交时正确携带 multipart 参数
2. 未填写时不提交空字符串污染后端逻辑

#### Task 10：搜索与列表默认显示 `preferredDisplayName`

修改点：

1. 修改 [web/src/features/skill/skill-card.tsx](web/src/features/skill/skill-card.tsx)
2. 修改 [web/src/pages/search.tsx](web/src/pages/search.tsx)
3. 修改 [web/src/pages/namespace.tsx](web/src/pages/namespace.tsx)
4. 修改 [web/src/pages/home.tsx](web/src/pages/home.tsx)
5. 修改 [web/src/pages/landing.tsx](web/src/pages/landing.tsx)
6. 修改 [web/src/pages/dashboard/my-skills.tsx](web/src/pages/dashboard/my-skills.tsx)
7. 修改 [web/src/pages/dashboard/stars.tsx](web/src/pages/dashboard/stars.tsx)

验收标准：

1. 中文 locale 下优先显示中文名
2. 无翻译时回退到规范名

#### Task 11：详情页标题与操作提示切换为 `preferredDisplayName`

修改点：

1. 修改 [web/src/pages/skill-detail.tsx](web/src/pages/skill-detail.tsx)

验收标准：

1. 标题、toast、确认弹窗统一显示 `preferredDisplayName`
2. 不影响 slug 相关确认输入逻辑

#### Task 12：增加翻译编辑入口

建议位置：

1. skill 详情管理区
2. 或 dashboard 我的 skill 管理区

验收标准：

1. 有权限用户能编辑 `zh-CN` 名称
2. 普通用户看不到编辑入口

### 3.4 测试任务

#### Task 13：发布链路测试

修改点：

1. 扩展 [server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/portal/SkillPublishControllerTest.java](server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/portal/SkillPublishControllerTest.java)
2. 扩展 [server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillPublishServiceTest.java](server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillPublishServiceTest.java)

覆盖点：

1. 发布时填写中文显示名
2. 发布时未填写中文显示名
3. 重复发布不清空已有 `USER` 翻译
4. 更新已有 `USER` 翻译

#### Task 14：查询与搜索响应测试

修改点：

1. 扩展 [server/skillhub-app/src/test/java/com/iflytek/skillhub/service/SkillSearchAppServiceTest.java](server/skillhub-app/src/test/java/com/iflytek/skillhub/service/SkillSearchAppServiceTest.java)
2. 扩展 [server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillSearchControllerTest.java](server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillSearchControllerTest.java)

覆盖点：

1. `preferredDisplayName` 返回正确
2. locale fallback 正确
3. DTO 扩展保持兼容

#### Task 15：搜索索引重建测试

修改点：

1. 扩展 [server/skillhub-search/src/test/java/com/iflytek/skillhub/search/postgres/PostgresFullTextQueryServiceTest.java](server/skillhub-search/src/test/java/com/iflytek/skillhub/search/postgres/PostgresFullTextQueryServiceTest.java)
2. 扩展 [server/skillhub-search/src/test/java/com/iflytek/skillhub/search/event/SearchIndexEventListenerTest.java](server/skillhub-search/src/test/java/com/iflytek/skillhub/search/event/SearchIndexEventListenerTest.java)

覆盖点：

1. 翻译名被纳入搜索文本
2. 中文关键词可命中
3. 翻译变更触发重建

#### Task 16：前端页面测试

修改点：

1. 扩展 [web/src/pages/search.test.tsx](web/src/pages/search.test.tsx)
2. 扩展 [web/src/pages/namespace.test.tsx](web/src/pages/namespace.test.tsx)
3. 扩展 [web/src/pages/skill-detail.test.tsx](web/src/pages/skill-detail.test.tsx)
4. 扩展 [web/src/shared/hooks/use-skill-queries.test.ts](web/src/shared/hooks/use-skill-queries.test.ts)

覆盖点：

1. 默认显示 `preferredDisplayName`
2. 无翻译时显示规范名
3. 发布页可提交中文显示名

### 3.5 文档任务

#### Task 17：更新 API 与前端设计文档

修改点：

1. 更新 [docs/06-api-design.md](docs/06-api-design.md)
2. 更新 [docs/08-frontend-architecture.md](docs/08-frontend-architecture.md)
3. 更新 [docs/04-search-architecture.md](docs/04-search-architecture.md)

内容包括：

1. 新增翻译相关接口
2. `preferredDisplayName` 字段说明
3. 搜索文档纳入翻译名的约束

### 3.6 交付结果

Phase 1 完成后，系统应具备：

1. 手工中文显示名维护能力
2. 中文 locale 优先展示能力
3. 中文显示名可参与搜索命中
4. 不依赖外部模型即可交付用户价值

## 4. Phase 2：异步自动翻译回填

### 4.1 目标

为未填写中文显示名的英文 skill 提供自动回填能力，但不阻塞发布主流程。

### 4.2 后端任务

#### Task 18：新增翻译任务表与状态机

新增：

1. `skill_translation_task`

字段建议：

1. `skill_id`
2. `locale`
3. `source_display_name`
4. `source_hash`
5. `status`
6. `attempt_count`
7. `next_run_at`
8. `provider_code`
9. `model_name`
10. `last_error`

#### Task 19：新增翻译 provider SPI

新增：

1. `SkillNameTranslationProvider`

要求：

1. 可插拔
2. 可配置关闭
3. 默认 `noop`

#### Task 20：发布后投递翻译任务

规则：

1. skill 无 `USER` 类型 `zh-CN` 翻译
2. 规范名非中文
3. 当前 source hash 没有重复任务

#### Task 21：实现后台 worker / scheduler

要求：

1. 拉取 `PENDING` 任务
2. 调用 provider
3. 成功则写入 `MACHINE` 翻译
4. 用户已手工修改时标记 `SKIPPED`

#### Task 22：机器回填成功后重建搜索索引

要求：

1. 自动翻译与手工翻译一样触发索引重建

### 4.3 测试任务

#### Task 23：任务投递与执行测试

覆盖点：

1. 非中文 skill 触发任务
2. 中文 skill 不触发任务
3. 用户已填写时不触发
4. 用户在任务执行前修改时任务跳过
5. 失败重试与终态记录正确

### 4.4 交付结果

Phase 2 完成后，系统应具备：

1. 未填写场景的自动中文名补全能力
2. 用户修改优先、模型不覆盖的安全机制

## 5. Phase 3：存量回填与治理增强

### 5.1 目标

让存量 skill 逐步具备中文显示名，并补齐治理能力。

### 5.2 后端任务

#### Task 24：存量 skill 批量扫描与回填

规则：

1. 已有 `USER` 翻译跳过
2. 中文规范名跳过
3. 其余生成任务

#### Task 25：增加 `stale` 管理逻辑

要求：

1. 规范名变更时，仅 `MACHINE` 翻译自动标记 `stale=true`
2. `USER` 翻译不被覆盖

#### Task 26：增加后台治理查询

支持：

1. 查看自动翻译结果
2. 查看 `stale` 翻译
3. 查看失败任务
4. 一键重试

### 5.3 交付结果

Phase 3 完成后，系统将具备：

1. 存量 skill 中文名补齐能力
2. 自动翻译质量治理能力

## 6. 任务依赖关系

### 6.1 强依赖

1. Phase 1 是 Phase 2 的前置条件
2. Phase 2 是 Phase 3 批量回填的前置条件

### 6.2 推荐实施顺序

推荐顺序：

1. Task 1-17
2. Task 18-23
3. Task 24-26

## 7. 建议 issue 拆分

### Issue A：skill 翻译数据模型与查询回退

包含：

1. Task 1
2. Task 2
3. Task 4

### Issue B：发布链路支持中文显示名

包含：

1. Task 3
2. Task 9
3. Task 13

### Issue C：搜索索引纳入翻译名

包含：

1. Task 6
2. Task 7
3. Task 15

### Issue D：前端默认显示 `preferredDisplayName`

包含：

1. Task 10
2. Task 11
3. Task 16

### Issue E：skill 翻译编辑接口与权限控制

包含：

1. Task 5
2. Task 8
3. Task 12

### Issue F：异步自动翻译回填

包含：

1. Task 18-23

### Issue G：存量回填与治理能力

包含：

1. Task 24-26

## 8. 验收标准

整体完成后，应满足以下验收标准：

1. skill 在中文 locale 下可优先显示中文名称
2. 用户可在发布时手工填写中文显示名
3. 用户可后续编辑中文显示名
4. 搜索可通过中文显示名命中 skill
5. 用户修改优先于机器翻译
6. 自动翻译不阻塞发布

## 9. 结论

本实施计划将“中文显示名能力”拆解为一条低风险、可逐步交付的路线：

1. 先完成手工可维护与展示链路
2. 再补自动翻译异步回填
3. 最后处理存量和治理

建议优先推进 Issue A-D，尽快交付用户可感知的中文显示体验。
