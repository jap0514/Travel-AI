from app.agents.state import AgentState
from app.config.logger import logger


# ==================== 原版路由逻辑（未加酒店预定和用户交互流程）====================
# def supervisor_node(state: AgentState):
#     logger.info(f"进入supervisor_node | 当前状态: {list(state.keys())}")
#
#     # 如果已有最终结果，直接解析
#     if state.get("final_plan"):
#         logger.info("✅ 已生成最终计划，下一步解析")
#         return {"next": "parse_plan"}
#
#     if state.get("should_end"):
#         return {"next": "parse_plan"}
#
#     # 线性单次流程（不再循环）
#     if "task" not in state or state.get("task") is None:
#         logger.info("下一步 → task_analyzer")
#         return {"next": "task_analyzer"}
#
#     elif "research_results" not in state or not state.get("research_results"):
#         logger.info("下一步 → researcher")
#         return {"next": "researcher"}
#
#     elif "draft_plan" not in state or state.get("draft_plan") is None:
#         logger.info("下一步 → planner")
#         return {"next": "planner"}
#
#     else:
#         # 关键修改：planner完成后直接进入最终优化，不再进入critic循环
#         logger.info("下一步 → final_optimizer（单次高质量生成）")
#         return {"next": "final_optimizer"}


# ==================== 第一次改版路由逻辑（未加酒店预定和用户交互流程）====================
# def supervisor_node(state: AgentState):
#     logger.info(f"进入supervisor_node")
#
#     # 优先检查 should_end 标志，一旦设置为 True 就直接结束
#     if state.get("should_end"):
#         logger.info(f"下一步=parse_plan（should_end=True）")
#         return {"next": "parse_plan"}
#
#     if state.get("final_plan"):  # 已有最终结果
#         logger.info(f"下一步final_plan")
#         return {"next": "parse_plan"}
#
#     # 如果 critic 或其他节点已经设置了 next，优先使用它（重要！）
#     if state.get("next") in ["refiner", "final_optimizer", "parse_plan"]:
#         logger.info(f"下一步={state.get("next")}")
#         return {"next": state["next"]}
#
#     if "task" not in state or state.get("task") is None:
#         logger.info(f"下一步task_analyzer")
#         return {"next": "task_analyzer"}
#     elif "research_results" not in state or not state.get("research_results"):
#         logger.info(f"下一步researcher")
#         return {"next": "researcher"}
#     elif "draft_plan" not in state or state.get("draft_plan") is None:
#         logger.info(f"下一步planner")
#         return {"next": "planner"}
#     else:
#         # 默认进入 critic 进行评审
#         logger.info(f"下一步critic")
#         return {"next": "critic"}


# ==================== 酒店预定 + 用户交互流程版路由逻辑 ====================
def supervisor_node(state: AgentState):
    """
    Supervisor 路由节点 — 按优先级检查条件，决定下一步流向
    """
    logger.info(f"进入supervisor_node")

    interaction = state.get("interaction") or {}
    task = state.get("task")

    # 0. interaction_interrupted 为 True → 结束当前图执行，等待外部输入
    if interaction.get("interaction_interrupted"):
        logger.info(f"路由: 0-interaction_interrupted=True，结束图执行")
        return {"next": "END"}

    # 1. final_plan 已有 → parse_plan
    if state.get("final_plan"):
        logger.info(f"路由: 1-final_plan已有 → parse_plan")
        return {"next": "parse_plan"}

    # 2. interaction.status 以 "waiting_" 开头 → user_interaction
    if str(interaction.get("status", "")).startswith("waiting_"):
        logger.info(f"路由: 2-waiting状态 → user_interaction (status={interaction.get('status')})")
        return {"next": "user_interaction"}

    # 3. needs_replan 为 True → task_analyzer（并清空已有 state）
    if state.get("needs_replan"):
        logger.info(f"路由: 3-needs_replan=True → task_analyzer（清空state）")
        cleared_state = {
            key: None for key in [
                "task", "research_results", "draft_plan", "final_plan",
                "parsed_plan", "critiques", "scores", "interaction",
                "needs_replan", "booking_status"
            ]
        }
        cleared_state["next"] = "task_analyzer"
        return cleared_state

    # 4. task 不存在 → task_analyzer
    if not task:
        logger.info(f"路由: 4-task不存在 → task_analyzer")
        return {"next": "task_analyzer"}

    # 5. research_results 不存在 → researcher
    if not state.get("research_results"):
        logger.info(f"路由: 5-research_results不存在 → researcher")
        return {"next": "researcher"}

    # 6. hotel_select 类型但 chosen 为空 → user_interaction
    interaction_type = interaction.get("type", "")
    if interaction_type == "hotel_select" and not interaction.get("chosen"):
        logger.info(f"路由: 6-hotel_select但chosen为空 → user_interaction")
        return {"next": "user_interaction"}

    # 7. 用户选了酒店且 booking_status 非 confirmed → confirm_and_book（必须在 planner 之前）
    chosen = interaction.get("chosen")
    booking_status = state.get("booking_status")
    if chosen and booking_status != "confirmed":
        logger.info(f"路由: 7-已选酒店但未confirm → confirm_and_book (booking_status={booking_status})")
        return {"next": "confirm_and_book"}

    # 8. draft_plan 不存在 → planner
    if not state.get("draft_plan"):
        logger.info(f"路由: 8-draft_plan不存在 → planner")
        return {"next": "planner"}

    # 9. 评分次数未达上限 → critic
    scores = state.get("scores") or []
    iteration = state.get("iteration", 0)
    max_iterations = state.get("max_iterations", 3)
    if iteration < max_iterations:
        logger.info(f"路由: 9-迭代{iteration+1}/{max_iterations} → critic")
        return {"next": "critic"}

    # 10. 其他 → final_optimizer
    logger.info(f"路由: 10-默认 → final_optimizer")
    return {"next": "final_optimizer"}
