from langchain_core.messages import HumanMessage
from langgraph.graph import StateGraph, START, END
from typing import TypedDict, Annotated, Optional
import operator

from app.agents.final_optimizer import final_optimizer_node
from app.agents.refiner import refiner_node
from app.agents.score_judge import score_judge_node
from app.agents.supervisor import supervisor_node
from app.agents.task_analyzer import task_analyzer_node
from app.agents.researcher import researcher_node
from app.agents.planner import planner_node
from app.agents.critic import critic_node
from app.agents.parse_plan import parse_plan_node
from app.agents.base import llm, tools
from app.agents.state import AgentState

from app.config.logger import logger
from app.model.plan_model import TravelPlan
from app.utils.redis_client import get_session_context, save_session_context
from app.utils.mem0_client import add_to_memory
from app.model.task_model import TravelTask



# 构建 Graph
workflow = StateGraph(AgentState)

workflow.add_node("supervisor", supervisor_node)
workflow.add_node("task_analyzer", task_analyzer_node)
workflow.add_node("researcher", researcher_node)
workflow.add_node("planner", planner_node)
workflow.add_node("critic", critic_node)
workflow.add_node("parse_plan", parse_plan_node)
workflow.add_node("refiner",refiner_node)
workflow.add_node("final_optimizer", final_optimizer_node)

workflow.set_entry_point("supervisor")

# 条件路由
workflow.add_conditional_edges(
    "supervisor",
    lambda state: state["next"],   # 从 state.next 读取路由目标
    {
        "task_analyzer": "task_analyzer",
        "researcher": "researcher",
        "planner": "planner",
        "critic": "critic",
        "parse_plan": "parse_plan",
        "refiner": "refiner",
        "final_optimizer": "final_optimizer",
    }
)

workflow.add_edge("task_analyzer", "supervisor")
workflow.add_edge("researcher", "supervisor")
workflow.add_edge("planner", "supervisor")
workflow.add_edge("critic", "supervisor")
workflow.add_edge("refiner", "critic")           # 回到 critic ，形成循环
workflow.add_edge("final_optimizer", "supervisor")   # 最终优化后回到 supervisor
workflow.add_edge("parse_plan", END)

# ==================== Critic 的专用条件路由（实现循环核心） ====================
workflow.add_conditional_edges(
    "critic",
    lambda state: state.get("next", "supervisor"),   # 关键：critic自己决定下一步
    {
        "refiner": "refiner",           # 分数不够 → 继续修正
        "final_optimizer": "final_optimizer",  # 分数达标 → 最终润色
        "supervisor": "supervisor",     # 兜底
    }
)


multi_agent = workflow.compile()


def process_with_agent(chat_message, trace_id):
    log = logger.bind(trace_id=trace_id)
    log.info(f"进入process_with_agent")

    try:
        session_messages = get_session_context(chat_message.session_id)
        session_messages.append({"role": chat_message.role, "content": chat_message.content})

        result = multi_agent.invoke({
            "messages": [HumanMessage(content=chat_message.content)],
            "user_id": chat_message.user_id,
            "session_id": chat_message.session_id,
            "msg_id": chat_message.msg_id,
            "trace_id": trace_id,
            "next": "supervisor"  # 初始 next
        })

        final_plan = result.get("final_plan", "")
        parsed_plan = result.get("parsed_plan")
        task = result.get("task")

        session_messages.append({"role": "assistant", "content": final_plan})
        save_session_context(chat_message.session_id, session_messages)

        add_to_memory([
            {"role": "user", "content": chat_message.content},
            {"role": "assistant", "content": f"已生成{task.days}天行程"}
        ], chat_message.user_id)

        log.info(f"✅ 8-Agent 多智能体处理完成")
        return task, final_plan, parsed_plan

    except Exception as e:
        logger.exception(f"多智能体处理失败: {e}")
        raise