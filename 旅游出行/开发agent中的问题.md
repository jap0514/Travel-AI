

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