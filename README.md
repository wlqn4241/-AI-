# 暖心 · 心理健康 AI 助手

基于 **Spring Boot 3 + MySQL 8 + 智谱 GLM-4-Flash** 的心理健康 Web 应用。前后端一体：后端提供 REST + SSE 流式接口，前端是单个 `index.html`（无构建步骤）。

## 功能

- **账号体系**：注册 / 登录，JWT 鉴权（`AuthInterceptor` 拦截 `/api/**`，登录注册放行）
- **AI 对话**：流式输出（SSE），多会话管理，自动带上下文（`history-limit` 控制轮数）
- **危机干预**：命中自伤/自杀等关键词时，`SafetyService` 固定前置一段求助提示并附热线
- **心理测评**：PHQ-9 抑郁、GAD-7 焦虑、PSS-10 压力三份量表，按选项分值计分、自动定级、生成文字报告 + AI 个性化建议，危机条目单独标记
- **情绪记录**：每日心情分 + 标签 + 备注，配套折线图和统计

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3.3.5 / Spring WebFlux（WebClient 流式）/ Spring Data JPA |
| 数据库 | MySQL 8，`ddl-auto=update` 自动建表，首次启动由 `DataInitializer` 灌入三份量表 |
| 大模型 | 智谱 GLM-4-Flash（OpenAI 兼容接口，SSE 流式） |
| 前端 | 原生 HTML + CSS + JS 单文件，ECharts 画图 |

## 本地运行

### 1. 准备数据库

```sql
CREATE DATABASE psych_ai DEFAULT CHARACTER SET utf8mb4;
```

也可以直接用 `db/schema.sql` 建表（可选，JPA 也会自动建）：

```bash
mysql -uroot -p < db/schema.sql
mysql -uroot -p psych_ai < db/seed.sql   # 导入三份量表数据（可选，DataInitializer 也会灌）
```

### 2. 配置密钥

仓库**不含**任何真实密钥。默认激活 `local` profile，从项目根目录的 `config/application-local.yml` 读取：

```yaml
# config/application-local.yml
spring:
  datasource:
    password: 你的数据库密码
deepseek:
  api-key: 你的智谱 API Key
```

`config/` 与 `application-*.yml` 已在 `.gitignore` 中排除。

不想用配置文件也可以直接给环境变量：

```bash
export DB_PASSWORD=your_password
export ZHIPU_API_KEY=your_api_key
export SPRING_PROFILES_ACTIVE=none   # 关闭 local profile
```

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `DB_USERNAME` / `DB_PASSWORD` | `root` / 空 | 数据库账号 |
| `ZHIPU_API_KEY` | 空 | 智谱 API Key，必填 |
| `ZHIPU_BASE_URL` | `https://open.bigmodel.cn/api/paas/v4` | 兼容 OpenAI 的接口地址 |
| `ZHIPU_MODEL` | `glm-4-flash` | 模型名 |
| `JWT_SECRET` | 内置开发用默认值 | 生产务必改 |
| `SPRING_PROFILES_ACTIVE` | `local` | 激活的 profile |

### 3. 启动

```bash
mvn spring-boot:run
```

浏览器打开 <http://localhost:8080> 。**注意要在项目根目录启动**，否则读不到 `config/application-local.yml`。

## 目录结构

```
src/main/java/com/psych/ai/
├── common/       统一响应体、异常处理、JWT 工具
├── config/       拦截器、WebClient、数据初始化
├── controller/   auth / chat / assessment / emotion
├── entity/       User、ChatSession、ChatMessage、EmotionRecord、AssessmentScale、AssessmentRecord
├── repository/   Spring Data JPA
└── service/      AuthService、ChatService、DeepSeekService、AssessmentService、EmotionService、SafetyService
src/main/resources/
├── application.yml
└── static/index.html   前端单文件（登录/对话/测评/情绪/报告五个视图）
db/
├── schema.sql    建表语句
└── seed.sql      三份量表的初始数据
```

## 接口一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register`、`/api/auth/login` | 注册 / 登录，返回 JWT |
| GET | `/api/chat/sessions` | 会话列表 |
| POST | `/api/chat/sessions` | 新建会话 |
| GET | `/api/chat/sessions/{id}/messages` | 历史消息 |
| POST | `/api/chat/sessions/{id}/stream` | **流式对话（SSE）** |
| GET | `/api/assessment/scales` | 量表列表 |
| GET | `/api/assessment/scales/{code}` | 量表详情（含 `questionsJson`） |
| POST | `/api/assessment/scales/{code}/submit` | 提交答案，返回得分 / 等级 / 报告 |
| GET | `/api/assessment/records` | 测评历史 |
| GET | `/api/emotion/...` | 情绪记录与统计 |

## 两处踩过的坑（已修复，留档）

1. **测评选项显示成 `[object Object]`**：后端 `questionsJson` 里题目选项是对象 `{"label":"完全不会","score":0}`，前端模板直接 `${opt}` 插值。已改为 `optLabel()` 按字段名兜底取文字。
2. **AI 回复只有兜底文案、看不到回答**：`WebClient` 的 `ServerSentEventHttpMessageReader` 已经把 SSE 解包成纯 JSON 载荷（**没有 `data:` 前缀**），后端 `extractStreamContent()` 却只保留 `startsWith("data:")` 的行，导致所有分片被过滤掉、Flux 空完成。已改为宽容解析；前端 SSE 拼接也改为按行处理、空载荷当换行（否则多行回答会被吞掉）。

## 免责声明

本项目为个人学习/毕设作品，量表结果与 AI 输出**不构成医疗诊断**。如遇心理危机，请拨打全国心理援助热线 **12356** 或希望 24 热线 **400-161-9995**。
