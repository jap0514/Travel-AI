# -*- coding: utf-8 -*-
"""
Parse Plan 节点 - 将行程解析为结构化数据

角色：结构化数据专家
输入：Markdown 行程文本
输出：TravelPlan + DailyActivity + Activity 对象
"""
from langchain_core.messages import HumanMessage
from app.agents.base import llm
from app.model.plan_model import TravelPlan, DailyActivity, Activity
from app.agents.state import AgentState
from app.config.logger import logger


ROLE_DEFINITION = """
## 角色：专业行程结构化解析专家

### 核心技能
- 精准提取行程信息
- 结构化数据建模
- 数据完整性保证

### 能力
- 识别日期、时间、活动
- 提取地点名称和地址
- 估算费用和时长
- 分类活动类型（景点/餐饮/交通/住宿）
"""

OUTPUT_FORMAT = """
## 输出格式要求

### TravelPlan（顶层）
- title：行程标题，如"广州3日游"
- destination：目的地城市，如"广州"
- daily_plans：每日计划列表

### DailyActivity（每日）
- day：第几天（数字）
- theme：当日主题，如"历史文化探索"
- activities：当日活动列表（Activity对象）

### Activity（每个活动）
- name：活动/景点名称（必填）
- time：时间段，如"9:00-12:00"（可选）
- description：简要描述（可选）
- location：具体地址，如"广州市越秀区人民北路"（用于地图显示）
- transportation：交通方式，如"地铁1号线"（可选）
- cost：预估费用/门票（可选），**必须是数字类型（int或float），不能是字符串或区间**

### 约束
- activities 是对象数组，不是字符串数组
- location 要具体，方便地图显示
- 每个活动都要有 name
- **cost 必须是具体数字，不允许出现"35-50"、"约100"等字符串或区间形式**
- **如果无法确定具体费用，cost 设为 null，不要用字符串**
"""

EXAMPLES = """
## 正确示例

### activities 数组（正确）
"activities": [
  {"name": "陈家祠", "time": "9:00-12:00", "description": "广东最著名的祠堂建筑", "location": "广州市荔湾区中山七路", "transportation": "地铁1号线", "cost": 10},
  {"name": "午餐", "time": "12:00-13:00", "location": "荔湾区上下九步行街", "cost": 50}
]

### 错误示例（activities 是字符串）
"activities": ["陈家祠", "午餐", "荔枝湾"]  # ❌ 应该是对象数组

### location 要具体
"location": "广州市越秀区"  # ✓ 正确
"location": "越秀区"         # ⚠️ 可以但不精确
"location": "市中心"         # ❌ 太模糊，地图无法定位
"""

NO_LOCATION_FALLBACK = """
### location 为空的处理
如果某个活动没有具体地址：
1. 优先填城市+区域，如"广州市天河区"
2. 如果是知名景点可不填（如"白云山"地图可识别）
3. 交通、餐饮等可填"就近安排"
"""


def parse_plan_node(state: AgentState):
    """
    Parse Plan 节点 - 结构化解析

    输入：final_plan（Markdown 行程文本）
    输出：parsed_plan（TravelPlan 对象）
    """
    final_plan = state.get("final_plan", "")
    task = state["task"]
    user_id = state.get("user_id", 0)

    prompt = f"""{ROLE_DEFINITION}

## 用户需求
- 目的地：{getattr(task, 'destination', '')}
- 天数：{getattr(task, 'days', 0)}天
- 预算：{getattr(task, 'budget', '中等')}
- 节奏：{getattr(task, 'pace', '适中')}

## 行程文本
{final_plan}

{OUTPUT_FORMAT}

{EXAMPLES}

{NO_LOCATION_FALLBACK}

## 输出要求
1. 严格按 JSON 格式输出
2. 不要有代码块标记
3. 不要有解释文字
4. activities 必须是对象数组
5. 每个 Activity 都要有 name
6. location 要尽量具体（用于地图显示）

直接输出 JSON 对象，不要其他内容。

重要：JSON 中的字符串值必须使用标准的英文双引号 " 和 "，不要使用中文全角引号「" "」或「' '」。否则会导致 JSON 解析失败。
"""

    parsed_dict = None

    # 尝试使用 json_mode 解析
    try:
        structured_llm = llm.with_structured_output(dict, method="json_mode")
        parsed_dict = structured_llm.invoke([HumanMessage(content=prompt)])
    except Exception as e:
        # 如果 json_mode 解析失败，尝试用正则提取并修复引号后重试
        # 原因：LLM 有时会在 JSON 中使用中文全角引号（" "）而非标准英文引号（" "），
        #       这会导致 json_mode 的 JSONParser 解析失败。备用方案通过字符串替换修复这个问题。
        import re
        import json as json_module
        logger.warning(f"[ParsePlan] json_mode 解析失败，尝试备用方案: {e}")
        try:
            # 重新调用 LLM 获取原始文本响应
            raw_response = llm.invoke([HumanMessage(content=prompt)])
            json_match = re.search(r'\{[\s\S]*\}', raw_response.content)
            if json_match:
                json_str = json_match.group()
                # 替换中文全角引号为标准英文引号，避免 JSON 解析失败
                # " " (U+201C/U+201D) -> " " (U+0022)
                # ' ' (U+2018/U+2019) -> ' ' (U+0027)
                json_str = json_str.replace(''', "'").replace(''', "'")
                json_str = json_str.replace('"', '"').replace('"', '"')
                parsed_dict = json_module.loads(json_str)
            else:
                raise ValueError("无法从响应中提取 JSON")
        except Exception as e2:
            logger.warning(f"[ParsePlan] 备用方案也失败: {e2}")
            parsed_dict = {"daily_plans": [], "title": "", "destination": ""}

    # 如果解析失败，使用空数据
    if parsed_dict is None:
        parsed_dict = {"daily_plans": [], "title": "", "destination": ""}

    # 转换为 DailyActivity 对象列表
    # 注意：这段代码在 try-except 外面，确保无论成功还是失败都会执行
    daily_plans = []
    for item in parsed_dict.get("daily_plans", []):
        # 转换 activities 数组为 Activity 对象列表
        activity_list = []
        for act in item.get("activities", []):
            if isinstance(act, dict):
                # cost 必须是数字或 None，不能是字符串区间
                cost_val = act.get("cost")
                if cost_val is not None:
                    try:
                        cost_val = float(cost_val) if isinstance(cost_val, str) else cost_val
                        # 如果是 .0 的 float 转成 int
                        cost_val = int(cost_val) if cost_val == int(cost_val) else cost_val
                    except (ValueError, TypeError):
                        cost_val = None  # 区间字符串如"35-50"无法转数字，设为null

                activity_list.append(Activity(
                    name=act.get("name", ""),
                    time=act.get("time"),
                    description=act.get("description"),
                    location=act.get("location"),
                    transportation=act.get("transportation"),
                    cost=cost_val
                ))
            elif isinstance(act, str):
                # 兼容字符串格式
                activity_list.append(Activity(name=act))

        # 计算当日总费用 = 所有活动的 cost 之和
        day_cost = sum(
            (act.cost for act in activity_list if act.cost is not None),
            0
        )

        daily_plans.append(DailyActivity(
            day=item.get("day", 1),
            theme=item.get("theme", ""),
            activities=activity_list,
            location=item.get("location"),
            transportation=item.get("transportation"),
            estimated_cost=day_cost if day_cost > 0 else None
        ))

    # 计算整个行程的总预估费用 = 每日费用之和
    total_cost = sum(
        (dp.estimated_cost for dp in daily_plans if dp.estimated_cost is not None),
        0
    )

    parsed_plan = TravelPlan(
        user_id=user_id,
        days=task.days,
        budget=task.budget,
        pace=task.pace,
        title=parsed_dict.get("title", f"{getattr(task, 'destination', '')}旅行计划"),
        destination=parsed_dict.get("destination", getattr(task, 'destination', '')),
        daily_plans=daily_plans,
        total_estimated_cost=total_cost if total_cost > 0 else None,
        raw_markdown=final_plan
    )

    logger.info(f"[ParsePlan] 解析成功，共 {len(parsed_plan.daily_plans)} 天")

    return {
        "parsed_plan": parsed_plan,
        "final_plan": final_plan,
        "task": task,
        "user_id": user_id,
        "session_id": state.get("session_id")
    }
