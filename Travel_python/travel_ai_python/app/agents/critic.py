from langchain_core.messages import SystemMessage
from pydantic import BaseModel, Field
from app.agents.base import llm
from app.agents.state import AgentState


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

    prompt = f"""你是一位极其严格、专业的旅行行程评审专家。
请严格按照JSON格式输出，不要输出任何解释、 markdown、代码块或其他文字。

任务信息：{task.days}天 {task.budget}预算 {task.pace}节奏

当前行程草案：
{draft}

请严格输出以下JSON格式（字段必须完全一致）：

{{
  "overall_score": 85,
  "feasibility": 88,
  "budget_balance": 82,
  "pace_suitability": 90,
  "attractions_diversity": 75,
  "feedback": "这里写详细的评审意见和具体改进建议..."
}}

注意：
- 所有评分必须是0-100的数字
- feedback 必须是字符串，详细指出问题
- 不要添加任何其他字段，不要写```json
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