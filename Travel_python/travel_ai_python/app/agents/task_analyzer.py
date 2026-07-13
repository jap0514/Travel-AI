# -*- coding: utf-8 -*-
"""
Task Analyzer 节点 - 从用户消息中提取结构化任务参数

角色：任务信息提取专家
输入：用户消息
输出：TravelTask 对象（destination, days, budget, pace）
"""
from langchain_core.messages import HumanMessage
from app.agents.base import llm
from app.model.task_model import TravelTask
from app.utils.mem0_client import get_user_memories
from app.utils.redis_client import get_user_profile,get_session_context
from app.config.logger import logger
from app.config.settings import settings
import json



ROLE_DEFINITION = """
## 角色：旅行任务信息提取专家

### 核心技能
- 精准提取用户旅行需求
- 理解口语化表达
- 处理模糊或不完整信息
- 推断缺失参数

### 能力
- 从口语中识别目的地
- 提取具体天数
- 判断预算水平
- 识别节奏偏好
"""

OUTPUT_FORMAT = """
### 输出规范
必须严格按以下 JSON 格式输出：
{
  "destination": "城市名",
  "days": 数字,
  "budget": "经济/中等/宽裕",
  "pace": "紧凑/适中/休闲",
  "user_query": "用户原始诉求"
}

### 约束
- destination 必须从用户消息中提取，不能凭空编造
- days 必须是数字，如3
- budget 只能是"经济"/"中等"/"宽裕"之一
- pace 只能是"紧凑"/"适中"/"休闲"之一
"""

EXAMPLES = """
### 示例

输入："我想去北京玩3天，预算一般"
输出：
{"destination": "北京", "days": 3, "budget": "中等", "pace": "适中", "user_query": "我想去北京玩3天，预算一般"}

输入："广州5日深度游"
输出：
{"destination": "广州", "days": 5, "budget": "中等", "pace": "适中", "user_query": "广州5日深度游"}

输入："带父母去成都耍，节奏轻松点"
输出：
{"destination": "成都", "days": 4, "budget": "中等", "pace": "休闲", "user_query": "带父母去成都耍，节奏轻松点"}
"""

DEFAULT_VALUES = """
### 默认值（当无法提取时使用）（我觉得这个默认值应该从用户的历史偏好中获取）
- destination: "北京"（用户未指定则默认北京）
- days: 3
- budget: "中等"
- pace: "适中"
"""


def task_analyzer_node(state):
    """
    Task Analyzer 节点 - 提取用户旅行任务参数

    输入：用户最新消息 + Mem0记忆 + Redis Profile
    输出：TravelTask 对象
    """
    user_msg = state["messages"][-1].content
    user_id = state.get("user_id", 0)
    session_id=state.get("session_id",0)

    logger.info(f"[TaskAnalyzer] 用户消息: {user_msg[:50]}...")

    # 获取记忆和偏好
    memories = get_user_memories(user_id)
    memories_text = "\n".join([m.get("memory", "") for m in memories]) if memories else "暂无"
    profile = get_user_profile(user_id)
    profile_text = json.dumps(profile, ensure_ascii=False) if profile else "暂无"
    # 获取历史对话
    session_messages= get_session_context(session_id)
    history_text= "\n".join([
        f"{m['role']}: {m['content']}"
        for m in session_messages
    ])

    prompt = f"""{ROLE_DEFINITION}

## 用户消息（必须从中提取信息）
{user_msg}

## 用户当前会话的历史消息
{history_text}

## 用户历史记忆（仅供参考，不能覆盖用户当前消息）
{memories_text}

## 用户偏好 Profile（仅供参考）
{profile_text}

{OUTPUT_FORMAT}

{EXAMPLES}

{DEFAULT_VALUES}

## 重要
- destination 必须来自用户消息，不是默认值
- 只看用户当前这条消息，不做推理假设
- 只输出 JSON 对象，不要其他内容
- 请直接输出JSON，不要包含思考过程（如</think>标签）"""

    try:
        structured_llm = llm.with_structured_output(dict, method="json_mode")
        result = structured_llm.invoke([HumanMessage(content=prompt)])

        task = TravelTask(
            task_id=state.get("msg_id", 0),
            trace_id=state.get("trace_id"),
            user_id=user_id,
            user_query=result.get("user_query", user_msg),
            days=result.get("days", 3),
            budget=result.get("budget", "中等"),
            pace=result.get("pace", "适中"),
            destination=result.get("destination", "北京")
        )

        logger.info(f"[TaskAnalyzer] 提取结果: {task.destination} {task.days}天 {task.budget} {task.pace}")

    except Exception as e:
        logger.warning(f"[TaskAnalyzer] 提取失败，使用默认值: {e}")
        task = TravelTask(
            task_id=state.get("msg_id", 0),
            trace_id=state.get("trace_id"),
            user_id=user_id,
            user_query=user_msg,
            days=3,
            budget="中等",
            pace="适中",
            destination="北京"
        )

    return {
        "task": task,
        "messages": state["messages"] + [HumanMessage(content=f"已提取任务: {task.destination}{task.days}天 {task.budget} {task.pace}")],
        "user_id": user_id,
        "session_id": state.get("session_id")
    }
