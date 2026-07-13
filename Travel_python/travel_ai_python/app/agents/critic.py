# -*- coding: utf-8 -*-
"""
Critic 节点 - 4个专家并行评审行程草案

专家：可行性专家、预算专家、节奏专家、多样性专家
评分：每个专家 0-100 分
决策：加权总分 < 85 则进入 refiner 优化
"""
import asyncio
from langchain_core.messages import HumanMessage
from pydantic import BaseModel, Field
from app.agents.base import llm
from app.agents.state import AgentState
from app.config.logger import logger


# ============================
# 评审专家输出结构
# ============================
class FeasibilityScore(BaseModel):
    """可行性专家评分"""
    score: float = Field(..., description="可行性评分，0-100分")
    feedback: str = Field(..., description="具体改进建议，100字以内")


class BudgetScore(BaseModel):
    """预算专家评分"""
    score: float = Field(..., description="预算合理性评分，0-100分")
    feedback: str = Field(..., description="具体改进建议，100字以内")


class PaceScore(BaseModel):
    """节奏专家评分"""
    score: float = Field(..., description="节奏适宜性评分，0-100分")
    feedback: str = Field(..., description="具体改进建议，100字以内")


class DiversityScore(BaseModel):
    """多样性专家评分"""
    score: float = Field(..., description="景点多样性评分，0-100分")
    feedback: str = Field(..., description="具体改进建议，100字以内")


# ============================
# 各专家角色定义
# ============================
FEASIBILITY_EXPERT = """
## 角色：行程可行性评审专家

### 核心技能
- 评估每天活动时间是否现实
- 检查景点开放时间、游玩时长
- 验证交通衔接合理性
- 评估城市内移动时间成本

### 评分标准（0-100）
- 90-100：时间安排完美，无赶路、无等待
- 70-89：基本合理，有小问题
- 50-69：明显赶路或等待过长
- <50：行程不可行

### 示例反馈
- "第2天安排太紧，博物馆+寺庙+缆车需要8小时，建议拆分"
- "两个景点距离30公里，建议调换顺序或删除一个"

## 输出格式（必须严格遵守）
{"score": 85, "feedback": "具体改进建议，100字以内"}
只输出一个JSON对象，不要任何其他文字。
"""

BUDGET_EXPERT = """
## 角色：预算合理性评审专家

### 核心技能
- 评估整体预算是否合理
- 检查各项目费用分配
- 识别隐性消费风险
- 判断是否超出或过于节省

### 评分标准（0-100）
- 90-100：预算分配完美，既有品质又不浪费
- 70-89：基本合理，有小优化空间
- 50-69：明显超支或过于节俭
- <50：预算完全不合理

### 示例反馈
- "住宿档次偏高，预算中等建议选300-500元酒店"
- "景区门票重复，可考虑部分免费景点替代"

## 输出格式（必须严格遵守）
{"score": 85, "feedback": "具体改进建议，100字以内"}
只输出一个JSON对象，不要任何其他文字。
"""

PACE_EXPERT = """
## 角色：行程节奏评审专家

### 核心技能
- 评估每天活动密度
- 判断是否太赶或太空闲
- 检查室内外交替是否合理
- 评估休息时间是否充足

### 评分标准（0-100）
- 90-100：节奏完美，张弛有度
- 70-89：基本合适，略有不足
- 50-69：明显太赶或太空
- <50：节奏完全失衡

### 示例反馈
- "每天超过4个景点，节奏太赶，建议减少1个"
- "第3天完全空白，建议增加一个景点"
- "连续3天都是户外，体力消耗大，建议穿插室内"

## 输出格式（必须严格遵守）
{"score": 85, "feedback": "具体改进建议，100字以内"}
只输出一个JSON对象，不要任何其他文字。
"""

DIVERSITY_EXPERT = """
## 角色：景点多样性评审专家

### 核心技能
- 评估景点类型是否丰富
- 检查是否有同质化景点
- 验证是否覆盖核心特色
- 判断是否有文化/自然/美食/休闲平衡

### 评分标准（0-100）
- 90-100：类型丰富，均衡完美
- 70-89：基本丰富，略有重复
- 50-69：有明显同质化或缺失
- <50：景点单一，缺乏特色

### 示例反馈
- "连续3个人文景点，建议加一个自然风光"
- "缺少美食体验，建议增加当地特色餐厅"

## 输出格式（必须严格遵守）
{"score": 85, "feedback": "具体改进建议，100字以内"}
只输出一个JSON对象，不要任何其他文字。
"""


async def critic_node(state: AgentState):
    """
    Critic 节点 - 并行调用4个评审专家

    输入：draft_plan（行程草案）
    输出：4个评分 + 总分 + 决定下一步
    决策规则：
    - 总分 >= 85 → 进入 final_optimizer
    - 总分 < 85 → 进入 refiner 优化
    """
    draft = state.get("draft_plan", "")
    task = state["task"]
    iteration = state.get("iteration", 0)

    logger.info(f"[Critic] 第{iteration}次评审开始")

    # 基础信息（供所有专家参考）
    base_context = f"""
## 行程草案
{draft}

## 用户需求
- 目的地：{getattr(task, 'destination', '未知')}
- 天数：{getattr(task, 'days', 0)}天
- 预算：{getattr(task, 'budget', '中等')}
- 节奏：{getattr(task, 'pace', '适中')}
- 核心诉求：{getattr(task, 'user_query', '')}
"""

    # 并行调用4个专家
    async def call_expert(prompt: str, output_model):
        structured_llm = llm.with_structured_output(output_model, method="json_mode")
        try:
            result = await structured_llm.ainvoke([HumanMessage(content=prompt)])
            return result
        except Exception as e:
            logger.warning(f"[Critic] 专家调用失败: {e}")
            return output_model(score=60, feedback="模型输出错误")

    # 4个专家并行执行
    feasibility_task = call_expert(
        base_context + FEASIBILITY_EXPERT + "\n\n## 输出要求\n只输出JSON对象",
        FeasibilityScore
    )
    budget_task = call_expert(
        base_context + BUDGET_EXPERT + "\n\n## 输出要求\n只输出JSON对象",
        BudgetScore
    )
    pace_task = call_expert(
        base_context + PACE_EXPERT + "\n\n## 输出要求\n只输出JSON对象",
        PaceScore
    )
    diversity_task = call_expert(
        base_context + DIVERSITY_EXPERT + "\n\n## 输出要求\n只输出JSON对象",
        DiversityScore
    )

    # 等待所有专家完成
    feasibility, budget, pace, diversity = await asyncio.gather(
        feasibility_task, budget_task, pace_task, diversity_task
    )

    # 加权总分：可行性40%、预算20%、节奏20%、多样性20%
    overall_score = round(
        feasibility.score * 0.4 +
        budget.score * 0.2 +
        pace.score * 0.2 +
        diversity.score * 0.2,
        1
    )

    # 决定下一步
    max_iter = state.get("max_iterations", 3)
    if overall_score >= 85 or iteration >= max_iter - 1:
        next_node = "final_optimizer"
    else:
        next_node = "refiner"

    # 更新 critiques 和 scores
    new_critiques = state.get("critiques", []) + [
        f"【可行性 {feasibility.score}】{feasibility.feedback}",
        f"【预算 {budget.score}】{budget.feedback}",
        f"【节奏 {pace.score}】{pace.feedback}",
        f"【多样性 {diversity.score}】{diversity.feedback}"
    ]

    new_scores = state.get("scores", []) + [{
        "iteration": iteration,
        "overall_score": overall_score,
        "feasibility": feasibility.score,
        "budget": budget.score,
        "pace": pace.score,
        "diversity": diversity.score
    }]

    logger.info(f"[Critic] 第{iteration}次评审完成 | 总分: {overall_score} | 下一步: {next_node}")

    return {
        "critiques": new_critiques,
        "scores": new_scores,
        "draft_plan": draft,
        "iteration": iteration + 1,
        "next": next_node,
    }
