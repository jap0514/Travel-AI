# AI Travel - 智能旅游规划平台

  ---

  ## 目录

  - [上手指南](#上手指南)
  - [项目架构](#项目架构)
  - [文件目录说明](#文件目录说明)
  - [部署](#部署)
  - [使用到的框架](#使用到的框架)

  ---

  ## 上手指南

  ### 技术栈

  | 端 | 技术 | 说明 |
  |---|------|------|
  | 后端 | Spring Boot 3.2 + Java 17 | RESTful API 服务 |
  | AI 服务 | FastAPI + Python 3.10+ | LangGraph 多智能体 |
  | 前端 | Vue 3 + 微信小程序 | 用户交互界面 |
  | 存储 | MySQL + Redis + Qdrant | 关系型 + 缓存 + 向量数据库 |
  | 消息队列 | RocketMQ | 订单超时延迟消息 |

  ### 环境要求

  **后端 (Java)**
  - JDK 17+
  - Maven 3.8+
  - MySQL 8.0+
  - Redis 6.0+
  - RocketMQ 4.9+

  **AI 服务 (Python)**
  - Python ≥ 3.10
  - Redis 6.0+
  - Qdrant（向量数据库）

  **前端**
  - Node.js 16+
  - 微信开发者工具

  ### 安装步骤

  ```bash
  # 1. 克隆项目
  git clone https://github.com/your-github-name/AI_travel.git
  cd AI_travel

  # ==================== Java 后端 ====================

  # 2. 配置 MySQL 数据库
  CREATE DATABASE travel_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

  # 3. 修改后端配置
  # 编辑 Travel/AI_travel/travel-all-in-one/src/main/resources/application.yml
  # 配置 MySQL、Redis、RocketMQ 连接信息

  # 4. 构建后端
  cd Travel/AI_travel
  mvn clean package -DskipTests

  # 5. 启动后端
  java -jar travel-all-in-one/target/travel-all-in-one-1.0-SNAPSHOT.jar
  # 后端地址：http://localhost:9999/doc.html

  # ==================== Python AI 服务 ====================

  # 6. 创建虚拟环境
  cd ../../Travel_python/travel_ai_python
  python -m venv .venv
  .venv\Scripts\activate  # Windows
  # source .venv/bin/activate  # Linux/Mac

  # 7. 安装依赖
  pip install -r requirements.txt

  # 8. 配置环境变量
  # 编辑 .env 文件，配置 AI 模型 API、Redis、Qdrant 连接信息

  # 9. 启动外部服务
  # Redis
  redis-server
  # Qdrant (Docker)
  docker run -d -p 6333:6333 -p 6334:6333 qdrant/qdrant

  # 10. 启动 AI 服务
  python web_app.py
  # AI 服务地址：http://localhost:8000/docs

  # ==================== 前端小程序 ====================

  # 11. 安装前端依赖
  cd ../../travel-mp/travel
  npm install

  # 12. 开发模式
  npm run dev

  # 13. 上传小程序（微信开发者工具）
  # 导入项目目录：travel-mp/travel

  ---
  项目架构

  整体架构图

  ┌─────────────────────────────────────────────────────────────────┐
  │                         用户端                                  │
  │            微信小程序 / H5 / Web                                │
  └────────────────────────────┬────────────────────────────────────┘
                               │ HTTP / WebSocket
  ┌────────────────────────────▼────────────────────────────────────┐
  │                      Java 后端 (Spring Boot)                    │
  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐        │
  │  │  接口限流│  │  熔断降级│  │  幂等校验 │  │  链路追踪│        │
  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘        │
  │  ┌──────────────────────────────────────────────────────────┐  │
  │  │                    三级缓存架构                          │   │
  │  │           Caffeine(本地) → Redis → MySQL                 │   │
  │  └──────────────────────────────────────────────────────────┘   │
  └────────────────────────────┬────────────────────────────────────┘
                               │ HTTP / SSE
           ┌───────────────────┼───────────────────┐
           ▼                   ▼                   ▼
  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
  │   Python AI     │ │     Redis       │ │    RocketMQ     │
  │  (FastAPI)      │ │  (会话/缓存)    │ │  (消息队列)      │
  │                 │ └─────────────────┘ └─────────────────┘
  │  LangGraph      │
  │  Multi-Agent    │
  │                 │ ┌─────────────────┐
  │  ┌───────────┐  │ │    Qdrant       │
  │  │ Agent节点 │  │ │  (向量数据库)    │
  │  │ Intent    │  │ └─────────────────┘
  │  │ Research  │
  │  │ Planner   │
  │  │ Critic    │
  │  │ Refiner   │
  │  └───────────┘
  └─────────────────┘

  Java 后端职责

  - RESTful API 提供
  - 业务逻辑处理（订单、酒店、会话）
  - 三级缓存管理
  - 接口限流、熔断、幂等
  - RocketMQ 消息发送
  - 微信登录认证

  Python AI 服务职责

  - LangGraph 多智能体协作
  - 用户意图识别
  - 行程规划生成
  - RAG 知识检索
  - 外部 API 调用（地图、天气）

  ---
  文件目录说明

  AI_travel/
  ├── Travel/                        # Java 后端
  │   ├── AI_travel/
  │   │   ├── travel-all-in-one/     # 主工程
  │   │   │   └── src/main/java/com/travel/
  │   │   │       ├── annotation/     # 自定义注解
  │   │   │       │   ├── @RateLimiter    # 限流
  │   │   │       │   ├── @CircuitBreaker # 熔断
  │   │   │       │   ├── @Idempotent     # 幂等
  │   │   │       │   └── @ThreeTierCache # 三级缓存
  │   │   │       ├── aspect/         # AOP 切面
  │   │   │       ├── config/         # 配置类
  │   │   │       ├── controller/     # 接口
  │   │   │       ├── service/        # 业务逻辑
  │   │   │       ├── entity/         # 实体
  │   │   │       ├── mapper/         # MyBatis Mapper
  │   │   │       ├── mq/             # RocketMQ
  │   │   │       └── util/           # 工具类
  │   │ 
  │   │  
  │   │  
  │   │  
  │   │  
  │   └── pom.xml                    # 父 POM
  │
  ├── Travel_python/                  # Python AI 服务
  │   └── travel_ai_python/
  │       ├── app/
  │       │   ├── agents/             # LangGraph Agent 节点
  │       │   │   ├── Intent_Recognition.py  # 意图识别
  │       │   │   ├── TaskAnalyzer.py        # 任务分析
  │       │   │   ├── Researcher.py          # 信息收集
  │       │   │   ├── Planner.py             # 行程规划
  │       │   │   ├── Critic.py              # 评审
  │       │   │   ├── Refiner.py             # 优化
  │       │   │   └── ParsePlan.py           # 计划解析
  │       │   ├── config/            # 配置
  │       │   ├── service/           # 服务层
  │       │   ├── tools/             # 工具
  │       │   ├── web/               # API 路由
  │       │   └── web_app.py         # 入口
  │       ├── requirements.txt        # 依赖
  │       └── README.md
  │
  └── travel-mp/                      # 前端小程序
      └── travel/
          ├── src/                   # 源代码
          ├── dist/                  # 构建输出
          ├── package.json
          └── README.md

  ---
  部署

  暂无

  ---
  使用到的框架

  ┌───────────┬─────────────────────────┬───────────────┐
  │    端     │          框架           │     用途      │
  ├───────────┼─────────────────────────┼───────────────┤
  │ Java 后端 │ Spring Boot 3.2         │ Web 框架      │
  ├───────────┼─────────────────────────┼───────────────┤
  │           │ MyBatis Plus 3.5.5      │ ORM           │
  ├───────────┼─────────────────────────┼───────────────┤
  │           │ Caffeine 3.1.8          │ 本地缓存      │
  ├───────────┼─────────────────────────┼───────────────┤
  │           │ Redis + Redisson 3.27.2 │ 分布式缓存/锁 │
  ├───────────┼─────────────────────────┼───────────────┤
  │           │ Sentinel 1.8.8          │ 限流熔断      │
  ├───────────┼─────────────────────────┼───────────────┤
  │           │ RocketMQ 4.9.11         │ 消息队列      │
  ├───────────┼─────────────────────────┼───────────────┤
  │           │ Micrometer + Prometheus │ 监控          │
  ├───────────┼─────────────────────────┼───────────────┤
  │ Python AI │ FastAPI                 │ Web 框架      │
  ├───────────┼─────────────────────────┼───────────────┤
  │           │ LangGraph               │ 多智能体框架  │
  ├───────────┼─────────────────────────┼───────────────┤
  │           │ LangChain               │ LLM 集成      │
  ├───────────┼─────────────────────────┼───────────────┤
  │           │ Qdrant                  │ 向量数据库    │
  ├───────────┼─────────────────────────┼───────────────┤
  │           │ Redis                   │ 会话存储      │
  ├───────────┼─────────────────────────┼───────────────┤
  │ 前端      │ Vue 3                   │ 框架          │
  ├───────────┼─────────────────────────┼───────────────┤
  │           │ 微信小程序              │ 端            │
  └───────────┴─────────────────────────┴───────────────┘
  Java 后端
  - Spring Boot (https://spring.io/projects/spring-boot) - Web 框架
  - MyBatis Plus (https://baomidou.com/) - ORM 框架
  - Sentinel (https://sentinelguard.io/) - 限流熔断
  - RocketMQ (https://rocketmq.apache.org/) - 消息队列
  - Caffeine (https://github.com/ben-manes/caffeine) - 本地缓存
  - Redisson (https://redisson.org/) - 分布式锁

  Python AI
  - FastAPI (https://fastapi.tiangolo.com/) - Web 框架
  - LangGraph (https://langchain-ai.github.io/langgraph/) - 多智能体
  - LangChain (https://www.langchain.com/) - LLM 应用框架
  - Qdrant (https://qdrant.tech/) - 向量数据库

  前端
  - Vue.js (https://vuejs.org/) - 渐进式框架
  - Vite (https://vitejs.dev/) - 构建工具
