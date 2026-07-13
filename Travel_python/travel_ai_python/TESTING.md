# 项目测试文档

## 环境准备

```bash
cd D:\毕业设计\Travel_python\travel_ai_python
source .venv/Scripts/activate  # Windows: .venv\Scripts\activate
```

## 启动服务

```bash
python web_app.py
```

访问 `http://localhost:8000`

---

## 测试用例

### 一、意图识别测试

| 用例编号 | 输入 | 预期结果 | 测试说明 |
|---------|------|---------|---------|
| IT-01 | "我想去杭州玩2天" | 进入规划流程（plan） | 标准旅行规划 |
| IT-02 | "杭州有什么好玩的" | 进入问答流程（qa） | 通用问答 |
| IT-03 | "淄博烧烤推荐" | 进入规划流程（plan） | 带具体目的地 |

### 二、任务分析测试（TaskAnalyzer）

| 用例编号 | 输入 | 预期结果 | 测试说明 |
|---------|------|---------|---------|
| TA-01 | "我想去杭州玩2天" | destination=杭州, days=2, budget=中等, pace=适中 | 标准2天行程 |
| TA-02 | "去北京3天，预算宽裕，节奏休闲" | destination=北京, days=3, budget=宽裕, pace=休闲 | 全参数指定 |
| TA-03 | "想去成都" | destination=成都, days=3（默认）, budget=中等（默认） | 参数缺省 |
| TA-04 | "带父母去上海耍，节奏轻松点" | destination=上海, days=3, pace=休闲 | 口语化表达 |
| TA-05 | "我要去广州5日深度游" | destination=广州, days=5 | 数字识别 |

### 三、研究阶段测试（Researcher）

| 用例编号 | 输入 | 预期结果 | 测试说明 |
|---------|------|---------|---------|
| RE-01 | "我想去杭州玩2天" | 研究报告包含景点、美食、交通信息 | 正常研究 |
| RE-02 | "我想去杭州玩2天" | 美食信息包含具体店名+地址+人均价格 | 地址具体性 |

### 四、规划阶段测试（Planner）

| 用例编号 | 输入 | 预期结果 | 测试说明 |
|---------|------|---------|---------|
| PL-01 | "我想去杭州玩2天" | 生成2天完整行程草案 | 基本规划 |
| PL-02 | "我想去杭州玩2天" | 餐饮必须为具体店名+地址，不能是"市区内餐厅" | 地址具体性 |
| PL-03 | "我想去杭州玩2天" | 每天景点不超过4个 | 节奏控制 |
| PL-04 | "去北京3天，预算宽裕" | 包含高端餐饮/住宿推荐 | 预算匹配 |

### 五、评审阶段测试（Critic）

| 用例编号 | 输入 | 预期结果 | 测试说明 |
|---------|------|---------|---------|
| CR-01 | "我想去杭州玩2天" | 4个专家评分，输出结构为 {score, feedback} | 评分格式 |
| CR-02 | "我想去杭州玩2天" | 总分≥85 → final_optimizer，总分<85 → refiner | 路由决策 |
| CR-03 | "我想去杭州玩2天" | 评分记录写入 scores 数组 | 分数记录 |
| CR-04 | 连续3次评审分数都<85 | 第3次后强制进入 final_optimizer | 最大迭代 |

### 六、优化阶段测试（Refiner + FinalOptimizer）

| 用例编号 | 输入 | 预期结果 | 测试说明 |
|---------|------|---------|---------|
| RF-01 | "我想去杭州玩2天" | refiner 解决 critique 中的问题 | 优化有效性 |
| FO-01 | "我想去杭州玩2天" | final_optimizer 输出 final_plan | 最终输出 |
| FO-02 | "我想去杭州玩2天" | final_plan 设置 should_end=True | 结束标记 |

### 七、完整流程测试

| 用例编号 | 输入 | 预期结果 | 测试说明 |
|---------|------|---------|---------|
| E2E-01 | "我想去杭州玩2天" | 完整流程：分析→研究→规划→评审→优化→解析 | 端到端 |
| E2E-02 | "我想去杭州玩2天" | 输出 parsed_plan 包含具体地址 | 地图可用性 |
| E2E-03 | "我想去杭州玩2天" | 用户偏好写入 Redis Profile | 记忆存储 |
| E2E-04 | "我想去杭州玩2天" | 对话历史写入 Mem0 | 长期记忆 |

### 八、SSE 进度推送测试

| 用例编号 | 检查点 | 预期结果 | 测试说明 |
|---------|-------|---------|---------|
| SSE-01 | 浏览器 Network 面板 | 能看到 SSE 流式响应 | SSE 连接正常 |
| SSE-02 | 日志中的 `进入supervisor_node` | 按顺序出现各节点日志 | 节点进度 |
| SSE-03 | `next=xxx` | critic 的 next 决定路由 | critic 路由 |

### 九、错误处理测试

| 用例编号 | 场景 | 预期结果 | 测试说明 |
|---------|------|---------|---------|
| ERR-01 | MiniMax API 超时 | 降级使用默认值 | 容错性 |
| ERR-02 | 高德 API 配额超限 | 地址解析失败但继续 | 外部依赖容错 |
| ERR-03 | 行程草案为空 | 跳转到 researcher 重新研究 | 状态检查 |

---

## 已知问题

- [ ] Langfuse 上报超时：网络连接 cloud.langfuse.com 超时，不影响功能
- [ ] 高德 API 10021 错误：API 配额超限，需登录高德控制台检查
- [ ] MiniMax 思考过程：模型可能输出</think>标签，需关注 task_analyzer 解析

---

## 日志位置

```
travel_ai_python\app\service\logs\travel_ai_YYYY-MM-DD.log      # 正常日志
travel_ai_python\app\service\logs\travel_ai_error_YYYY-MM-DD.log # 错误日志
travel_ai_python\logs\travel_ai_YYYY-MM-DD.log                  # 主日志
travel_ai_python\app\mcp_servers\logs\travel_ai_YYYY-MM-DD.log   # MCP 服务日志
```

---

## 快速验证命令

```bash
# 验证 Python 环境
python -c "import langgraph; print('langgraph OK')"

# 验证 MCP 工具
curl http://localhost:8000/api/mcp/tools  # 需先启动服务

# 验证 Redis 连接
python -c "from app.utils.redis_client import get_session_context; print('redis OK')"

# 验证 Mem0 连接
python -c "from app.utils.mem0_client import get_user_memories; print('mem0 OK')"
```
