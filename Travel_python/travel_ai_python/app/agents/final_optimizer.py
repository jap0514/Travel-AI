from langchain_core.messages import HumanMessage
from app.agents.base import llm
from app.agents.state import AgentState
from app.config.logger import logger

def final_optimizer_node(state: AgentState):
    """最终优化节点：生成最终高质量版本"""
    draft = state.get("draft_plan", "")
    critiques = "\n\n".join(state.get("critiques", []))
    best_score = max([s.get("overall_score", 0) for s in state.get("scores", [])], default=0)

    prompt = f"""你是一位顶级旅行规划大师。现在进行最终润色。

当前最佳草案：
{draft}

历史评审意见（已基本解决，但仍可进一步精炼）：
{critiques}

当前最高分数：{best_score}/100

请输出**最终版**完整行程，做到：
- 节奏舒适、逻辑流畅
- 预算合理且有缓冲
- 景点搭配均衡，有亮点
- 实用性强（包含交通、时间、tips等）"""

    response = llm.invoke([HumanMessage(content=prompt)])

    logger.info(f"已完成完成最后的规划={response.content},分数={best_score}")

    return {
        "final_plan": response.content,
        "draft_plan": response.content,  # 可选：同步更新
        "next": "parse_plan",    # 结束循环
        "should_end": True
    }