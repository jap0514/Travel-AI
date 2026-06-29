"""
LangGraph Agent 回调处理器
用于将 Agent 节点执行进度通过 SSE 实时推送给前端
"""

import uuid
from typing import Any, Dict, List, Optional, Union, Callable
from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.messages import BaseMessage, AIMessage, HumanMessage, SystemMessage
from langchain_core.outputs import LLMResult, ChatGeneration

from app.utils.sse_publisher import sse_emitter, get_node_display_name


class AgentProgressCallback(BaseCallbackHandler):
    """
    LangChain 回调处理器，跟踪 Agent 节点执行进度

    通过 on_chain_start / on_chain_end 追踪 LangGraph 节点的进入和完成，
    并通过 SSE 推送给前端。
    """

    def __init__(self, trace_id: str):
        super().__init__()
        self.trace_id = trace_id
        self._node_stack: List[str] = []  # 当前执行的节点栈

    def on_chain_start(
        self,
        serialized: Dict[str, Any],
        inputs: Dict[str, Any],
        *,
        run_id: uuid.UUID,
        parent_run_id: Optional[uuid.UUID] = None,
        **kwargs: Any,
    ) -> None:
        """当一个 chain（节点）开始执行时"""
        # 获取节点名称
        # serialized 中有 name 字段
        chain_name = serialized.get("name", "") if serialized else ""

        # 过滤掉内部节点，只关注我们定义的 Agent 节点
        known_nodes = {
            "Intent_Recognition", "supervisor", "task_analyzer", "researcher",
            "planner", "critic", "refiner", "final_optimizer", "parse_plan", "general_QA"
        }

        if chain_name in known_nodes:
            self._node_stack.append(chain_name)
            display_name = get_node_display_name(chain_name)
            sse_emitter.emit_node_started(self.trace_id, chain_name, display_name)

    def on_chain_end(
        self,
        outputs: Dict[str, Any],
        *,
        run_id: uuid.UUID,
        parent_run_id: Optional[uuid.UUID] = None,
        **kwargs: Any,
    ) -> None:
        """当一个 chain（节点）执行完成时"""
        if self._node_stack:
            node_name = self._node_stack.pop()
            display_name = get_node_display_name(node_name)
            sse_emitter.emit_node_finished(self.trace_id, node_name, display_name)

    def on_chain_error(
        self,
        error: Union[Exception, KeyboardInterrupt],
        *,
        run_id: uuid.UUID,
        parent_run_id: Optional[uuid.UUID] = None,
        **kwargs: Any,
    ) -> None:
        """当 chain 执行出错时"""
        error_msg = str(error)
        sse_emitter.emit_error(self.trace_id, error_msg)
        # 清空节点栈
        self._node_stack.clear()
