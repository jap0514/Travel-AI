from langchain_core.messages import SystemMessage
from app.agents.base import llm, tools

def researcher_node(state):
    """2. Researcher"""
    task = state["task"]
    prompt = f"""请为{task.destination or '用户指定城市'} {task.days}日游（{task.budget}预算，{task.pace}节奏）收集实用信息，包括景点推荐、天气建议、门票价格、注意事项等。"""


    response = llm.bind_tools(tools).invoke([SystemMessage(content=prompt)])

    return {
        "research_results": response.content,
        "messages": state["messages"]
    }