from langchain_core.messages import HumanMessage
from app.config.logger import logger
from app.model.plan_model import TravelPlan, DailyActivity, Activity
from app.agents.base import llm


def parse_plan_node(state):
    """5. Parse Plan - 结构化解析"""
    final_plan = state.get("final_plan", "")
    task = state["task"]
    user_id = state.get("user_id", 0)

    # LLM Prompt - 详细的 activities 格式
    parse_prompt = f"""你是一个专业的行程结构化助手。请将下面的旅行计划解析成严格的 JSON 格式，不要添加任何其他内容。

行程内容：
{final_plan}

请严格按照以下 JSON 格式输出，注意 activities 是对象数组，每个 activity 都要有详细字段：
{{
  "title": "上海10日深度游",
  "destination": "上海",
  "daily_plans": [
    {{
      "day": 1,
      "theme": "抵达适应",
      "activities": [
        {{
          "name": "上海博物馆",
          "time": "9:00 - 12:00",
          "description": "欣赏古代青铜器和书画",
          "location": "上海市黄浦区人民大道201号",
          "transportation": "地铁1号线",
          "cost": 0
        }},
        {{
          "name": "素食午餐",
          "time": "12:00 - 13:00",
          "description": "品尝当地素食",
          "location": "博物馆附近餐厅的具体地址",
          "transportation": "步行",
          "cost": 60
        }}
      ],
      "location": "上海市中心",
      "transportation": "地铁",
      "estimated_cost": 60
    }}
  ]
}}"""

    try:
        structured_llm = llm.with_structured_output(dict, method="json_mode")
        parsed_dict = structured_llm.invoke([HumanMessage(content=parse_prompt)])

        daily_plans = []
        for item in parsed_dict.get("daily_plans", []):
            # activities 是对象数组，需要转换成 Activity 对象列表
            activity_list = []
            for act in item.get("activities", []):
                activity_list.append(Activity(
                    name=act.get("name", ""),
                    time=act.get("time"),
                    description=act.get("description"),
                    location=act.get("location"),
                    transportation=act.get("transportation"),
                    cost=act.get("cost")
                ))

            daily_plans.append(DailyActivity(
                day=item.get("day", 1),
                theme=item.get("theme", ""),
                activities=activity_list,
                location=item.get("location"),
                transportation=item.get("transportation"),
                estimated_cost=item.get("estimated_cost")
            ))

        parsed_plan = TravelPlan(
            user_id=user_id,
            days=task.days,
            budget=task.budget,
            pace=task.pace,
            title=parsed_dict.get("title", f"{task.user_query} 行程计划"),
            destination=parsed_dict.get("destination", "北京"),
            daily_plans=daily_plans,
            raw_markdown=final_plan
        )

        logger.info(f"✅ Plan 结构化解析成功 | 共 {parsed_plan.days} 天")

    except Exception as e:
        logger.warning(f"Plan 结构化解析失败: {e}")
        parsed_plan = TravelPlan(
            user_id=user_id,
            days=task.days,
            budget=task.budget,
            pace=task.pace,
            title=f"{task.user_query} 行程计划",
            daily_plans=[],
            raw_markdown=final_plan
        )

    return {
        "parsed_plan": parsed_plan,
        "final_plan": final_plan,
        "task": task,
        "user_id": user_id,
        "session_id": state.get("session_id")
    }
