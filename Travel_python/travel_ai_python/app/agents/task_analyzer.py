from langchain_core.messages import HumanMessage
from app.config.logger import logger
from app.utils.mem0_client import get_user_memories
from app.utils.redis_client import get_user_profile
from app.model.task_model import TravelTask
from app.agents.base import llm
from app.config.settings import settings
import json
import re


def task_analyzer_node(state):
    """1. Task Analyzer"""
    user_content = state["messages"][-1].content
    user_id = state.get("user_id", 0)

    logger.info(f"实际调用的大模型={settings.AI_MODEL_NAME}")

    try:
        # 读取 Mem0 长期记忆
        memories = get_user_memories(user_id)
        memories_text = "\n".join([m.get("memory", "") for m in memories]) if memories else "暂无"
        logger.info(f"Mem0 长期记忆：{memories_text}")

        # 读取用户 Profile 结构化偏好
        profile = get_user_profile(user_id)
        profile_text = json.dumps(profile, ensure_ascii=False) if profile else "暂无"
        logger.info(f"用户 Profile：{profile_text}")

        prompt = f"""你是一个严格的JSON输出器，从用户消息中提取旅行参数。

⚠️ 最重要规则：只根据「用户消息」提取信息！
历史记忆和偏好仅作背景参考，不能覆盖用户当前消息中的需求。

用户消息：{user_content}

【用户历史对话（Mem0 长期记忆）】仅供背景参考：
{memories_text}

【用户结构化偏好（Profile）】仅供背景参考：
{profile_text}

提取要求：
- destination：必须从「用户消息」中提取用户这次想去的目的地城市
- days：用户消息中提到的旅行天数
- budget：从用户消息中提取预算（"经济"/"中等"/"宽裕"），没提就填"中等"
- pace：节奏（"紧凑"/"适中"/"休闲"），没提就填"适中"
- user_query：用户诉求

输出格式（严格JSON，不要任何解释文字）：
{{
  "days": 3,
  "budget": "中等",
  "pace": "适中",
  "user_query": "请帮我规划一个广州3日游",
  "destination": "广州"
}}

注意：
- destination 必须来自用户消息中这次提到的城市名，不要从历史记忆中取
- 只输出JSON对象，不要代码块，不要解释"""

        raw = llm.invoke([HumanMessage(content=prompt)]).content
        # 提取 JSON 对象（支持嵌套大括号）
        json_match = re.search(r'\{[\s\S]*\}', raw, re.DOTALL)
        if json_match:
            task_output = json.loads(json_match.group())
        else:
            raise ValueError("无法从模型返回中提取 JSON")

        task = TravelTask(
            task_id=state.get("msg_id", 0),
            trace_id=state.get("trace_id"),
            user_id=user_id,
            user_query=task_output.get('user_query', user_content),
            days=task_output.get('days', 5),
            budget=task_output.get('budget', "中等"),
            pace=task_output.get('pace', "适中"),
            destination=task_output.get('destination', "南极")
        )
        logger.info(f"✅ 任务提取结果: destination={task.destination}, days={task.days}, budget={task.budget}, pace={task.pace}")
    except Exception as e:
        logger.warning(f"Task 提取失败，使用默认值: {e}")
        task = TravelTask(
            task_id=state.get("msg_id", 0),
            trace_id=state.get("trace_id"),
            user_id=user_id,
            user_query=user_content,
            days=5,
            budget="中等",
            pace="适中",
            destination="北极"
        )

    return {
        "task": task,
        "messages": state["messages"] + [HumanMessage(content=f"已提取任务: {task.days}天 {task.budget}预算")],
        "user_id": user_id,
        "session_id": state.get("session_id")
    }
