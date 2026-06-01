from langchain_core.messages import HumanMessage, SystemMessage
from app.config.logger import logger
from app.utils.mem0_client import get_user_memories
from app.model.task_model import TravelTask
from app.agents.base import llm
from app.config.settings import settings

def task_analyzer_node(state):
    """1. Task Analyzer"""
    user_content = state["messages"][-1].content
    user_id = state.get("user_id", 0)

    logger.info(f"实际调用的大模型={settings.AI_MODEL_NAME}")

    try:
        memories = get_user_memories(user_id)
        memories_text = "\n".join([m.get("memory", "") for m in memories])

        prompt = f"""你是一个严格的JSON输出器。请严格按照以下格式输出，不要添加任何其他文字、解释或代码块。

用户消息：{user_content}

输出示例：
{{
  "days": 10,
  "budget": "中等",
  "pace": "适中",
  "user_query": "请帮我规划一个北京10日游"
  "destination": "北京"
}}
注意：必须是对象 {{ }}，不要输出 [ ] 数组形式。"""


        structured_llm = llm.with_structured_output(dict, method="json_mode")
        task_output = structured_llm.invoke([SystemMessage(content=prompt)])

        task = TravelTask(
            task_id=state.get("msg_id", 0),
            trace_id=state.get("trace_id"),
            user_id=user_id,
            user_query=task_output.get('user_query', user_content),
            days=task_output.get('days', 5),
            budget=task_output.get('budget', "中等"),
            pace=task_output.get('pace', "适中"),
            destination=task_output.get('destination',"南极")
        )
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