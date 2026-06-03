from typing import TypedDict, Annotated, Optional
import operator

from app.model.task_model import TravelTask
from app.model.plan_model import TravelPlan

# 自定义 reducer，这样 Langgraph就可以按照我reducer里面的规则来处理当多个节点都需要修改的同一字段
def last_value(left,right):
    """如果有新的值就用新值，否则保留旧值"""
    if right is not None:
        return right
    return left

class AgentState(TypedDict):
    messages: Annotated[list, operator.add]
    user_id: int
    session_id: int
    msg_id: int
    trace_id: str
    task: Optional[TravelTask]
    research_results: Optional[str]
    draft_plan: Annotated[Optional[str],last_value]=None
    final_plan: Annotated[Optional[str],last_value]=None
    parsed_plan: Optional[TravelPlan]
    next: Annotated[str, last_value] = "supervisor"   # 用于 Supervisor 路由
    critiques: Annotated[list[str], operator.add]  # 历次批评意见
    scores: Annotated[list[dict], operator.add]  # 每次评分结果
    iteration: int = 0  # 当前迭代次数
    max_iterations: int = 3  # 最大迭代次数
    should_end: Annotated[bool, last_value] = False  # 用来判断是否结束
    intent: Optional[str]  # "plan" or "qa"
    qa_answer: Optional[str]  # 最终返回的普通问答内容