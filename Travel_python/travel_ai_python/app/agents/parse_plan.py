from langchain_core.messages import SystemMessage
from app.config.logger import logger
from app.model.plan_model import TravelPlan, DailyActivity
from app.agents.base import llm

def parse_plan_node(state):
    """5. Parse Plan - 结构化解析"""
    final_plan = state.get("final_plan", "")
    task = state["task"]
    user_id = state.get("user_id", 0)

    parse_prompt = f"""你是一个专业的行程结构化助手。请将下面的旅行计划解析成严格的 JSON 格式，不要添加任何其他内容。

行程内容：
{final_plan}

请严格按照以下 JSON 格式输出：
{{
  "title": "北京10日深度游",
  "destination": "北京",
  "daily_plans": [
    {{
      "day": 1,
      "theme": "抵达适应",
      "activities": ["入住酒店", "前门大街漫步"],
      "location": "前门/大栅栏",
      "transportation": "地铁",
      "estimated_cost": 300
    }}
  ]
}}"""

    try:

        structured_llm = llm.with_structured_output(dict, method="json_mode")
        parsed_dict = structured_llm.invoke([SystemMessage(content=parse_prompt)])

        daily_plans = []
        for item in parsed_dict.get("daily_plans", []):
            daily_plans.append(DailyActivity(
                day=item.get("day", 1),
                theme=item.get("theme", ""),
                activities=item.get("activities", []),
                location=item.get("location", ""),
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
        logger.info(f"最后的解析完的计划：{parsed_plan}")
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