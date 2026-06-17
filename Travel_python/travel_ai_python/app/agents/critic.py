import asyncio
from langchain_core.messages import SystemMessage
from pydantic import BaseModel, Field
from app.agents.base import llm
from app.agents.state import AgentState
from app.config.logger import logger


# ========== 四个独立专家的输出结构 ==========
class FeasibilityScore(BaseModel):
    score: float = Field(..., description="可行性评分，0-100")
    feedback: str = Field(..., description="关于时间安排、交通、每日活动量的具体批评和建议")


class BudgetScore(BaseModel):
    score: float = Field(..., description="预算合理性评分，0-100")
    feedback: str = Field(..., description="关于预算分配、是否超出或过于节省的具体意见")


class PaceScore(BaseModel):
    score: float = Field(..., description="节奏适宜性评分，0-100")
    feedback: str = Field(..., description="关于行程松紧度、疲劳程度的具体改进建议")


class DiversityScore(BaseModel):
    score: float = Field(..., description="景点多样性评分，0-100")
    feedback: str = Field(..., description="关于景点类型丰富度、是否均衡（文化/自然/美食/休闲）的具体意见")


async def critic_node(state: AgentState):
    draft = state.get("draft_plan", "")
    task = state["task"]
    iteration = state.get("iteration", 0)

    logger.info(f"第{iteration}次并行评审（可行性/预算/节奏/多样性）")

    # ---------- 1. 构造四个专家的 prompt ----------
    base_prompt = f"""
你是一位极其严格、挑剔的专业旅行行程评审专家。

**任务背景**：
- 预算水平：{task.budget}
- 节奏要求：{task.pace}
- 天数：{task.days}天
- 用户核心需求：{getattr(task, 'user_query', '')}

**当前行程草案**：
{draft}
"""

    # 可行性专家
    feasibility_prompt = base_prompt + """
你负责评审**可行性**：
- 时间安排是否现实（景点开门/关门时间、游玩时长）
- 交通衔接是否合理（城市内部、城际移动）
- 每天活动量是否合适（步行、排队、休息）

输出格式：{"score": 0-100, "feedback": "具体建议"}
只输出 JSON，不要有其他内容。
"""

    # 预算专家
    budget_prompt = base_prompt + """
你负责评审**预算合理性**：
- 预算分配是否合理（住宿、餐饮、门票、交通）
- 是否超出预算水平或过于节省
- 是否有隐性消费风险

输出格式：{"score": 0-100, "feedback": "具体建议"}
只输出 JSON，不要有其他内容。
"""

    # 节奏专家
    pace_prompt = base_prompt + """
你负责评审**节奏适宜性**：
- 行程是否太赶（每天超过 4 个景点扣分）
- 行程是否太松（空闲时间太多也扣分）
- 景点类型是否交替（室内/室外、动/静）

输出格式：{"score": 0-100, "feedback": "具体建议"}
只输出 JSON，不要有其他内容。
"""

    # 多样性专家
    diversity_prompt = base_prompt + """
你负责评审**景点多样性**：
- 景点类型是否丰富均衡（历史文化、自然风光、美食体验、休闲娱乐、购物等）
- 是否有重复同质化景点
- 是否覆盖当地核心特色

输出格式：{"score": 0-100, "feedback": "具体建议"}
只输出 JSON，不要有其他内容。
"""

    # ---------- 2. 并行调用四个专家 ----------
    async def call_expert(prompt: str, output_model):
        structured_llm = llm.with_structured_output(output_model, method="json_mode")
        try:
            result = await structured_llm.ainvoke([SystemMessage(content=prompt)])
            return result
        except Exception as e:
            logger.warning(f"专家评审失败: {e}")
            # 返回默认低分
            return output_model(score=60, feedback="模型输出格式错误，使用默认评分")

    # 并行执行
    feasibility_task = call_expert(feasibility_prompt, FeasibilityScore)
    budget_task = call_expert(budget_prompt, BudgetScore)
    pace_task = call_expert(pace_prompt, PaceScore)
    diversity_task = call_expert(diversity_prompt, DiversityScore)

    feasibility, budget, pace, diversity = await asyncio.gather(
        feasibility_task, budget_task, pace_task, diversity_task
    )

    # ---------- 3. 加权总分与决策 ----------
    # 权重：可行性40% / 预算20% / 节奏20% / 多样性20%（可根据需求调整）
    overall_score = (
        feasibility.score * 0.40 +
        budget.score * 0.20 +
        pace.score * 0.20 +
        diversity.score * 0.20
    )
    overall_score = round(overall_score, 1)

    # 决定下一步
    max_iter = state.get("max_iterations", 3)
    if overall_score >= 85 or iteration >= max_iter - 1:
        next_node = "final_optimizer"
    else:
        next_node = "refiner"

    # ---------- 4. 更新 State ----------
    # 合并所有专家的反馈
    new_critiques = state.get("critiques", []) + [
        f"【可行性】{feasibility.feedback}",
        f"【预算】{budget.feedback}",
        f"【节奏】{pace.feedback}",
        f"【多样性】{diversity.feedback}"
    ]

    # 记录本次评分详情（兼容原 state 中的字段名）
    new_scores = state.get("scores", []) + [{
        "iteration": iteration,
        "overall_score": overall_score,
        "feasibility": feasibility.score,
        "budget_balance": budget.score,
        "pace_suitability": pace.score,
        "attractions_diversity": diversity.score,
    }]

    logger.info(f"并行评审完成 | 总分: {overall_score} | 下一步: {next_node}")

    return {
        "critiques": new_critiques,
        "scores": new_scores,
        "draft_plan": draft,
        "iteration": iteration + 1,
        "next": next_node,
    }