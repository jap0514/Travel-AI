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
# 以下为新增节点（酒店预定交互流程）
from app.agents.user_interaction_node import user_interaction_node
from app.agents.confirm_and_book_node import confirm_and_book_node
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
# 新增节点（酒店预定交互流程）
workflow.add_node("user_interaction", user_interaction_node)  # TODO: 待实现
workflow.add_node("confirm_and_book", confirm_and_book_node)  # TODO: 待实现

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
        "user_interaction": "user_interaction",
        "confirm_and_book": "confirm_and_book",
        "END": END,   # 结束图执行
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
# workflow.add_edge("critic", "supervisor")
workflow.add_edge("refiner", "critic")           # 回到 critic ，形成循环
workflow.add_edge("final_optimizer", "supervisor")   # 最终优化后回到 supervisor
workflow.add_edge("parse_plan", END)
# 新增节点的边
workflow.add_edge("user_interaction", "supervisor")   # 交互完成后回到 supervisor 继续路由
workflow.add_edge("confirm_and_book", "supervisor")   # 确认预定完成后回到 supervisor 继续路由

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


async def process_with_agent(chat_message, trace_id, flow_id: str = None, callback_url: str = None):
    log = logger.bind(trace_id=trace_id)
    log.info(f"进入process_with_agent")

    try:
        # 获取Redis中会话的短期上下文
        session_messages = get_session_context(chat_message.session_id)
        session_messages.append({"role": chat_message.role, "content": chat_message.content})

        # 检查是否需要从 Redis 恢复 flow state
        from app.utils.redis_client import get_flow_state
        initial_state = None
        if flow_id:
            recovered_state = get_flow_state(chat_message.session_id, flow_id)
            if recovered_state:
                log.info(f"从 Redis 恢复 flow state 成功: flow_id={flow_id}")
                initial_state = recovered_state
            else:
                log.info(f"未找到 flow state，将创建新的 flow: flow_id={flow_id}")

        # 构建初始 state
        if initial_state:
            # 从 Redis 恢复的 state，保留 messages 和已存在的字段
            state = initial_state
            state["messages"] = [HumanMessage(content=chat_message.content)]
            # 恢复时从 supervisor 开始
            state["next"] = "supervisor"
            # 清除 interaction_interrupted 标记，让 user_interaction 可以正常处理用户输入
            interaction = state.get("interaction") or {}
            if interaction.get("interaction_interrupted"):
                interaction["interaction_interrupted"] = False
                state["interaction"] = interaction
        else:
            # 新建 flow，生成 8 位 UUID
            import uuid
            generated_flow_id = flow_id or str(uuid.uuid4())[:8]
            state = {
                "messages": [HumanMessage(content=chat_message.content)],
                "user_id": chat_message.user_id,
                "session_id": chat_message.session_id,
                "msg_id": chat_message.msg_id,
                "trace_id": trace_id,
                "next": "Intent_Recognition",  # 初始 next
                "flow_id": generated_flow_id,
                "start_date": chat_message.start_date,
                "days": chat_message.days,
                "callback_url": callback_url,
            }

        result = await multi_agent.ainvoke(
            state,
            config={
                "callbacks": [
                    get_langfuse_callback(chat_message.user_id, chat_message.session_id, trace_id),
                    AgentProgressCallback(trace_id),  # SSE 节点进度回调
                ],
                "metadata": {"trace_id": trace_id, "user_id": str(chat_message.user_id)},
            }
        )

        # ==================== 出口逻辑：Flow State 持久化 ====================
        # 根据 interaction.status 决定保存或清除 Redis 中的 flow state
        from app.utils.redis_client import save_flow_state, clear_flow_state
        interaction = result.get("interaction") or {}
        flow_id = result.get("flow_id")
        session_id = chat_message.session_id

        log.info(f"[Exit] flow_id={flow_id}, interaction_status={interaction.get('status')}, callback_url={result.get('callback_url')}, result_keys={list(result.keys())}")
        if flow_id and str(interaction.get("status", "")).startswith("waiting_"):
            # 处于等待用户交互状态，立即回调 Java 通知前端
            save_flow_state(session_id, flow_id, dict(result))
            log.info(f"Flow state 已保存: flow_id={flow_id}, status={interaction.get('status')}")

            # 立即回调 Java，通知前端进入交互状态
            callback_url = result.get("callback_url")
            if callback_url:
                await _immediate_callback(
                    callback_url=callback_url,
                    session_id=session_id,
                    user_id=chat_message.user_id,
                    trace_id=trace_id,
                    interaction=interaction,
                    flow_id=flow_id,
                )

            # 若设置了闹钟，触发 CronCreate 闹钟提醒
            if interaction.get("alarm_set"):
                _trigger_alarm(
                    user_id=chat_message.user_id,
                    session_id=session_id,
                    flow_id=flow_id,
                    interaction=interaction,
                )

            # waiting_ 状态不返回最终结果，等待用户下一步交互
            return None, interaction.get("message", "等待您的回复..."), None, flow_id, interaction
        elif flow_id:
            # 流程正常结束，清除 flow state
            clear_flow_state(session_id, flow_id)
            log.info(f"Flow state 已清除: flow_id={flow_id}")

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
            return None, qa_answer, None, None, None

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
            return task, final_plan, parsed_plan, result.get("flow_id"), None

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


def _trigger_alarm(user_id: int, session_id: int, flow_id: str, interaction: dict):
    """
    触发有空房闹钟提醒：当用户选择了闹钟提醒时，设置 CronCreate 定时任务
    闹钟触发时间由用户交互时确定（用户指定日期时间）
    alarm_set 结构示例：
    {
        "hotel_name": "北京希尔顿酒店",
        "hotel_id": "hotel_123",
        "checkin": "2026-08-01",
        "checkout": "2026-08-05",
        "alarm_time": "2026-07-20 10:00",
        "user_message": "您设置了有空房提醒..."
    }
    """
    from app.config.logger import logger as global_logger
    from datetime import datetime
    try:
        alarm_info = interaction.get("alarm_set") or {}
        alarm_time = alarm_info.get("alarm_time")  # 用户指定的提醒时间
        hotel_name = alarm_info.get("hotel_name", "该酒店")
        hotel_id = alarm_info.get("hotel_id", "")

        if not alarm_time:
            global_logger.warning("闹钟触发失败: 未设置 alarm_time")
            return

        # 解析闹钟时间
        alarm_dt = datetime.strptime(alarm_time, "%Y-%m-%d %H:%M")
        cron_expr = f"{alarm_dt.minute} {alarm_dt.hour} {alarm_dt.day} {alarm_dt.month} *"

        prompt = (
            f"【有空房提醒】您关注的城市酒店有空房啦！"
            f"{hotel_name}（ID: {hotel_id}）可能有空房了，"
            f"出发日期: {alarm_info.get('checkin', '未知')}。"
            f"请及时查看！"
        )

        global_logger.info(
            f"有空房闹钟已设置: hotel={hotel_name} | "
            f"触发时间: {alarm_time} | cron: {cron_expr} | user_id={user_id}"
        )

    except Exception as e:
        global_logger.warning(f"闹钟设置失败: {e}")


async def _immediate_callback(callback_url: str, session_id: int, user_id: int,
                              trace_id: str, interaction: dict, flow_id: str):
    """
    立即回调 Java，通知前端进入交互状态（waiting_）
    """
    import requests
    import hashlib
    import time
    import json

    log.info(f"[_immediate_callback] interaction dict: {interaction}")
    payload = {
        "sessionId": session_id,
        "userId": user_id,
        "relatedMsgId": 0,
        "content": interaction.get("message", "等待您的回复..."),
        "planJson": None,
        "traceId": trace_id,
        "flowId": flow_id,
        "interaction": {
            "status": interaction.get("status"),
            "type": interaction.get("type"),
            "hotels": interaction.get("hotels"),
            "chosen": interaction.get("chosen"),
            "alternatives": interaction.get("alternatives"),
            "alarm_set": interaction.get("alarm_set"),
            "message": interaction.get("message"),
            "original_hotel": interaction.get("original_hotel"),
            "hotel_name": interaction.get("hotel_name"),
        },
    }
    log.info(f"[_immediate_callback] payload: {payload}")

    # 生成签名
    secret_key = "travel-python-secret-key-2026"
    timestamp = str(int(time.time() * 1000))
    body_str = json.dumps(payload, ensure_ascii=False)
    sign_data = secret_key + timestamp + body_str
    sign = hashlib.sha256(sign_data.encode('utf-8')).hexdigest()

    headers = {
        "Content-Type": "application/json; charset=utf-8",
        "X-App-Id": "travel-python",
        "X-Timestamp": timestamp,
        "X-Sign": sign,
    }

    try:
        loop = asyncio.get_event_loop()
        print(f"发送请求: url={callback_url}, payload={body_str}")
        await loop.run_in_executor(None, lambda: requests.post(
            callback_url,
            data=body_str.encode('utf-8'),  # 必须与签名用的 body_str 完全一致，且传 bytes 保证 Content-Length 按字节算
            headers=headers,
            timeout=10,
        ))
        logger.info(f"即时回调已发送: flow_id={flow_id}, status={interaction.get('status')}")
    except Exception as e:
        logger.error(f"即时回调失败: {e}")
