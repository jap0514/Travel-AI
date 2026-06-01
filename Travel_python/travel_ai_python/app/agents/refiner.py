from langchain_core.messages import SystemMessage
from app.config.logger import logger
from app.agents.state import AgentState
from app.agents.base import llm


def refiner_node(state: AgentState):
    draft = state["draft_plan"]
    critiques = "\n".join(state.get("critiques", []))

    prompt = f"""你是一位顶级旅行行程优化专家，擅长根据专家反馈大幅提升行程质量。

    **原草案**：
    {draft}

    **专家评审意见**：
    {critiques}

    **优化要求**：
    1. 必须解决评审中提到的所有主要问题
    2. 显著提升行程的逻辑性、舒适度和亮点
    3. 保持预算在合理范围内
    4. 优化每日节奏，避免过度疲劳或过于松散
    5. 增强整体连贯性和惊喜感

    请输出**大幅改进后的完整新版本行程**（使用Markdown格式）。"""

    logger.info(f"refiner迭代次数={state.get("iteration")}")

    response = llm.invoke([SystemMessage(content=prompt)])

    return {
        "draft_plan": response.content,  # 更新draft用于下一轮
    }