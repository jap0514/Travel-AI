from langchain_core.messages import SystemMessage
from pydantic import BaseModel, Field
from app.agents.base import llm
from app.agents.state import AgentState
from app.config.logger import logger


class CritiqueScore(BaseModel):
    overall_score: float = Field(..., description="整体评分，0-100之间的整数")
    feasibility: float = Field(..., description="可行性评分 0-100")
    budget_balance: float = Field(..., description="预算合理性评分 0-100")
    pace_suitability: float = Field(..., description="节奏适宜性评分 0-100")
    attractions_diversity: float = Field(..., description="景点多样性评分 0-100")
    feedback: str = Field(..., description="详细的批评意见和改进建议")


def critic_node(state: AgentState):
    draft = state.get("draft_plan", "")
    task = state["task"]
    iteration = state.get("iteration", 0)

    logger.info(f"第{iteration}次的分数：{state.get("scores")}")

    prompt = f"""你是一位极其严格、挑剔的专业旅行行程评审专家，标准极高。

    **任务**：对以下 {task.days}天旅行行程进行全面评审。

    **任务背景**：
    - 预算水平：{task.budget}
    - 节奏要求：{task.pace}
    - 用户核心需求：{getattr(task, 'user_query', '')}

    **当前行程草案**：
    {draft}

    **请严格按照以下JSON格式输出**，不要输出任何其他内容：

    {{
      "overall_score": 82,
      "feasibility": 85,
      "budget_balance": 78,
      "pace_suitability": 90,
      "attractions_diversity": 75,
      "feedback": "这里写详细、具体、有建设性的批评和改进建议..."
    }}

    **详细评分标准**（请严格参考）：
    - overall_score（0-100）：综合质量，考虑所有维度
    - feasibility（0-100）：时间安排是否现实、交通是否合理、每天活动量是否合适
    - budget_balance（0-100）：预算分配是否合理，是否超出或过于节省
    - pace_suitability（0-100）：节奏舒适度（太赶扣很多分，太松也扣分）
    - attractions_diversity（0-100）：景点类型是否丰富均衡（文化、美食、自然、休闲等）

    **要求**：
    - 作为严格的评审专家，请敢于打低分
    - feedback 必须具体指出问题，并给出明确的改进方向
    """

    try:
        structured_llm = llm.with_structured_output(CritiqueScore, method="json_mode")
        critique = structured_llm.invoke([SystemMessage(content=prompt)])

        # 决策下一步
        max_iter = state.get("max_iterations", 3)
        if critique.overall_score >= 85 or iteration >= max_iter - 1:
            next_node = "final_optimizer"
        else:
            next_node = "refiner"

        new_critiques = state.get("critiques", []) + [critique.feedback]

        return {
            "critiques": new_critiques,
            "scores": state.get("scores", []) + [{
                "iteration": iteration,
                "overall_score": critique.overall_score,
                **critique.model_dump(exclude={"feedback"})
            }],
            "draft_plan": draft,
            "iteration": iteration + 1,
            "next": next_node,

        }

    except Exception as e:
        # 出错时兜底处理
        print(f"Critic 结构化输出失败: {e}")
        # 返回一个默认低分，强制进入 refiner
        return {
            "critiques": state.get("critiques", []) + ["模型输出格式错误，无法解析"],
            "scores": state.get("scores", []) + [{
                "iteration": iteration,
                "overall_score": 60,
                "feasibility": 60,
                "budget_balance": 60,
                "pace_suitability": 60,
                "attractions_diversity": 60
            }],
            "draft_plan": draft,
            "iteration": iteration + 1,
            "next": "refiner"
        }