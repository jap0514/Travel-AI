

1、

| 序号 | 问题描述                                                     | 原因                                                       | 最终解决方案                                                 |
| ---- | ------------------------------------------------------------ | ---------------------------------------------------------- | ------------------------------------------------------------ |
| 1    | `module 'datetime' has no attribute 'now'`                   | `import datetime` 后直接用 `datetime.now()`                | 改为 `datetime.datetime.now()` 或 `from datetime import datetime` |
| 2    | `ChatMessage.__init__() missing 1 required positional argument: 'plan_json'` | `ChatMessage` dataclass 中 `plan_json` 是必填字段          | 创建对象时必须传入 `plan_json=body.get("planJson") or None`  |
| 3    | Mem0 初始化报错（`base_url`、`openai_base_url` 等字段错误）  | Mem0 对 Embedding 配置字段要求严格                         | 使用 `openai_base_url` 而不是 `base_url`；正确配置 `embedder` 和 `llm` |
| 4    | Mem0 添加记忆失败：`shapes (0,1536) and (1024,) not aligned` | 新旧 Embedding 维度不一致（集合已存在）                    | **删除数据库文件夹** `mem0_qdrant_db`，让 Mem0 重新创建集合  |
| 5    | Mem0 添加记忆失败：`Range of input length should be [1, 2048]` | 通义千问 Embedding 最大长度限制，行程文本太长              | 在 `add_to_memory` 中对长文本进行截断，只保存摘要            |
| 6    | LangGraph 状态传递错误：`KeyError: 'user_id'` / `'task'`     | `extract_task_node` 返回的状态没有完整传递后续节点所需字段 | 每个节点必须显式返回 `user_id`、`session_id`、`task` 等关键字段 |
| 7    | `with_structured_output` 解析失败（TravelTaskOutput 缺少字段） | 大模型没有严格按 JSON 格式输出                             | 加强 Prompt + 使用 `method="json_mode"` + 添加降级默认值处理 |
| 8    | `parse_plan_node` 解析失败（'dict' object has no attribute 'user_id'`） | `with_structured_output` 有时返回 dict 而非对象            | 先解析成 `dict`，再手动构造 `TravelPlan` 对象                |
| 9    | Qdrant 退出警告（`sys.meta_path is None`）                   | Python 关闭时 Qdrant 清理代码执行时机问题                  | 无害，可通过 `warnings.filterwarnings` 忽略，或切换为 Chroma |





2、

LangGraph 在使用 add_conditional_edges 进行动态路由时，有严格的返回值要求：

| 写法 | 返回值                    | 是否正确 | 说明                             |
| ---- | ------------------------- | -------- | -------------------------------- |
| 错误 | "task_analyzer"           | ❌        | LangGraph 期望更新 state 的 dict |
| 正确 | {"next": "task_analyzer"} | ✅        | 推荐写法，清晰且稳定             |

------

**为什么会出现这个错误？**

因为你在 supervisor_node 中直接返回了节点名称字符串，而 LangGraph 的状态更新机制（_get_updates）要求 Supervisor 返回的是**可以用于更新 AgentState 的字典**。

当它拿到字符串时，无法完成状态更新，因此抛出 Expected dict, got xxx 错误。



LangGraph 的 Supervisor 模式下，**路由节点必须返回字典**，而不是直接返回字符串。

多智能体系统中，状态（State）的传递非常重要，每个节点都要保证把下游需要的数据放进去。

遇到 Expected dict, got xxx 错误，99% 是路由节点返回值类型不对。



3、模型有时候会犯浑，会按照prompt里面的模版数据



4、



**1. 核心流程循环问题（最严重）**

- **final_optimizer 被多次重复执行**（输出好几版几乎相同的“终版”行程）
- **critic → final_optimizer → supervisor → critic** 形成循环
- should_end 和 final_plan 判断不生效，导致流程无法干净终止
- iteration 计数不递增（一直显示1）

------

**2. Task 解析相关问题**

- **Task Analyzer 解析失败**：'int' object has no attribute 'get'
- 默认值提取不准确（用户说“广州1日游”，却经常解析成5天）
- TravelTask 模型缺少 destination 字段

------

**3. 节点具体 Bug**

- **researcher_node**：硬编码“北京”，且访问 task.destination 导致 AttributeError
- **critic_node**：iteration 更新后没有正确返回到 state
- **supervisor_node**：终止条件判断优先级不够高
- **parse_plan** 执行后流程虽然结束，但后续处理较弱

------

**4. 状态管理（State）问题**

- should_end 字段未在 AgentState 中声明
- iteration 的 reducer 配置不完善（可能缺少 Annotated + last_value）
- 部分字段更新后没有被 LangGraph 正确合并

------

**5. 模型切换相关问题**

- 当前使用的是通义千问（Dashscope）
- 想切换到本地 Ollama（DeepSeek-R1:1.5B）
- 需要同步修改 .env + settings.py + Mem0 配置

------

**6. 其他次要/环境问题**

- Mem0 依赖缺失警告（spaCy、fastembed）
- Qdrant 关闭时的 Python 清理警告（无害）
- 日志中出现中文和英文混杂，阅读不便
- 测试用例固定为“广州1日游”，方便调试但覆盖面有限





