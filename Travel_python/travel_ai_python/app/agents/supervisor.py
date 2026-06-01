from app.agents.state import AgentState
from app.config.logger import logger


def supervisor_node(state: AgentState):
    logger.info(f"进入supervisor_node | 当前状态: {list(state.keys())}")

    # 如果已有最终结果，直接解析
    if state.get("final_plan"):
        logger.info("✅ 已生成最终计划，下一步解析")
        return {"next": "parse_plan"}

    if state.get("should_end"):
        return {"next": "parse_plan"}

    # 线性单次流程（不再循环）
    if "task" not in state or state.get("task") is None:
        logger.info("下一步 → task_analyzer")
        return {"next": "task_analyzer"}

    elif "research_results" not in state or not state.get("research_results"):
        logger.info("下一步 → researcher")
        return {"next": "researcher"}

    elif "draft_plan" not in state or state.get("draft_plan") is None:
        logger.info("下一步 → planner")
        return {"next": "planner"}

    else:
        # 关键修改：planner完成后直接进入最终优化，不再进入critic循环
        logger.info("下一步 → final_optimizer（单次高质量生成）")
        return {"next": "final_optimizer"}




# def supervisor_node(state: AgentState):
#     logger.info(f"进入supervisor_node")
#     if state.get("final_plan"):  # 已有最终结果
#         logger.info(f"下一步final_plan")
#         return {"next": "parse_plan"}   # 添加这个，不让一直循环
#
#     if state.get("should_end"):   # 双重保险
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