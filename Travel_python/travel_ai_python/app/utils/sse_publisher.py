"""
SSE (Server-Sent Events) 实时推送工具
用于将 Agent 执行节点进度实时推送给前端
"""

import json
import asyncio
import threading
from typing import Dict, Any
from collections import defaultdict


class SSEmitter:
    """SSE 事件发射器，支持订阅模式（线程安全）"""

    def __init__(self):
        self._subscribers: Dict[str, asyncio.Queue] = defaultdict(asyncio.Queue)
        self._lock = threading.Lock()

    def subscribe(self, trace_id: str) -> asyncio.Queue:
        """为某个 trace_id 创建一个订阅队列"""
        queue = asyncio.Queue(maxsize=50)
        with self._lock:
            self._subscribers[trace_id] = queue
        return queue

    def unsubscribe(self, trace_id: str):
        """取消订阅"""
        with self._lock:
            if trace_id in self._subscribers:
                del self._subscribers[trace_id]

    async def push(self, trace_id: str, event_type: str, data: Dict[str, Any]):
        """推送事件到订阅者"""
        with self._lock:
            if trace_id not in self._subscribers:
                return
            queue = self._subscribers[trace_id]

        event = {
            "type": event_type,
            "data": data
        }
        try:
            queue.put_nowait(event)
        except asyncio.QueueFull:
            # 队列满了，丢弃旧事件
            try:
                queue.get_nowait()
                queue.put_nowait(event)
            except:
                pass

    def _get_queue(self, trace_id: str) -> asyncio.Queue | None:
        """获取订阅队列（线程安全）"""
        with self._lock:
            return self._subscribers.get(trace_id)

    def emit_node_started(self, trace_id: str, node_name: str, node_display_name: str):
        """节点开始执行（可在同步上下文中调用）"""
        self._schedule_async(self.push(trace_id, "node_started", {
            "node": node_name,
            "display": node_display_name
        }))

    def emit_node_finished(self, trace_id: str, node_name: str, node_display_name: str):
        """节点执行完成（可在同步上下文中调用）"""
        self._schedule_async(self.push(trace_id, "node_finished", {
            "node": node_name,
            "display": node_display_name
        }))

    def emit_agent_complete(self, trace_id: str, result: Dict[str, Any]):
        """Agent 执行完成（可在同步上下文中调用）"""
        self._schedule_async(self.push(trace_id, "agent_complete", result))

    def emit_error(self, trace_id: str, error: str):
        """发送错误事件（可在同步上下文中调用）"""
        self._schedule_async(self.push(trace_id, "error", {"error": error}))

    def _schedule_async(self, coro):
        """在事件循环中调度协程（兼容同步上下文）"""
        try:
            loop = asyncio.get_running_loop()
            # 在已运行的事件循环中调度任务
            loop.call_soon(lambda: asyncio.create_task(coro))
        except RuntimeError:
            # 没有运行中的事件循环，在新事件循环中运行
            try:
                loop = asyncio.get_event_loop()
                if loop.is_running():
                    # 事件循环正在运行，创建任务
                    asyncio.run_coroutine_threadsafe(coro, loop)
                else:
                    # 事件循环没有运行，直接运行协程
                    loop.run_until_complete(coro)
            except Exception:
                # 忽略调度错误
                pass


# 全局 SSE 发射器
sse_emitter = SSEmitter()


# 节点中文名称映射
NODE_DISPLAY_NAMES = {
    "Intent_Recognition": "🔍 识别用户意图",
    "supervisor": "📋 调度任务",
    "task_analyzer": "📊 分析任务",
    "researcher": "🔍 检索信息",
    "planner": "🗺️ 规划行程",
    "critic": "⭐ 评估方案",
    "refiner": "✏️ 优化细节",
    "final_optimizer": "✨ 最终优化",
    "parse_plan": "📄 生成计划",
    "general_QA": "💬 回答问题",
}

# 所有节点列表（按执行顺序）
ALL_NODES = [
    "Intent_Recognition",
    "supervisor",
    "task_analyzer",
    "researcher",
    "planner",
    "critic",
    "refiner",
    "final_optimizer",
    "parse_plan",
    "general_QA",
]


def get_node_display_name(node: str) -> str:
    """获取节点的中文显示名称"""
    return NODE_DISPLAY_NAMES.get(node, node)
