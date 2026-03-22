# Skill 中文显示名 Phase 1 开发 Checklist

> Date: 2026-03-22
> Status: Draft
> Goal: 在不引入外部模型依赖的前提下，为 skill 建立手工中文显示名、locale 优先展示与搜索命中能力

## 1. 范围定义

Phase 1 只做：

1. skill 翻译数据模型
2. 发布时手工填写中文显示名
3. 查询接口返回 `preferredDisplayName`
4. 搜索索引纳入翻译名
5. 基础编辑接口与前端展示切换

本阶段不做：

1. 大模型翻译调用
2. 异步翻译任务调度
3. 存量批量回填
4. 后台治理看板

## 2. 必改文件

### 2.1 数据模型与迁移

需要新增：

1. 新的 Flyway migration
2. `SkillTranslation` 实体
3. `SkillTranslationRepository`

实现注意点：

1. 唯一约束使用 `(skill_id, locale)`
2. `display_name` 长度与 `skill.display_name` 保持一致
3. `source_type` 一期至少支持 `USER` 与 `MACHINE`

### 2.2 发布链路

#### [server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillPublishController.java](server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillPublishController.java)

需要改动：

1. 接收可选 multipart 参数 `localizedDisplayNameZhCn`
2. 透传给发布应用逻辑

实现注意点：

1. 空字符串归一化为未填写
2. 不影响已有 `file`、`visibility`、`label` 参数

#### [server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java](server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java)

需要改动：

1. 发布成功后处理 `zh-CN` 翻译 upsert
2. 继续保持 `skill.display_name = metadata.name`

实现注意点：

1. 未填写不清空已有 `USER` 翻译
2. 填写时覆盖已有 `zh-CN` 翻译并记为 `USER`

### 2.3 本地化服务

需要新增：

1. `SkillLocalizationService`

职责：

1. 解析 `preferredDisplayName`
2. 处理 locale fallback
3. 提供规范名 hash 或归一化工具

实现注意点：

1. 回退顺序统一
2. 不在 controller 层做散落的 fallback 逻辑

### 2.4 查询与 DTO

#### [server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillQueryService.java](server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillQueryService.java)

需要改动：

1. 聚合 skill 翻译名
2. 返回 `preferredDisplayName`
3. 返回 `canonicalDisplayName`

#### [server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillSearchAppService.java](server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillSearchAppService.java)

需要改动：

1. 搜索结果映射增加 `preferredDisplayName`
2. 搜索列表 DTO 同步扩展

#### [server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillSummaryResponse.java](server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillSummaryResponse.java)

需要改动：

1. 新增 `preferredDisplayName`
2. 新增 `canonicalDisplayName`

#### [server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillDetailResponse.java](server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillDetailResponse.java)

需要改动：

1. 新增 `preferredDisplayName`
2. 新增 `canonicalDisplayName`

实现注意点：

1. 保留现有 `displayName` 字段，先不改语义
2. 兼容已有前端与兼容层依赖

### 2.5 搜索重建

#### [server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/PostgresSearchRebuildService.java](server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/PostgresSearchRebuildService.java)

需要改动：

1. 将 skill 翻译名纳入 `keywords`
2. 将 skill 翻译名纳入 `search_text`

实现注意点：

1. `title` 仍使用规范名
2. 只补充翻译名，不替换现有搜索主文本

### 2.6 搜索同步事件

需要新增：

1. `SkillTranslationChangedEvent`
2. 对应的 search rebuild listener 或 sync service 调用

要求：

1. 新增翻译后重建
2. 修改翻译后重建
3. 删除翻译后重建

### 2.7 翻译管理接口

建议新增：

1. skill translation controller
2. skill translation app service

要求：

1. `GET translations`
2. `PUT translation`
3. `DELETE translation`
4. 复用现有 lifecycle 管理权限

## 3. 前端改动清单

### 3.1 发布页

#### [web/src/pages/dashboard/publish.tsx](web/src/pages/dashboard/publish.tsx)

需要改动：

1. 增加“中文显示名”输入框
2. 增加辅助提示文案

#### [web/src/shared/hooks/use-skill-queries.ts](web/src/shared/hooks/use-skill-queries.ts)

需要改动：

1. 扩展 `publishSkill()` 参数类型
2. multipart 增加 `localizedDisplayNameZhCn`

### 3.2 默认显示名切换

#### [web/src/features/skill/skill-card.tsx](web/src/features/skill/skill-card.tsx)

需要改动：

1. 卡片主标题优先显示 `preferredDisplayName`

#### [web/src/pages/search.tsx](web/src/pages/search.tsx)

需要改动：

1. 搜索结果列表统一使用 `preferredDisplayName`

#### [web/src/pages/namespace.tsx](web/src/pages/namespace.tsx)

需要改动：

1. namespace skill 列表统一使用 `preferredDisplayName`

#### [web/src/pages/home.tsx](web/src/pages/home.tsx)

需要改动：

1. 首页列表统一使用 `preferredDisplayName`

#### [web/src/pages/landing.tsx](web/src/pages/landing.tsx)

需要改动：

1. landing 页 skill card 统一使用 `preferredDisplayName`

#### [web/src/pages/dashboard/my-skills.tsx](web/src/pages/dashboard/my-skills.tsx)

需要改动：

1. 我的 skill 列表统一使用 `preferredDisplayName`

#### [web/src/pages/dashboard/stars.tsx](web/src/pages/dashboard/stars.tsx)

需要改动：

1. 收藏列表统一使用 `preferredDisplayName`

#### [web/src/pages/skill-detail.tsx](web/src/pages/skill-detail.tsx)

需要改动：

1. 标题、toast、确认弹窗统一使用 `preferredDisplayName`

### 3.3 编辑入口

建议优先落在以下页面之一：

1. [web/src/pages/skill-detail.tsx](web/src/pages/skill-detail.tsx)
2. [web/src/pages/dashboard/my-skills.tsx](web/src/pages/dashboard/my-skills.tsx)

要求：

1. 仅管理者可见
2. 可编辑 `zh-CN` 单语言名称

## 4. 测试清单

### 4.1 后端测试

#### [server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/portal/SkillPublishControllerTest.java](server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/portal/SkillPublishControllerTest.java)

补充：

1. 发布时携带中文显示名参数
2. 留空时不提交该参数

#### [server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillPublishServiceTest.java](server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillPublishServiceTest.java)

补充：

1. 新建 skill 时写入 `USER` 翻译
2. 重复发布不清空已有人工翻译
3. 更新已有人工翻译

#### [server/skillhub-app/src/test/java/com/iflytek/skillhub/service/SkillSearchAppServiceTest.java](server/skillhub-app/src/test/java/com/iflytek/skillhub/service/SkillSearchAppServiceTest.java)

补充：

1. `preferredDisplayName` 正确映射
2. 无翻译时回退到规范名

#### [server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillSearchControllerTest.java](server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillSearchControllerTest.java)

补充：

1. 搜索响应包含新增字段
2. 兼容旧字段结构

#### [server/skillhub-search/src/test/java/com/iflytek/skillhub/search/postgres/PostgresFullTextQueryServiceTest.java](server/skillhub-search/src/test/java/com/iflytek/skillhub/search/postgres/PostgresFullTextQueryServiceTest.java)

补充：

1. 翻译名可被搜索命中
2. 中文关键词命中英文 skill

#### [server/skillhub-search/src/test/java/com/iflytek/skillhub/search/event/SearchIndexEventListenerTest.java](server/skillhub-search/src/test/java/com/iflytek/skillhub/search/event/SearchIndexEventListenerTest.java)

补充：

1. 翻译变更触发重建

### 4.2 前端测试

#### [web/src/pages/search.test.tsx](web/src/pages/search.test.tsx)

补充：

1. 搜索页显示 `preferredDisplayName`

#### [web/src/pages/namespace.test.tsx](web/src/pages/namespace.test.tsx)

补充：

1. namespace 列表显示 `preferredDisplayName`

#### [web/src/pages/skill-detail.test.tsx](web/src/pages/skill-detail.test.tsx)

补充：

1. 标题与操作提示使用 `preferredDisplayName`

#### [web/src/shared/hooks/use-skill-queries.test.ts](web/src/shared/hooks/use-skill-queries.test.ts)

补充：

1. 发布表单正确提交中文显示名

## 5. 文档同步

需要同步更新：

1. [docs/06-api-design.md](docs/06-api-design.md)
2. [docs/08-frontend-architecture.md](docs/08-frontend-architecture.md)
3. [docs/04-search-architecture.md](docs/04-search-architecture.md)

## 6. 完成定义

Phase 1 完成时，应满足：

1. skill 可以手工维护中文显示名
2. 中文 locale 下默认显示中文名
3. 搜索页、详情页、列表页都已切换到 `preferredDisplayName`
4. 搜索可通过中文名命中 skill
5. 无需外部模型即可完整运行
