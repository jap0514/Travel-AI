from langgraph.graph import StateGraph, START, END
from langgraph.prebuilt import ToolNode, tools_condition
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI
from pydantic import BaseModel, Field
from dataclasses import asdict

from app.model.plan_model import TravelPlan, DailyActivity
from app.model.task_model import TravelTask
from app.tools.mcp_tools import init_mcp_tools_async
from app.utils.redis_client import get_session_context, save_session_context
from app.utils.mem0_client import get_user_memories, add_to_memory
from app.config.settings import settings
from app.config.logger import logger


# ==================== LLM 初始化 ====================
llm = ChatOpenAI(
    model=settings.AI_MODEL_NAME,
    base_url=settings.AI_API_URL,
    api_key=settings.AI_API_KEY,
    temperature=0.3,
)


tools = init_mcp_tools_async()
tool_node = ToolNode(tools)


# ==================== Task 提取结构 ====================
class TravelTaskOutput(BaseModel):
    days: int = Field(..., description="旅行天数")
    budget: str = Field(..., description="预算水平：低/中/高 或 具体金额")
    pace: str = Field(..., description="节奏：轻松/适中/紧凑")
    user_query: str = Field(..., description="原始用户需求")


def extract_task_node(state):
    """第一步：提取结构化 Task"""
    user_content = state["messages"][-1].content
    user_id = state.get("user_id", 0)

    try:
        memories = get_user_memories(user_id)
        memories_text = "\n".join([m.get("memory", "") for m in memories])

        prompt = f"""你是一个严格的JSON输出器。请严格按照以下格式输出，不要添加任何其他文字、解释或代码块。

用户消息：{user_content}

输出示例：
{{
  "days": 10,
  "budget": "中等",
  "pace": "适中",
  "user_query": "请帮我规划一个北京10日游"
}}"""

        structured_llm = llm.with_structured_output(TravelTaskOutput, method="json_mode")
        task_output = structured_llm.invoke([SystemMessage(content=prompt)])

        task = TravelTask(
            task_id=state.get("msg_id", 0),
            trace_id=state.get("trace_id"),
            user_id=user_id,
            user_query=getattr(task_output, 'user_query', user_content),
            days=getattr(task_output, 'days', 5),
            budget=getattr(task_output, 'budget', "中等"),
            pace=getattr(task_output, 'pace', "适中"),
        )

    except Exception as e:
        logger.warning(f"Task 提取失败，使用默认值: {e}")
        task = TravelTask(
            task_id=state.get("msg_id", 0),
            trace_id=state.get("trace_id"),
            user_id=user_id,
            user_query=user_content,
            days=5,
            budget="中等",
            pace="适中",
        )

    return {
        "task": task,
        "messages": state["messages"] + [HumanMessage(content=f"已提取任务: {task.days}天 {task.budget}预算")],
        "user_id": user_id,
        "session_id": state.get("session_id")
    }


def generate_plan_node(state):
    """第二步：生成旅行计划"""
    # 防止 task 不存在
    if "task" not in state:
        logger.error("state 中缺少 task 字段，使用默认 Task")
        task = TravelTask(task_id=0, trace_id="", user_id=state.get("user_id", 0),
                         user_query="默认任务", days=5, budget="中等", pace="适中")
    else:
        task: TravelTask = state["task"]

    user_id = state.get("user_id", 0)

    try:
        memories = get_user_memories(user_id)
        memories_text = "\n".join([m.get("memory", "") for m in memories])
    except:
        memories_text = ""

    system_prompt = f"""你是一位专业的旅行规划师。
用户长期偏好：{memories_text or "暂无"}

当前任务：
- 天数：{task.days}天
- 预算：{task.budget}
- 节奏：{task.pace}
- 需求：{task.user_query}

请生成详细、实用的旅行计划。"""

    response = llm.bind_tools(tools).invoke(
        [SystemMessage(content=system_prompt)] + state.get("messages", [])
    )

    return {
        "messages": state.get("messages", []) + [response],
        "final_plan": response.content,
        "task": task,
        "user_id": user_id,
        "session_id": state.get("session_id")
    }


def parse_plan_node(state):
    """第三步：解析 Plan 为结构化数据"""
    final_plan = state.get("final_plan", "")
    task: TravelTask = state["task"]
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
        structured_llm = llm.with_structured_output(dict, method="json_mode")  # 先解析成 dict
        parsed_dict = structured_llm.invoke([SystemMessage(content=parse_prompt)])

        # 构建 TravelPlan 对象
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

        logger.info(f"✅ Plan 结构化解析成功 | 共 {len(daily_plans)} 天")

    except Exception as e:
        logger.warning(f"Plan 结构化解析失败: {e}，使用简化版本")
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


# ==================== 构建 LangGraph ====================
workflow = StateGraph(dict)

workflow.add_node("extract_task", extract_task_node)
workflow.add_node("generate_plan", generate_plan_node)
workflow.add_node("parse_plan", parse_plan_node)
workflow.add_node("tools", tool_node)

workflow.set_entry_point("extract_task")
workflow.add_edge("extract_task", "generate_plan")
workflow.add_conditional_edges("generate_plan", tools_condition)
workflow.add_edge("tools", "generate_plan")
workflow.add_edge("generate_plan", "parse_plan")
workflow.add_edge("parse_plan", END)

travel_agent = workflow.compile()


# ==================== 主入口函数 ====================
def process_with_agent(chat_message, trace_id):
    log = logger.bind(trace_id=trace_id)

    try:
        session_messages = get_session_context(chat_message.session_id)
        session_messages.append({"role": chat_message.role, "content": chat_message.content})

        # 执行 Agent
        result = travel_agent.invoke({
            "messages": [HumanMessage(content=chat_message.content)],
            "user_id": chat_message.user_id,
            "session_id": chat_message.session_id,
            "msg_id": chat_message.msg_id,
            "trace_id": trace_id
        })

        final_plan = result.get("final_plan", "生成计划失败")
        parsed_plan: TravelPlan = result.get("parsed_plan")
        task = result.get("task")

        # 保存记录
        session_messages.append({"role": "assistant", "content": final_plan})
        save_session_context(chat_message.session_id, session_messages)
        # 得到的plan ，不能直接存入mem0，因为有范围限制，不能太长。所以需要将plan的对象存进去，而不是完整的plan
        # 暂时只保存摘要到 Mem0
        add_to_memory([
            {"role": "user", "content": chat_message.content},
            {"role": "assistant", "content": f"已生成{task.days}天行程计划"}
        ], chat_message.user_id)

        log.info(f"✅ Agent 处理完成 | {task.days if task else 5}天行程生成成功")
        return task, final_plan, parsed_plan

    except Exception as e:
        logger.exception(f"Agent 处理失败: {e}")
        raise