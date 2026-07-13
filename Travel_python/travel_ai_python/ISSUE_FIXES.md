# 项目问题修复记录

## 1. SSE 进度显示问题

### 问题描述
前端加载动画显示固定的 `loadingSteps` 数组内容（"正在分析您的需求..."、"正在检索相关信息..."等），而不是根据 Agent 实际执行进度动态显示。

### 问题原因
前端同时存在两套更新逻辑：
1. **后端 SSE 事件驱动** — `handleSSEEvent` 收到 `node_started/node_finished` 时更新进度
2. **setInterval 轮询覆盖** — 每 3 秒强制覆盖为静态数组内容

即使后端发送了真实的节点进度，3 秒后又被轮询覆盖回去。

### 解决方案
删除 `index.html` 中 `showLoading()` 函数的 `setInterval` 轮询机制，让进度完全由后端 SSE 事件驱动。

**修改文件**：`app/web/static/index.html`

**删除内容**：
- 删除了 `loadingStepInterval` 变量声明
- 删除了 `loadingSteps` 静态数组
- 删除了 `setInterval` 轮询逻辑
- 简化 `removeLoading()` 函数，移除对 `loadingStepInterval` 的引用

---

## 2. `loadingStepInterval is not defined` 错误

### 问题描述
前端控制台报错：`ReferenceError: loadingStepInterval is not defined`

### 问题原因
删除 `loadingStepInterval` 变量时，漏掉了 `removeLoading()` 函数中对其的引用。

### 解决方案
简化 `removeLoading()` 函数，仅保留删除 DOM 元素的逻辑：

```javascript
function removeLoading(id) {
    const el = document.getElementById(id);
    if (el) el.remove();
}
```

---

## 3. SSE 节点进度事件不触发

### 问题描述
后端 `AgentProgressCallback` 的 `on_chain_start/on_chain_end` 被调用，但前端始终收不到 `node_started/node_finished` 事件。

### 问题分析

#### 调试过程
通过添加调试日志，发现：
```
[DEBUG] on_chain_start called: chain_name=
[DEBUG] serialized keys: None
[DEBUG] kwargs keys: dict_keys(['tags', 'metadata', 'name'])
[DEBUG] kwargs name: Intent_Recognition  // 节点名称在这里！
```

#### 问题根因
`AgentProgressCallback` 从 `serialized.get("name")` 获取节点名称，但：
- `serialized` 在大多数情况下为 `None`
- 真正的节点名称通过 `kwargs["name"]` 传递

### 解决方案
修改 `app/utils/agent_callback.py`：

```python
# 原来（错误）
chain_name = serialized.get("name", "") if serialized else ""

# 修改后（正确）
chain_name = kwargs.get("name", "")
```

**修改文件**：`app/utils/agent_callback.py`

添加注释说明：
```python
# 注意：LangGraph 的节点名称不在 serialized 中，而是在 kwargs["name"] 中
# 这是 LangChain 回调机制的特点：serialized 在大多数情况下为 None，
# 真正的节点名称通过 kwargs 传递（如 Intent_Recognition, supervisor, task_analyzer 等）
```

---

## 4. `coroutine was never awaited` 警告

### 问题描述
控制台报警告：`RuntimeWarning: coroutine 'SSEmitter.push' was never awaited`

### 问题原因
`_schedule_async` 方法在同步上下文中无法正确调度协程：

```python
def _schedule_async(self, coro):
    try:
        loop = asyncio.get_running_loop()
        # 问题在这里：call_soon 不会 await 协程
        loop.call_Soon(lambda: asyncio.create_task(coro))
    except RuntimeError:
        # 后备方案也失败了
        ...
```

### 解决方案
使用独立线程运行协程，确保协程被正确执行：

```python
def _schedule_async(self, coro):
    """在事件循环中调度协程（兼容同步上下文）"""
    try:
        loop = asyncio.get_running_loop()
        asyncio.ensure_future(coro)
    except RuntimeError:
        # 没有运行中的事件循环，在新线程中运行
        import threading
        def run_coro():
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            try:
                loop.run_until_complete(coro)
            finally:
                loop.close()
        threading.Thread(target=run_coro, daemon=True).start()
```

**修改文件**：`app/utils/sse_publisher.py`

---

## 5. 会话上下文丢失问题（待解决）

### 问题描述
用户连续对话时，Agent 只看到当前消息，无法感知之前的对话历史。

### 问题分析
`process_with_agent` 从 Redis 加载了 `session_messages`，但传给 Agent 的只有当前消息：

```python
session_messages = get_session_context(chat_message.session_id)
session_messages.append({"role": chat_message.role, "content": chat_message.content})

result = await multi_agent.ainvoke(
    {
        "messages": [HumanMessage(content=chat_message.content)],  # 只传了当前消息
        ...
    }
)
```

### 建议方案
在 `task_analyzer_node` 等需要上下文的节点中，从 Redis 独立获取会话历史：

```python
async def task_analyzer_node(state: State) -> dict:
    session_id = state.get("session_id")
    session_messages = get_session_context(session_id)

    history_text = "\n".join([
        f"{m['role']}: {m['content']}"
        for m in session_messages
    ])

    # 把历史拼到 prompt 里
    prompt = f"""用户当前消息: {user_msg}\n\n对话历史:\n{history_text}\n\n根据完整上下文提取任务信息..."""
```

---

## 修复文件清单

| 文件 | 修改内容 |
|------|----------|
| `app/web/static/index.html` | 删除 loadingSteps 数组和 setInterval；简化 removeLoading |
| `app/utils/agent_callback.py` | 修复节点名称获取方式：kwargs["name"] |
| `app/utils/sse_publisher.py` | 修复协程调度：使用独立线程运行协程 |
