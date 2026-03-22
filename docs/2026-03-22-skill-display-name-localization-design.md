# Skill 中文显示名能力设计

## 1. 背景

当前 SkillHub 的 skill 展示名称来自 `SKILL.md` frontmatter 中的 `name` 字段。

现状链路如下：

1. 发布时解析 `SKILL.md`
2. `name` 被用于生成 `slug`
3. 原始 `name` 写入 `skill.display_name`
4. 搜索、列表、详情、卡片均直接展示 `skill.display_name`

这带来两个问题：

1. 大量 skill 的 `name` 以英文为主，中文用户浏览成本高
2. 当前没有 skill 级名称本地化模型，无法表达“规范名”和“面向中文用户的显示名”之间的差异

用户目标是新增一个可选能力：

1. 发布 skill 时可选择性填写中文显示名
2. 若原始名称不是中文且用户未填写，则可通过大模型自动翻译并回填
3. 若用户后续修改中文显示名，则始终以用户修改为准，不再被模型覆盖

## 2. 现状评估

### 2.1 已有基础

代码库已经具备适合承接该能力的几项基础设施：

1. skill 主模型已有 `display_name` 字段，且展示链路统一走该字段
2. 后端已存在成熟的 locale fallback 模式，典型例子是 `label_translation` + `LabelLocalizationService`
3. 搜索层已有独立的搜索投影和重建机制，可以在不改领域主模型查询路径的前提下扩展搜索文本
4. 发布流程、详情页、搜索页、卡片组件都已集中走固定 DTO，便于统一补充“优先显示名”字段

### 2.2 当前缺口

当前缺的不是单个前端字段，而是一整套本地化能力：

1. 没有 skill 名称翻译存储模型
2. 没有发布时承载“中文显示名”的输入参数
3. 没有名称翻译的优先级与回退规则
4. 没有模型翻译异步回填机制
5. 没有名称翻译变更后的搜索重建触发链路

### 2.3 可行性结论

该能力可行。

但不建议把“大模型翻译”直接塞进同步发布主流程。原因如下：

1. 当前仓库没有现成的通用翻译服务或外部大模型调用编排
2. 发布链路是核心写路径，不应被外部模型延迟、配额、失败率绑死
3. 用户修改优先于模型结果，天然更适合采用“人工输入优先、模型异步补全”的模式

因此，推荐方案是：

1. 保留 `skill.display_name` 作为规范名和兼容字段
2. 新增 skill 名称翻译表承载 locale 化显示名
3. 前端展示改为优先显示 locale 命中的翻译名，否则回退到规范名
4. 自动翻译采用异步任务回填，不阻塞发布
5. 用户一旦手工填写或修改，后续模型结果不得覆盖

## 3. 设计目标

### 3.1 目标

1. 中文用户默认优先看到中文 skill 名称
2. 不破坏现有 slug、包元数据、兼容 API 的语义稳定性
3. 用户填写的中文名称优先级最高
4. 自动翻译可插拔、可关闭、可重试
5. 搜索支持通过中文显示名命中英文 skill

### 3.2 非目标

本次不直接扩展以下能力：

1. 不翻译 slug
2. 不把 skill 全量文档正文纳入机器翻译
3. 不一次性把所有 skill 元数据字段都做成多语言
4. 不在第一阶段支持任意语言的完整多语运营管理界面
5. 不把自动翻译能力暴露为同步必选依赖

## 4. 方案对比

### 4.1 方案 A：在 `skill` 表直接增加 `display_name_zh`

优点：

1. 改动最小
2. 查询简单
3. 前端接入快

缺点：

1. 把语言硬编码进主表，后续扩展更多 locale 成本高
2. 难以表达机器回填、用户覆盖、陈旧状态等元数据
3. 与现有 label translation 模型风格不一致

结论：不推荐作为长期方案。

### 4.2 方案 B：新增 skill 名称翻译表

优点：

1. 与现有 `label_translation` 模式一致
2. 易于支持 locale fallback
3. 可记录来源、状态、模型信息、覆盖关系
4. 后续可扩展更多 locale，而不需要反复改主表

缺点：

1. 查询需要额外 join 或单独查询聚合
2. 发布、详情、搜索、编辑都要接入翻译表

结论：推荐。

## 5. 总体方案

### 5.1 核心原则

1. `skill.display_name` 继续作为规范名
2. skill 翻译名通过独立表管理
3. 展示层新增“优先显示名”概念，不直接改写规范名
4. 用户手工输入永远优先于机器翻译
5. 机器翻译只负责补全缺失值，不负责覆盖人工值

### 5.2 命名建议

后端与数据库层建议使用 `translation` 命名，而不是直接叫 `zhName`：

1. 实体：`SkillTranslation`
2. 表：`skill_translation`
3. 服务：`SkillLocalizationService`

产品界面文案可以显示为：

1. 中文显示名
2. 中文名称
3. 面向中文用户的显示名称

## 6. 数据模型设计

### 6.1 保持现有主模型不变

继续保留：

1. `skill.slug`
2. `skill.display_name`
3. `skill.summary`

语义保持：

1. `slug` 是安装与寻址标识
2. `display_name` 是 skill 规范名，来源于包元数据 `name`
3. 翻译名属于展示层增强，不反写规范名

### 6.2 新增 `skill_translation`

建议新增表：

```sql
CREATE TABLE skill_translation (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL,
    locale VARCHAR(32) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_display_name VARCHAR(200),
    source_hash VARCHAR(64),
    provider_code VARCHAR(64),
    model_name VARCHAR(128),
    stale BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(128),
    updated_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_skill_translation_skill_locale UNIQUE (skill_id, locale)
);

CREATE INDEX idx_skill_translation_skill_id ON skill_translation(skill_id);
CREATE INDEX idx_skill_translation_locale ON skill_translation(locale);
```

### 6.3 字段语义

#### `locale`

一期主目标为：

1. `zh-CN`

实现上允许扩展：

1. `zh`
2. `en`
3. 其它 locale

#### `source_type`

建议值：

1. `USER`
2. `MACHINE`
3. `SYSTEM`

语义：

1. `USER`：用户在发布或后续编辑时手工填写
2. `MACHINE`：由大模型自动翻译生成
3. `SYSTEM`：预留给批量导入、迁移或后台治理写入

#### `source_display_name`

记录该翻译生成时依赖的规范名快照。例如：

1. 原始规范名是 `Code Review Assistant`
2. 机器生成中文名为 `代码审查助手`

如果后续规范名改成 `AI Code Review Assistant`，则可以判断旧翻译是否陈旧。

#### `source_hash`

保存规范名的归一化 hash，用于快速判断是否需要重译。

#### `stale`

标识当前翻译是否因规范名变化而陈旧。

规则建议：

1. `USER` 来源默认 `stale=false`
2. `MACHINE` 来源若规范名变化，则标记为 `stale=true`
3. `USER` 翻译即使规范名变化，也不自动覆盖，只在后台提示可能需要复核

## 7. 展示与回退规则

### 7.1 读路径返回字段

不建议直接改变现有 `displayName` 字段语义。

推荐新增字段：

1. `preferredDisplayName`
2. `canonicalDisplayName`

其中：

1. `canonicalDisplayName` = 当前 `skill.display_name`
2. `preferredDisplayName` = 基于 locale fallback 解析后的展示名

兼容策略：

1. 现有 `displayName` 可继续保留并等于 `canonicalDisplayName`
2. 前端逐步切换为优先使用 `preferredDisplayName`

这样不会破坏现有 API 消费方对 `displayName` 的稳定预期。

### 7.2 locale fallback

参考现有 `LabelLocalizationService`，skill 名称采用类似回退链：

1. 精确 locale，例如 `zh-CN`
2. 语言级 locale，例如 `zh`
3. 规范名 `canonicalDisplayName`

注意：

1. 不要求存在 `en` 翻译记录
2. 英文本身由 `skill.display_name` 承担

### 7.3 前端显示策略

以下页面默认展示 `preferredDisplayName`：

1. 首页 skill card
2. 搜索结果列表
3. namespace skill 列表
4. 收藏列表
5. 我的 skill 列表
6. skill 详情页标题
7. 各种确认弹窗中的 skill 名称

管理场景中同时展示：

1. 中文显示名
2. 规范名
3. 来源类型（手工 / 自动）

这样可避免用户误以为 slug 或包里的原始 `name` 已被修改。

## 8. 发布流程设计

### 8.1 发布接口扩展

当前发布接口：

1. `POST /api/web/skills/{namespace}/publish`
2. multipart 字段包含 `file`、`visibility`、`label`

建议新增可选参数：

1. `localizedDisplayNameZhCn`

一期先聚焦中文展示名即可。

后续如需多 locale，再升级为：

1. `localizedDisplayName[zh-CN]`
2. 或 `translationsJson`

但一期不建议过早把发布请求做成复杂多语言结构。

### 8.2 发布写入规则

发布成功后执行如下规则：

1. 永远更新 `skill.display_name = metadata.name`
2. 如果请求带了 `localizedDisplayNameZhCn` 且非空：
   1. upsert `skill_translation(skill_id, 'zh-CN')`
   2. `source_type = USER`
   3. `stale = false`
3. 如果请求未带中文显示名：
   1. 保留已有 `USER` 翻译，不做覆盖
   2. 若不存在 `zh-CN` 翻译且规范名不是中文，则投递机器翻译任务
   3. 若存在 `MACHINE` 翻译但规范名已变化，则将其标记为 `stale=true` 并重新投递任务

### 8.3 对重复发布的处理

当 skill 已存在、用户再次发布新版本时：

1. 不因“未填写中文显示名”而清空已有人工翻译
2. 人工填写新值时，覆盖旧翻译并写成 `USER`
3. 仅 `MACHINE` 翻译会被重算

这符合“用户如修改则以用户修改为准”的要求。

## 9. 后续编辑流程设计

仅在发布时填写不够，需要允许后续修改。

### 9.1 新增管理接口

建议新增：

1. `GET /api/web/skills/{namespace}/{slug}/translations`
2. `PUT /api/web/skills/{namespace}/{slug}/translations/{locale}`
3. `DELETE /api/web/skills/{namespace}/{slug}/translations/{locale}`

返回结构建议包含：

1. `locale`
2. `displayName`
3. `sourceType`
4. `stale`
5. `updatedAt`

### 9.2 权限模型

建议与 skill 生命周期管理权限保持一致：

1. skill owner 可编辑
2. namespace ADMIN / OWNER 可编辑
3. 其它用户只读

### 9.3 删除翻译后的行为

当用户删除 `zh-CN` 翻译时：

1. 前台展示立即回退到规范名
2. 若规范名不是中文，系统可重新投递自动翻译任务
3. 如果产品担心自动补回会与用户删除意图冲突，可增加 `translationOptOut` 标记

推荐加一个轻量控制字段：

1. `auto_translate_disabled` 挂在 `skill_translation` 删除墓碑表或 skill 扩展字段中

一期若想控制范围，可先不做删除后自动补回，只做手工重新生成按钮。

## 10. 自动翻译回填设计

### 10.1 为什么必须异步

不建议在同步发布里直接调用模型：

1. 发布耗时会被外部模型放大
2. 网络失败会污染核心写路径
3. 模型调用通常需要限流、超时、重试和成本控制

因此推荐事件驱动 + durable task。

### 10.2 任务表建议

新增表：

```sql
CREATE TABLE skill_translation_task (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL,
    locale VARCHAR(32) NOT NULL,
    source_display_name VARCHAR(200) NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_run_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    provider_code VARCHAR(64),
    model_name VARCHAR(128),
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_skill_translation_task_status_next_run
    ON skill_translation_task(status, next_run_at);
```

状态建议：

1. `PENDING`
2. `PROCESSING`
3. `SUCCEEDED`
4. `FAILED`
5. `SKIPPED`

### 10.3 自动翻译触发条件

满足以下条件才创建任务：

1. 目标 locale 为 `zh-CN`
2. skill 没有 `USER` 类型的 `zh-CN` 翻译
3. 规范名检测为非中文
4. 当前没有同 `skill_id + locale + source_hash` 的未完成任务

### 10.4 中文检测规则

建议使用轻量规则，不依赖模型：

1. 若名称包含明显 CJK 字符，判定为中文或中英混写，不触发自动翻译
2. 若完全为 ASCII 且含字母，则判定为非中文，可触发自动翻译

可接受误差：

1. 中英混写名称不强制翻译
2. 品牌名或技术名保留原词是可接受的

### 10.5 模型输出写入规则

任务执行成功后：

1. 若目标 skill 仍无 `USER` 翻译
2. 且现有翻译不存在或为 `MACHINE`
3. 且 `source_hash` 仍匹配当前规范名

则 upsert `skill_translation`：

1. `source_type = MACHINE`
2. 写入 `provider_code`、`model_name`
3. `stale = false`

若在任务排队期间用户已手工修改，则任务应直接 `SKIPPED`，不得覆盖。

### 10.6 Prompt 与约束建议

模型提示建议强调：

1. 保留产品名、品牌名、缩写名
2. 不翻译 slug
3. 不臆造功能范围
4. 输出短名称，不输出解释句

例如目标：

1. `Code Review Assistant` -> `代码审查助手`
2. `RAG Knowledge Base` -> `RAG 知识库`
3. `OpenAI Agent SDK` -> `OpenAI Agent SDK`

### 10.7 Provider 设计

推荐抽象接口：

```java
public interface SkillNameTranslationProvider {
    TranslationResult translateDisplayName(String sourceText, String targetLocale);
}
```

通过配置切换具体实现：

1. `noop`
2. `openai`
3. `azure-openai`
4. `dashscope`

默认建议：

1. 未配置 provider 时，仅支持手工填写，不自动翻译

## 11. 搜索设计

### 11.1 搜索目标

用户使用中文关键词时，应能命中原始名称为英文、但已有中文显示名的 skill。

### 11.2 搜索文档扩展

当前 `skill_search_document.title` 只存规范名。

建议保持：

1. `title` 继续写规范名

并在搜索重建时将翻译名注入：

1. `keywords`
2. `search_text`

即：

1. `title = canonicalDisplayName`
2. `keywords += all active translation display names`
3. `search_text += all active translation display names`

这样无需改变搜索表主结构，就能支持中文命中。

### 11.3 搜索重建触发点

以下事件需要重建 skill 搜索文档：

1. 发布导致规范名变更
2. 用户手工新增或修改翻译
3. 机器翻译回填成功
4. 翻译被删除

建议新增 `SkillTranslationChangedEvent`，由应用服务层触发，搜索层监听后执行 `rebuildBySkill(skillId)`。

## 12. API 设计建议

### 12.1 列表/搜索/详情返回结构扩展

建议在以下 DTO 中新增字段：

1. `SkillSummaryResponse`
2. `SkillDetailResponse`
3. 我的 skill 列表 DTO
4. 收藏列表 DTO

建议新增字段：

```json
{
  "displayName": "Code Review Assistant",
  "preferredDisplayName": "代码审查助手",
  "canonicalDisplayName": "Code Review Assistant"
}
```

其中：

1. `displayName` 保持兼容，可等于 `canonicalDisplayName`
2. `preferredDisplayName` 供前端默认展示
3. `canonicalDisplayName` 供管理页或调试场景显式展示

### 12.2 owner 视角附加信息

在 skill 详情或编辑接口中，建议额外返回：

```json
{
  "nameTranslations": [
    {
      "locale": "zh-CN",
      "displayName": "代码审查助手",
      "sourceType": "MACHINE",
      "stale": false,
      "updatedAt": "2026-03-22T12:00:00Z"
    }
  ]
}
```

普通公开列表不必返回全量翻译明细。

## 13. 前端交互设计

### 13.1 发布页

在现有发布页中增加一个可选输入：

1. 字段名：中文显示名
2. 提示：可选。不填时，系统会在需要时自动生成中文显示名

交互规则：

1. 不必强制填写
2. 不因留空而阻止发布
3. 对已存在 skill 的重复发布，留空不等于清空已有中文名

### 13.2 skill 详情页管理区

当用户具备管理权限时，在详情页或 dashboard skill 管理页增加：

1. 当前中文显示名
2. 来源标识：手工 / 自动
3. 编辑按钮
4. 如为 `MACHINE` 且 `stale=true`，展示“需复核”提示

### 13.3 展示文案

建议：

1. 面向普通用户，仅显示 `preferredDisplayName`
2. 面向 owner，在管理面板显示“规范名：xxx”

避免把两个名字同时放在卡片主视图里，降低阅读噪音。

## 14. 权限与审计

### 14.1 权限

编辑 skill 名称翻译建议复用 lifecycle 管理权限：

1. skill owner
2. namespace ADMIN
3. namespace OWNER

### 14.2 审计

建议为以下动作记审计日志：

1. 手工新增翻译
2. 手工修改翻译
3. 手工删除翻译
4. 机器回填翻译成功
5. 机器回填被用户覆盖

## 15. 迁移与回填

### 15.1 数据迁移

上线时新增表：

1. `skill_translation`
2. `skill_translation_task`

主表 `skill` 无需破坏性改动。

### 15.2 存量 skill 回填

对现有 ACTIVE skill 执行一次性扫描：

1. 若名称已是中文，跳过
2. 若已有 `USER` 类型 `zh-CN` 翻译，跳过
3. 若没有中文翻译，则投递自动翻译任务

### 15.3 灰度策略

建议分三步灰度：

1. 先上线数据模型 + 手工填写 + 展示回退
2. 再上线异步翻译任务和后台 worker
3. 最后开启存量批量回填

## 16. 分阶段实施计划

### Phase 1：手工中文显示名能力

目标：先把核心产品能力做对，不依赖模型。

后端：

1. 新增 `skill_translation` 表与实体
2. 新增 `SkillLocalizationService`
3. 扩展 skill 查询 DTO，返回 `preferredDisplayName`
4. 扩展发布接口，支持 `localizedDisplayNameZhCn`
5. 新增 skill 翻译管理接口
6. 为翻译变更接入审计

前端：

1. 发布页增加“中文显示名”可选输入
2. 搜索、首页、详情、namespace、我的 skill 卡片切换为使用 `preferredDisplayName`
3. 管理页增加翻译编辑入口

测试：

1. 发布时填写中文显示名
2. 发布时不填写，展示回退到规范名
3. 重复发布留空不清空已有手工翻译
4. 详情页和搜索页默认显示 `preferredDisplayName`

### Phase 2：自动翻译任务

目标：补齐“未填写时自动回填”。

后端：

1. 新增 `skill_translation_task` 表
2. 新增 `SkillNameTranslationProvider` SPI
3. 新增调度任务 / worker
4. 发布后按条件投递翻译任务
5. 机器回填成功后触发搜索重建

测试：

1. 非中文规范名生成任务
2. 中文规范名不生成任务
3. 用户已手工填写时任务跳过
4. 任务失败重试与最终失败记录

### Phase 3：存量回填与治理增强

目标：让存量数据获得中文显示名，同时控制质量。

后端：

1. 批量扫描现有 skill
2. 增加 `stale` 标记和复核逻辑
3. 增加后台筛选：自动翻译、待复核、失败任务

前端：

1. 后台治理视图展示机器翻译来源与更新时间
2. 支持一键重试翻译

## 17. 主要风险与应对

### 风险 1：模型结果质量不稳定

应对：

1. 模型只做补全，不做覆盖
2. 提供手工编辑入口
3. 记录 provider/model/source，便于追溯

### 风险 2：规范名变化导致机器翻译过期

应对：

1. 保存 `source_hash`
2. 仅对 `MACHINE` 翻译自动标记 `stale`
3. 用户翻译永不自动覆盖

### 风险 3：搜索结果与详情展示不一致

应对：

1. 翻译变更后立即触发 `rebuildBySkill`
2. 在搜索投影中补入翻译名

### 风险 4：把外部模型依赖引入发布主路径

应对：

1. 绝不在同步发布里调用翻译模型
2. 全部通过异步任务完成

## 18. 推荐结论

该能力可行，且值得做。

推荐的落地顺序不是“一步到位把模型翻译塞进发布”，而是：

1. 先建设 `skill_translation` 模型与 `preferredDisplayName` 展示链路
2. 先让用户可在发布时手工填写中文显示名，并可后续编辑
3. 再接入异步大模型翻译任务，为未填写的英文 skill 自动回填中文名
4. 最后做存量批量回填和治理

这样既满足产品目标，也能把工程风险控制在现有 SkillHub 架构可承受范围内。
