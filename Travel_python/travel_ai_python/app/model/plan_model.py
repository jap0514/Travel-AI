from dataclasses import dataclass, asdict
from typing import List, Optional
from datetime import date

@dataclass
class DailyActivity:
    day: int    # 第几天
    theme: str  # 主题
    activities: List[str]          # 主要活动列表
    location: str   # 位置
    transportation: Optional[str] = None   # 交通方式
    meals: Optional[List[str]] = None      # 吃什么
    tips: Optional[str] = None             # 提示
    estimated_cost: Optional[int] = None   # 预估成本


@dataclass
class TravelPlan:
    user_id: int    # 用户ID
    daily_plans: List[DailyActivity]  # 每天的计划
    title: str    # 总的主题
    days: int     # 总天数
    budget: str   # 预算
    pace: str     # 旅游形式（轻松、愉快）
    task_id: Optional[int] = None    # 任务ID
    plan_id: Optional[int] = None    # 计划ID
    destination: str = "北京"         # 旅游目的地
    start_date: Optional[date] = None   # 开始时间
    total_estimated_cost: Optional[int] = None   # 总的预估成本
    notes: Optional[str] = None
    raw_markdown: str = ""          # 保留原始文本用于展示

    def to_dict(self):
        data = asdict(self)
        if self.start_date:
            data['start_date'] = self.start_date.isoformat()
        return data