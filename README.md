# AgentAI — 多智能体全栈代码生成平台

> 输入一句需求描述，多智能体团队为你自动生成完整的前后端项目，并提供沙箱预览。

---

## 项目简介

AgentAI 是一个基于多智能体协作的全栈代码自动生成平台。用户用自然语言描述系统需求（如"构建一个带权限管理的图书馆系统"），平台将自动驱动 9 个专项 AI 智能体按序执行，依次完成需求分析、架构设计、后端代码生成、前端代码生成、代码审查与自动修复，最终输出一套可直接运行的 Spring Boot + Vue3 全栈项目，并支持一键部署到 E2B 云沙箱进行在线预览。

本项目为毕业设计作品，旨在探索 LLM 驱动的智能化软件工程流程。

---

## 核心功能

| 功能 | 描述 |
|------|------|
| **自然语言生成项目** | 输入中文需求描述，自动生成完整可运行的全栈项目 |
| **PRD & 架构文档生成** | 需求智能体生成规范化产品需求文档，架构智能体输出系统架构与 API 设计 |
| **后端代码生成** | 自动生成 Spring Boot 骨架、RESTful Controller、Service/Entity/DTO 层 |
| **前端代码生成** | 自动生成 Vue3 + Vite 项目骨架、页面视图（Views）、Axios API 封装 |
| **代码审查与自动修复** | Qwen 驱动的多轮代码审查，自动修复缺失文件和逻辑问题（最多 3 轮） |
| **E2B 沙箱预览** | 将生成的前端项目一键部署到 E2B 云沙箱，提供在线预览链接（30 分钟生命周期） |
| **SSE 实时进度推送** | 基于 `fetch + ReadableStream` 的 SSE 流，实时推送各智能体执行进度 |
| **智能体动态开关** | 后台可独立启用/禁用每个智能体，平台自动处理依赖链 |
| **工作流历史** | 每次生成任务均持久化为工作流记录，支持查看执行详情和下载产物 |
| **用户隔离** | 多用户支持，每个用户只能查看和管理自己的工作流与任务 |

---

## 系统架构

```
用户浏览器 (Vue3)
     │  HTTP/SSE
     ▼
Spring Boot 后端 (AgentAi) :8080
     │
     ├── JWT 认证层 (Spring Security)
     │
     ├── AgentOrchestrator（智能体协调器）
     │     ├── RequirementAnalysisService  → DashScope/Qwen
     │     ├── ArchitectAnalysisService    → DashScope/Qwen
     │     ├── BackendSkeletonGenerator   → AnythingLLM
     │     ├── BackendControllerGenerator → AnythingLLM
     │     ├── BackendDomainGenerator     → AnythingLLM
     │     ├── FrontendSkeletonGenerator  → AnythingLLM
     │     ├── FrontendViewsGenerator     → AnythingLLM
     │     ├── FrontendApiGenerator       → AnythingLLM
     │     └── CodeReviewService          → DashScope/Qwen
     │
     ├── Redis（任务状态、SSE 数据缓存，TTL 24h）
     │
     └── MySQL（用户、工作流、步骤、产物持久化）
           │
           └── e2b-proxy-service (Node.js) :3002
                 └── E2B Sandbox（云端 Node.js 容器）
```

### 智能体执行流程

```
用户输入
  │
  ▼
[1] 需求分析智能体 → 生成 PRD 文档
  │
  ▼
[2] 架构设计智能体 → 生成架构文档 + API 定义
  │
  ├──────────────────────────────────────┐
  ▼                                      ▼
[3] 后端骨架生成                       [6] 前端骨架生成
  │                                      │
  ▼                                      ▼
[4] Controller 生成                   [7] Views 页面生成
  │                                      │
  ▼                                      ▼
[5] Service/Entity/DTO 生成           [8] API 接口封装
  │                                      │
  └──────────────┬───────────────────────┘
                 ▼
          [9] 代码审查智能体（最多 3 轮）
                 │
                 ▼
          沙箱部署 / 产物下载
```

---

## 技术栈

### 后端 (AgentAi)

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.7 | 主框架 |
| Spring Security | 6.x | JWT 鉴权 |
| Spring AI Alibaba | 1.1.0.0-M5 | DashScope/Qwen LLM 集成 |
| MyBatis-Plus | 3.5.6 | ORM |
| MySQL | 8.0 | 持久化存储 |
| Redis | - | 任务状态缓存 |
| JJWT | 0.12.6 | JWT 令牌 |
| CommonMark | 0.25.1 | Markdown 解析 |
| Java | 17 | 运行时 |

### 前端 (web/app)

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | 3.5.24 | 前端框架 |
| Vue Router | 5.0.4 | 路由 |
| Pinia | 3.0.4 | 状态管理 |
| Element Plus | 2.13.6 | UI 组件库 |
| Vite | 7.2.4 | 构建工具 |
| Axios | 1.14.0 | HTTP 客户端 |
| Markdown-it | 14.1.0 | Markdown 渲染 |
| Mermaid | 11.12.2 | 架构图渲染 |
| Highlight.js | 11.11.1 | 代码高亮 |

### 沙箱代理 (e2b-proxy-service)

| 技术 | 版本 | 用途 |
|------|------|------|
| Node.js / Express | 4.19.2 | HTTP 服务 |
| E2B Code Interpreter SDK | 2.4.0 | 云沙箱管理 |

---

## 项目结构

```
project/
├── AgentAi/                          # Spring Boot 后端
│   └── src/main/java/top/whyh/agentai/
│       ├── coordinator/
│       │   ├── AgentDefinition.java  # 9 个智能体枚举定义及依赖关系
│       │   ├── AgentOrchestrator.java# 核心协调器，驱动完整生成流程
│       │   └── AgentRegistry.java    # 从 DB 动态加载智能体启用状态
│       ├── service/
│       │   ├── RequirementAnalysisService.java
│       │   ├── ArchitectAnalysisService.java
│       │   ├── CodeReviewService.java
│       │   ├── TaskService.java      # 任务提交与查询
│       │   ├── AsyncTaskRunner.java  # @Async 任务执行器
│       │   ├── WorkflowService.java  # 工作流持久化
│       │   ├── SandboxDeploymentService.java
│       │   └── codegen/              # 代码生成器（6 个专项生成器）
│       ├── controller/
│       │   ├── AgentController.java  # /api/agent/* 核心接口
│       │   ├── WorkflowController.java
│       │   ├── ArtifactController.java
│       │   └── ...
│       ├── entity/                   # 数据库实体
│       ├── security/                 # JWT 认证过滤器
│       └── cache/                    # Redis 工具类
│
├── web/app/                          # Vue3 前端
│   └── src/
│       ├── views/
│       │   ├── Dashboard.vue         # 主页：需求输入 + SSE 进度展示
│       │   ├── WorkflowDetail.vue    # 工作流详情（步骤 + 产物）
│       │   ├── Workflows.vue         # 工作流列表
│       │   ├── ArtifactPreview.vue   # 代码预览 + 沙箱部署
│       │   ├── Agents.vue            # 智能体管理（启用/禁用）
│       │   └── Logs.vue              # 系统日志
│       ├── api/index.js              # 所有 API 调用封装
│       ├── stores/user.js            # 用户状态（Pinia）
│       └── layouts/MainLayout.vue    # 导航布局
│
├── e2b-proxy-service/                # E2B 沙箱代理（Node.js）
│   └── index.js                     # Express 服务，/deploy-frontend 接口
│
└── ai_agent.sql                      # 数据库初始化脚本
```

---

## 快速开始

### 环境要求

- **Java** 17+
- **Node.js** 18+
- **MySQL** 8.0+
- **Redis** 6+
- **Maven** 3.8+
- **通义千问 API Key**（[申请地址](https://dashscope.aliyuncs.com/)）
- **AnythingLLM**（本地部署，用于代码生成）
- **E2B API Key**（可选，仅沙箱预览需要，[申请地址](https://e2b.dev/)）

### 第一步：数据库初始化

```bash
mysql -u root -p < ai_agent.sql
```

### 第二步：后端配置

编辑 `AgentAi/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_agent?useUnicode=true&characterEncoding=utf8
    username: your_db_username
    password: your_db_password
  redis:
    host: localhost
    port: 6379

# 通义千问配置（用于需求分析、架构设计、代码审查）
spring:
  ai:
    dashscope:
      api-key: your_dashscope_api_key
      chat:
        options:
          model: qwen-plus

# AnythingLLM 配置（用于代码生成）
agent:
  ai:
    coder:
      base-url: http://localhost:3001    # AnythingLLM 地址
      api-key: your_anythingllm_api_key
      workspace: your_workspace_slug

# E2B 沙箱代理地址
e2b:
  proxy-url: http://localhost:3002

# 生成代码本地保存路径
code:
  output:
    base-path: E:/AgentAI_Output
```

### 第三步：启动后端

```bash
cd AgentAi
mvn spring-boot:run
# 后端启动于 http://localhost:8080
```

### 第四步：启动 E2B 沙箱代理（可选）

```bash
cd e2b-proxy-service
npm install

# 创建 .env 文件
echo "E2B_API_KEY=your_e2b_api_key" > .env

node index.js
# 代理服务启动于 http://localhost:3002
```

### 第五步：启动前端

```bash
cd web/app
npm install
npm run dev
# 前端启动于 http://localhost:5173
```

### 第六步：访问系统

打开浏览器访问 `http://localhost:5173`，使用 SQL 脚本中的默认账号登录：

> 默认管理员账号请参考 `ai_agent.sql` 中的 `user` 表初始数据。

---

## 核心智能体说明

| 编号 | 智能体 | 职责 | 依赖 | LLM |
|------|--------|------|------|-----|
| 1 | 需求分析智能体 | 将用户自然语言输入转化为结构化 PRD 文档 | 无 | Qwen |
| 2 | 架构设计智能体 | 基于 PRD 设计系统架构、数据模型和 API 接口 | 需求分析 | Qwen |
| 3 | 后端骨架生成智能体 | 生成 Spring Boot 项目结构、pom.xml、配置文件 | 架构设计 | AnythingLLM |
| 4 | Controller 生成智能体 | 生成 RESTful 接口层代码 | 后端骨架 | AnythingLLM |
| 5 | 领域层生成智能体 | 生成 Entity、Service、DTO、Mapper | 后端骨架 | AnythingLLM |
| 6 | 前端骨架生成智能体 | 生成 Vue3 + Vite 项目结构、路由、状态管理 | 架构设计 | AnythingLLM |
| 7 | 前端视图生成智能体 | 生成各业务页面 Vue 组件 | 前端骨架 + Controller | AnythingLLM |
| 8 | 前端 API 生成智能体 | 生成 Axios 接口封装层 | 前端骨架 + Controller | AnythingLLM |
| 9 | 代码审查智能体 | 检查代码完整性、前后端接口一致性，自动修复 | 所有生成智能体 | Qwen |

**代码审查机制**：审查智能体最多执行 3 轮，每轮：
1. 检查 CRITICAL 问题（缺失文件、接口不匹配、编译错误）
2. 自动生成缺失文件
3. 自动修复逻辑问题
4. 超过 3 轮后将剩余问题以 WARNING 形式告知用户

---

## 主要 API 接口

### 任务接口

| 方法 | 路径 | 描述 |
|------|------|------|
| `POST` | `/api/agent/generate` | 提交生成任务，返回 `taskId` |
| `GET` | `/api/agent/task/{taskId}` | 查询任务状态与结果 |
| `GET` | `/api/agent/task/{taskId}/stream` | SSE 实时进度流（需 Bearer Token） |
| `POST` | `/api/agent/task/{taskId}/cancel` | 取消进行中的任务 |
| `POST` | `/api/agent/task/{taskId}/save` | 将生成代码保存到本地磁盘 |
| `POST` | `/api/agent/task/{taskId}/deploy` | 将前端部署到 E2B 沙箱 |

### 工作流接口

| 方法 | 路径 | 描述 |
|------|------|------|
| `GET` | `/api/workflow/page` | 分页查询当前用户的工作流列表 |
| `GET` | `/api/workflow/{id}` | 查询工作流详情（含步骤和产物） |
| `DELETE` | `/api/workflow/{id}` | 删除工作流及关联数据 |

### 认证接口

| 方法 | 路径 | 描述 |
|------|------|------|
| `POST` | `/api/auth/login` | 登录，返回 JWT Token |
| `POST` | `/api/auth/register` | 注册新账号 |
| `GET` | `/api/user/profile` | 获取当前用户信息 |
| `PUT` | `/api/user/profile` | 更新用户信息（头像、邮箱等） |
| `POST` | `/api/user/change-password` | 修改密码 |

---

## 数据库设计

数据库名：`ai_agent`，包含以下核心表：

| 表名 | 描述 |
|------|------|
| `user` | 用户账号（UUID 主键，bcrypt 密码） |
| `agent` | 智能体定义（名称、角色代码、启用状态、效率评分） |
| `workflow_instance` | 工作流执行记录（关联 taskId、userId、状态、最终结果 JSON） |
| `workflow_step` | 工作流各步骤详情（智能体 ID、输出内容、耗时） |
| `artifact` | 生成产物（PRD、架构文档、项目代码 JSON） |
| `requirement` | 用户原始需求记录 |
| `system_log` | 系统操作审计日志 |
| `system_config` | 系统配置键值对 |

---

## 关键设计说明

### SSE 实时推送
前端使用 `fetch + AbortController + ReadableStream` 实现 SSE 订阅，而非浏览器原生 `EventSource`。原因：`EventSource` 不支持自定义请求头，无法携带 `Authorization: Bearer <token>` 进行 JWT 鉴权。

### SecurityContext 线程隔离
Spring Security 的 `SecurityContext` 基于 `ThreadLocal`，后台线程不会继承请求线程的上下文。因此所有需要用户身份的后台操作（SSE 推送线程、`@Async` 任务线程），均在 Controller 请求线程中提前捕获 `userId`，再通过参数显式传递。

### 沙箱部署保障
`AgentOrchestrator` 在代码生成流程结束后，无论 LLM 生成了什么，都会强制覆写 `frontend/vite.config.js`（确保 `host: '0.0.0.0'`、`allowedHosts: 'all'`、`proxy /api`）和修补 `frontend/package.json`（确保正确的 `scripts` 和依赖），防止沙箱部署失败。

### 任务恢复机制
前端通过 `localStorage` 持久化进行中的 `taskId`。用户切换页面或刷新后，页面挂载时自动恢复任务状态：若任务仍在运行则重建 SSE 连接，若已完成则直接展示结果。

---

## License

本项目为毕业设计作品，仅供学习与研究使用。
