from dataclasses import dataclass, asdict
from typing import List, Optional
from datetime import date


# ---------- 新增：单个活动模型 ----------
@dataclass
class Activity:
    name: str  # 活动名称（如“上海博物馆”）
    time: Optional[str] = None  # 时间段（如“9:00 - 12:00”）
    description: Optional[str] = None  # 活动描述
    location: Optional[str] = None  # 具体地点（如“黄浦区人民大道201号”）
    transportation: Optional[str] = None  # 前往该地点的交通方式
    cost: Optional[int] = None  # 单个活动预估花费（可选）


# ---------- 每日行程 ----------
@dataclass
class DailyActivity:
    day: int
    theme: str
    activities: List[Activity]  # 【核心改动】现在是对象列表，不再是字符串列表

    # 以下字段为“当日摘要”，方便快速概览（与每个活动的细节可并存）
    location: Optional[str] = None  # 当日主要活动区域（如“市中心”）
    transportation: Optional[str] = None  # 当日主要交通方式（如“地铁”）

    meals: Optional[List[str]] = None
    tips: Optional[str] = None
    estimated_cost: Optional[int] = None  # 当日总预估费用


# ---------- 整个旅行计划 ----------
@dataclass
class TravelPlan:
    user_id: int
    daily_plans: List[DailyActivity]
    title: str
    days: int
    budget: str  # 可保留为字符串（如“中等”），或改为 int
    pace: str  # 如“轻松”、“紧凑”
    task_id: Optional[int] = None
    plan_id: Optional[int] = None
    destination: str = "北京"  # 旅游目的地城市
    start_date: Optional[date] = None
    total_estimated_cost: Optional[int] = None
    notes: Optional[str] = None
    raw_markdown: str = ""

    def to_dict(self):
        data = asdict(self)
        if self.start_date:
            data['start_date'] = self.start_date.isoformat()
        return data