from langchain_core.messages import SystemMessage
from app.config.logger import logger
from app.agents.state import AgentState
from app.agents.base import llm


def refiner_node(state: AgentState):
    draft = state["draft_plan"]
    critiques = "\n".join(state.get("critiques", []))

    prompt = f"""根据以下批评意见，生成改进后的完整行程（Markdown格式）：

原草案：
{draft}

批评与建议：
{critiques}

请生成显著改进的新版本，确保解决所有主要问题。"""

    logger.info(f"迭代次数={state.get("iteration")}")

    response = llm.invoke([SystemMessage(content=prompt)])

    return {
        "draft_plan": response.content,  # 更新draft用于下一轮
    }