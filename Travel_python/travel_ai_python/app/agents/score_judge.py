from app.agents.state import AgentState

def score_judge_node(state: AgentState):
    scores = state.get("scores", [])
    if not scores:
        return {"next": "refiner"}

    latest_score = scores[-1]["overall_score"]
    iteration = state.get("iteration", 0)
    max_iter = state.get("max_iterations", 3)

    if latest_score >= 85 or iteration >= max_iter:
        # 达标或达到上限 → 进入最终优化
        return {"next": "final_optimizer"}
    else:
        return {"next": "refiner"}  # 继续修正