# Travel AI - 智能旅行规划系统

基于 LangGraph 多智能体协作的旅行规划助手，能够理解用户意图、收集目的地信息、生成个性化行程方案。

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                      前端 (Web UI)                           │
│                   http://localhost:8000                     │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  FastAPI 后端服务                            │
│                     web_app.py                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │   /chat     │  │  /chat/stream│  │   /route/*         │ │
│  │  (同步聊天)   │  │   (SSE流式)  │  │   (地图路线API)      │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  LangGraph      │ │  Redis          │ │  Mem0/Qdrant    │
│  Multi-Agent    │ │  (会话/Profile)  │ │  (记忆存储)       │
└─────────────────┘ └─────────────────┘ └─────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Agent 节点                                │
│  Intent_Recognition → Supervisor → TaskAnalyzer             │
│       ↓                    ↓              ↓                 │
│    GeneralQA        Researcher      Planner                 │
│                         ↓              ↓                    │
│                      Critic ←──────────┘                    │
│                         ↓                                   │
│                      Refiner                                │
│                         ↓                                   │
│                   FinalOptimizer                            │
│                         ↓                                   │
│                    ParsePlan                                │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   MCP Tools (外部工具)                        │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐ │
│  │ RAG 知识库    │ │  高德地图API   │ │  天气/酒店/航班查询      ││
│  │ (Qdrant)     │ │              │ │                      │ │
│  └──────────────┘ └──────────────┘ └──────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## 环境依赖

### 核心依赖

| 依赖 | 版本 | 说明 |
|------|------|------|
| Python | ≥ 3.10 | 推荐 3.11+ |
| FastAPI | ≥ 0.110.0 | Web 框架 |
| Uvicorn | ≥ 0.27.0 | ASGI 服务器 |
| LangGraph | 最新 | 多智能体框架 |
| LangChain | 最新 | LLM 集成 |
| langchain-openai | 最新 | OpenAI 兼容接口 |
| langchain-mcp-adapters | ≥ 0.2.2 | MCP 协议适配 |

### 数据存储

| 依赖 | 说明 |
|------|------|
| Redis | 会话上下文、用户 Profile 存储 |
| Qdrant | 向量数据库，用于 RAG 知识检索 |
| Mem0 | 长期记忆管理 |

### 其他依赖

```
rocketmq-python-client==5.1.1
python-dotenv==1.0.0
loguru==0.7.2
requests==2.31.0
PyJWT==2.8.0
pytest>=7.4.0
pytest-asyncio>=0.21.0
sentence_transformers==5.5.1
```

## 环境配置

### 1. 克隆项目

```bash
cd D:\毕业设计\Travel_python\travel_ai_python
```

### 2. 创建虚拟环境

```bash
# Windows
python -m venv .venv
.venv\Scripts\activate

# Linux/Mac
python -m venv .venv
source .venv/bin/activate
```

### 3. 安装依赖

```bash
pip install -r requirements.txt
```

### 4. 配置环境变量

复制 `.env.example` 为 `.env`（如果存在），或直接编辑 `.env` 文件：

```bash
# AI 模型配置（必填）
AI_MODEL_API_URL=https://api.deepseek.com
AI_MODEL_API_KEY=your-api-key
AI_MODEL_NAME=deepseek-v4-flash

# 向量数据库（必填）
QDRANT_HOST=localhost
QDRANT_PORT=6333

# Redis（必填）
REDIS_HOST=localhost
REDIS_PORT=6379

# 高德地图（地图功能必填）
GAODE_MAP_API_KEY=your-gaode-api-key

# 心知天气（天气功能可选）
XINZHI_WEATHER_API_KEY=your-xinzhi-api-key
```

### 5. 启动外部服务

```bash
# Redis
redis-server

# Qdrant（Docker）
docker run -p 6333:6333 -p 6334:6333 qdrant/qdrant
```

## 启动服务

### 方式一：直接运行

```bash
python web_app.py
```

### 方式二：使用 Uvicorn

```bash
uvicorn web_app:app --reload --host 0.0.0.0 --port 8000
```

### 方式三：后台运行

```bash
nohup python web_app.py > app.log 2>&1 &
```

服务启动后访问：
- 前端页面：http://localhost:8000
- API 文档：http://localhost:8000/docs

## 项目结构

```
travel_ai_python/
├── app/
│   ├── agents/              # Agent 节点实现
│   │   ├── base.py          # LLM 和工具初始化
│   │   ├── researcher.py    # 研究节点
│   │   ├── planner.py       # 规划节点
│   │   ├── critic.py        # 评审节点
│   │   ├── refiner.py       # 优化节点
│   │   ├── task_analyzer.py # 任务分析节点
│   │   └── parse_plan.py    # 计划解析节点
│   ├── config/
│   │   ├── settings.py      # 配置管理
│   │   └── logger.py        # 日志配置
│   ├── model/
│   │   ├── task_model.py    # 任务数据模型
│   │   └── plan_model.py    # 计划数据模型
│   ├── mcp_servers/         # MCP 服务器配置
│   ├── service/
│   │   ├── ai_service.py    # AI 服务
│   │   ├── multi_agent_travel.py  # 多智能体主流程
│   │   └── profile_worker.py      # Profile 更新 Worker
│   ├── tools/
│   │   └── mcp_tools.py     # MCP 工具封装
│   ├── utils/
│   │   ├── redis_client.py  # Redis 客户端
│   │   ├── mem0_client.py   # Mem0 客户端
│   │   ├── gaode_map.py     # 高德地图工具
│   │   ├── sse_publisher.py # SSE 事件发布
│   │   └── agent_callback.py # Agent 回调处理
│   ├── web/
│   │   ├── api.py           # API 路由
│   │   ├── route_api.py     # 地图路线 API
│   │   └── static/          # 前端静态文件
│   └── web_app.py           # 应用入口
├── .env                     # 环境变量配置
├── requirements.txt         # Python 依赖
└── README.md                # 本文件
```

## Agent 节点说明

| 节点 | 功能 | 输入 | 输出 |
|------|------|------|------|
| Intent_Recognition | 意图识别 | 用户消息 | intent: plan/qa |
| TaskAnalyzer | 任务参数提取 | 用户消息 | destination, days, budget, pace |
| Researcher | 收集目的地信息 | 任务参数 | 研究报告（景点、美食等） |
| Planner | 生成行程草案 | 研究报告 | 行程草稿 |
| Critic | 评估行程质量 | 行程草稿 | 评分 + 改进意见 |
| Refiner | 根据评审优化 | 评审意见 | 优化后的草稿 |
| FinalOptimizer | 最终润色 | 评分最高的草稿 | 最终行程 |
| ParsePlan | 结构化解析 | Markdown 行程 | 结构化 TravelPlan |

## API 接口

### 聊天接口

```
POST /api/chat/stream
Content-Type: application/json

{
    "content": "我想去北京玩3天",
    "user_id": 1,
    "session_id": 123456
}
```

响应：SSE 流式事件
- `init`: 初始化事件，包含 trace_id
- `node_started`: 节点开始执行
- `node_finished`: 节点执行完成
- `agent_complete`: 整体完成

### 地图路线接口

```
POST /api/route/map
Content-Type: application/json

{
    "start_address": "广州市天河区",
    "destinations": ["广州塔", "陈家祠", "白云山"],
    "travel_mode": "driving"
}
```

### 地址解析接口

```
GET /api/route/geocode?address=广州市天河区广州塔
```


