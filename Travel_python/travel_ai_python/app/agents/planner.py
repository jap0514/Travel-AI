from langchain_core.messages import SystemMessage
from app.agents.base import llm, get_tools


async def planner_node(state):
    """3. Itinerary Planner"""
    task = state["task"]
    tools = await get_tools()
    research = state.get("research_results", "")

    system_prompt = f"""你是一位顶级旅行行程规划大师，曾为上千位客户设计过高满意度旅行计划。

    **用户画像**：{task.user_query}

    **行程要求**：
    - 天数：{task.days}天
    - 预算水平：{task.budget}
    - 节奏：{task.pace}
    - 核心需求：{task.user_query}

    **规划原则**（必须严格遵守）：
    1. 节奏合理，每天活动量适中，不赶路、不疲劳
    2. 景点搭配均衡（文化、自然、美食、休闲结合）
    3. 逻辑流畅，减少不必要的往返
    4. 包含详细的时间安排、交通方式、预计费用
    5. 加入实用Tips（用餐建议、避坑提醒等）

    请生成完整、详细、美观的旅行行程（使用Markdown格式）。"""


    response =await llm.bind_tools(tools).ainvoke([SystemMessage(content=system_prompt)] + state.get("messages", []))

    return {
        "draft_plan": response.content,
        "messages": state["messages"] + [response],
        "user_id": state.get("user_id"),
        "session_id": state.get("session_id")
    }