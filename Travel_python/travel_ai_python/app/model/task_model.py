# app/model/task_model.py
from dataclasses import dataclass
from typing import Optional

@dataclass
class TravelTask:
    task_id: int
    trace_id: str
    user_id: int
    user_query: str
    days: int
    budget: str
    pace: str
    # 结果字段
    plan_id: Optional[int] = None
    error_msg: Optional[str] = None
    result_status: Optional[str] = None
    destination: str = "未知城市"