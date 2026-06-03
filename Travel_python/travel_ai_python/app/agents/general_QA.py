from app.agents.base import llm
from app.agents.state import AgentState
from app.config.logger import logger
from langchain_core.messages import SystemMessage
from app.tools.mcp_tools import init_mcp_tools
import json
import re


async def general_qa_node(state: AgentState):
    user_query = state["messages"][-1].content
    logger.info(f"General QA 处理用户问题: {user_query}")

    # 1. 获取所有可用工具
    tools =init_mcp_tools()
    # 工具名称映射，方便调用（假设工具是 callable 对象，具有 name 和 args_schema）
    tool_dict = {tool.name: tool for tool in tools}

    # 2. 让 LLM 提取查询关键信息（目的地、天数、关键词）
    extraction_prompt = f"""你是一个查询解析助手。从用户问题中提取以下信息，以 JSON 格式输出：
{{
    "destination": "城市名（若无则为空字符串）",
    "days": 天数（若无则为 null）,
    "keyword": "核心景点或主题关键词（若无则为空字符串）"
}}

用户问题：{user_query}
只输出 JSON，不要有其他内容。"""

    try:
        response = llm.invoke([SystemMessage(content=extraction_prompt)])
        info = json.loads(response.content.strip())
        destination = info.get("destination", "")
        days = info.get("days")
        keyword = info.get("keyword", "")
    except Exception as e:
        logger.warning(f"解析查询信息失败: {e}，使用默认空值")
        destination = ""
        days = None
        keyword = ""

    # 3. 根据信息调用相应工具，收集上下文
    context_parts = []

    # 如果有目的地，尝试搜索经典路线
    if destination and days is not None:
        logger.info(f"经典行程查询")
        try:
            route_tool = tool_dict.get("search_classic_routes")
            if route_tool:
                result =await route_tool.ainvoke({"destination": destination, "days": days})
                if result and "未找到" not in result:
                    context_parts.append(f"【经典行程参考】\n{result}")
        except Exception as e:
            logger.warning(f"调用 search_classic_routes 失败: {e}")

    # 搜索景点信息（如果有目的地或关键词）
    if destination or keyword:
        logger.info(f"景点查询")
        try:
            attr_tool = tool_dict.get("search_attractions")
            if attr_tool:
                result =await attr_tool.ainvoke({"city": destination, "keyword": keyword, "limit": 5})
                if result and "未找到" not in result:
                    context_parts.append(f"【景点详情】\n{result}")
        except Exception as e:
            logger.warning(f"调用 search_attractions 失败: {e}")

    # 搜索历史用户行程（如果有目的地）
    if destination:
        logger.info(f"历史行程查询")
        try:
            user_plan_tool = tool_dict.get("search_user_plans")
            if user_plan_tool:
                result =await user_plan_tool.ainvoke({"destination": destination, "preferences": keyword, "limit": 3})
                if result and "未找到" not in result:
                    context_parts.append(f"【用户真实行程参考】\n{result}")
        except Exception as e:
            logger.warning(f"调用 search_user_plans 失败: {e}")

    # 若用户明确问天气，可调用天气工具
    if any(word in user_query for word in ["天气", "气温", "下雨"]):
        try:
            weather_tool = tool_dict.get("search_weather")
            if weather_tool and destination:
                result =await weather_tool.ainvoke({"city": destination})
                if result and "未配置" not in result:
                    context_parts.append(f"【天气信息】\n{result}")
        except Exception as e:
            logger.warning(f"调用 search_weather 失败: {e}")

    # 4. 如果没有任何上下文，给出兜底提示
    context = "\n\n".join(context_parts) if context_parts else ""

    # 5. 生成最终回答
    final_prompt = f"""你是一位亲切、专业的旅行助手。请基于以下检索到的信息，回答用户的问题。
- 如果信息足以回答，请组织成流畅、有用的回复（可以使用列表或小标题）。
- 如果信息不足，请礼貌地说“暂未找到相关答案，建议你提供更详细的关键词或查看官方渠道”。
- 不要编造信息。

用户问题：{user_query}

检索到的信息：
{context if context else "（无直接相关检索结果）"}

回答："""
    answer = llm.invoke([SystemMessage(content=final_prompt)]).content

    logger.info(f"General QA 回答已生成，长度 {len(answer)}")
    return {"qa_answer": answer, "should_end": True}