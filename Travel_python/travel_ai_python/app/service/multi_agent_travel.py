import asyncio
import functools

from langchain_core.messages import HumanMessage, SystemMessage
from langgraph.graph import StateGraph, START, END
from typing import TypedDict, Annotated, Optional
import operator

from app.agents.Intent_Recognition import intent_recognition_node
from app.agents.final_optimizer import final_optimizer_node
from app.agents.general_QA import general_qa_node
from app.agents.refiner import refiner_node
from app.agents.score_judge import score_judge_node
from app.agents.supervisor import supervisor_node
from app.agents.task_analyzer import task_analyzer_node
from app.agents.researcher import researcher_node
from app.agents.planner import planner_node
from app.agents.critic import critic_node
from app.agents.parse_plan import parse_plan_node
from app.agents.base import llm
from app.agents.state import AgentState

from app.config.logger import logger
from app.model.plan_model import TravelPlan
from app.utils.redis_client import get_session_context, save_session_context, get_user_profile, save_user_profile
from app.utils.mem0_client import add_memory_background
from app.model.task_model import TravelTask
from app.utils.agent_callback import AgentProgressCallback

# ==================== Langfuse 追踪 ====================
# Langfuse 通过环境变量 LANGFUSE_PUBLIC_KEY / LANGFUSE_SECRET_KEY / LANGFUSE_HOST 配置
# LangChain CallbackHandler 自动追踪所有 LLM 调用、工具调用和 Agent 节点的输入/输出/耗时/token
from langfuse.langchain import CallbackHandler as LangfuseCallbackHandler

_langfuse_handler = LangfuseCallbackHandler()


def get_langfuse_callback(user_id: int, session_id: int, trace_id: str):
    """获取 Langfuse LangChain callback handler"""
    return _langfuse_handler



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
workflow.add_node("general_QA",general_qa_node)
workflow.add_node("Intent_Recognition",intent_recognition_node)

workflow.set_entry_point("Intent_Recognition")

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

workflow.add_conditional_edges(
    "Intent_Recognition",
    lambda state: state["intent"], {
        "plan": "supervisor",
        "qa": "general_QA"
    }
)
workflow.add_edge("general_QA",END)

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


async def process_with_agent(chat_message, trace_id):
    log = logger.bind(trace_id=trace_id)
    log.info(f"进入process_with_agent")

    try:
        # 获取Redis中会话的短期上下文
        session_messages = get_session_context(chat_message.session_id)
        session_messages.append({"role": chat_message.role, "content": chat_message.content})

        result = await multi_agent.ainvoke(
            {
                "messages": [HumanMessage(content=chat_message.content)],
                "user_id": chat_message.user_id,
                "session_id": chat_message.session_id,
                "msg_id": chat_message.msg_id,
                "trace_id": trace_id,
                "next": "Intent_Recognition"  # 初始 next
            },
            config={
                "callbacks": [
                    get_langfuse_callback(chat_message.user_id, chat_message.session_id, trace_id),
                    AgentProgressCallback(trace_id),  # SSE 节点进度回调
                ],
                "metadata": {"trace_id": trace_id, "user_id": str(chat_message.user_id)},
            }
        )

        final_plan = result.get("final_plan", "")
        parsed_plan = result.get("parsed_plan","")
        task = result.get("task")
        qa_answer = result.get("qa_answer", "")

        session_messages.append({"role": "assistant", "content": final_plan})
        save_session_context(chat_message.session_id, session_messages)

        # 区分问答流程和规划流程
        if qa_answer:
            # 问答流程 — 后台存 Mem0，不阻塞返回
            add_memory_background([
                {"role": "user", "content": chat_message.content},
                {"role": "assistant", "content": qa_answer}
            ], chat_message.user_id)
            log.info(f"✅ 问答处理完成")
            log.info(f"回答：{qa_answer}")
            return None, qa_answer, None

        else:
            # 旅游规划流程
            add_memory_background([
                {"role": "user", "content": chat_message.content},
                {"role": "assistant", "content": f"已生成{task.days}天行程"}
            ], chat_message.user_id)

            # 从任务中提取偏好并更新 Profile
            # 也放到后台执行，不阻塞返回
            _update_profile_background(
                user_id=chat_message.user_id,
                task=task,
                final_plan_text=final_plan,
                session_messages=session_messages
            )

            log.info(f"✅ 8-Agent 多智能体处理完成")
            return task, final_plan, parsed_plan

    except Exception as e:
        logger.exception(f"多智能体处理失败: {e}")
        raise


def _update_profile_background(user_id: int, task: TravelTask, final_plan_text: str, session_messages: list):
    """将 Profile 更新任务放入 Redis 队列，由 Worker 异步串行处理，不阻塞主流程"""
    from app.utils.redis_client import push_profile_update_task

    task_data = {
        "user_id": user_id,
        "task": {
            "user_query": task.user_query if task else "",
            "destination": task.destination if task else None,
            "days": task.days if task else None,
            "budget": task.budget if task else None,
            "pace": task.pace if task else None,
        },
        "final_plan_text": final_plan_text,
        "session_messages": session_messages,
    }

    push_profile_update_task(user_id, task_data)
    logger.info(f"Profile 更新任务已入队: user_id={user_id}")
