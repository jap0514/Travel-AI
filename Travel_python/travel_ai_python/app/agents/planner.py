from langchain_core.messages import SystemMessage
from app.agents.base import llm, tools

def planner_node(state):
    """3. Itinerary Planner"""
    task = state["task"]
    research = state.get("research_results", "")

    system_prompt = f"""你是一位专业的旅行规划师。
用户长期偏好：暂无（可后续扩展）

当前任务：
- 天数：{task.days}天
- 预算：{task.budget}
- 节奏：{task.pace}
- 需求：{task.user_query}

请生成详细、实用的旅行计划。"""


    response = llm.bind_tools(tools).invoke([SystemMessage(content=system_prompt)] + state.get("messages", []))

    return {
        "draft_plan": response.content,
        "messages": state["messages"] + [response],
        "user_id": state.get("user_id"),
        "session_id": state.get("session_id")
    }