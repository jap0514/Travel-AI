from app.agents.base import llm, get_tools
from app.agents.state import AgentState
from app.config.logger import logger
from langchain_core.messages import HumanMessage
from app.tools.mcp_tools import init_mcp_tools_async
from app.utils.redis_client import get_user_profile, get_session_context
from app.utils.mem0_client import get_user_memories
import json
import re


async def rewrite_query(original_query: str, state: AgentState) -> str:
    """RAG 查询改写：让查询更清晰、完整、更适合向量检索"""
    task = state.get("task")

    prompt = f"""你是一个专业的查询优化专家。请将用户的旅行相关问题改写成更适合向量数据库检索的形式。

要求：
- 更正式、完整、具体
- 保留核心意图
- 增加关键旅行要素（目的地、时间、类型等）
- 长度适中（1-2句话）

原始查询：{original_query}

上下文（如果有）：{task.user_query if task else ''}

请直接输出改写后的查询，不要解释。"""

    try:
        response = await llm.ainvoke([HumanMessage(content=prompt)])
        rewritten = response.content.strip()
        return rewritten if len(rewritten) > 5 else original_query
    except Exception as e:
        logger.warning(f"查询改写失败: {e}")
        return original_query

TOOLS_DESCRIPTION = """
可用工具：
1. search_attractions(city, keyword, limit=5) - 查询景点的历史、亮点、贴士。
2. search_user_plans(destination, preferences, limit=3) - 查找历史用户的真实行程规划。
3. search_classic_routes(destination, days, limit=3) - 查找经典的行程模板（如北京3日游）。
4. hybrid_search(query, collection_type, city, limit, alpha) - 混合检索（向量+关键词），推荐用于 RAG 查询。可选 collection_type: attractions / classic_routes / user_plans。
5. list_collections() - 列出所有可用的 collection 名称。
6. get_collection_stats(knowledge_type) - 获取指定 collection 的统计信息（向量数量、维度等）。knowledge_type 可选: classic_route / attraction / user_plan。
7. get_knowledge_by_id(knowledge_type, point_id) - 根据 ID 查询单条知识详情。
8. search_weather(city, date=None) - 查询城市天气。
9. search_flights(departure, destination, date) - 查询航班信息。
10. search_hotels(city, checkin, checkout, budget) - 查询酒店推荐。
11. 如果不适合以上任何工具，返回 {"tool": "none"}。
"""


async def general_qa_node(state: AgentState):
    user_query = state["messages"][-1].content
    user_id = state.get("user_id", 0)
    session_id = state.get("session_id", 0)
    logger.info(f"General QA 处理用户问题: {user_query}")

    # ========== 读取三层记忆 ==========
    profile = get_user_profile(user_id)
    profile_text = json.dumps(profile, ensure_ascii=False) if profile else "暂无"

    memories = get_user_memories(user_id)
    memories_text = "\n".join([m.get("memory", "") for m in memories]) if memories else "暂无"

    session_msgs = get_session_context(session_id)
    session_text = "\n".join([f"{m.get('role')}: {m.get('content')}" for m in session_msgs[-6:]]) if session_msgs else "暂无"

    # ========== 查询改写 ==========
    rewritten_query = await rewrite_query(user_query, state)
    logger.info(f"原始查询: {user_query} → 改写后: {rewritten_query}")

    # ========== 提取查询信息 + 调用工具 ==========
    tools = await get_tools()
    tool_dict = {tool.name: tool for tool in tools}

    extraction_prompt = f"""你是一个旅行助手决策器。根据用户问题，判断最适合调用哪个工具，并以 JSON 格式输出。
你可以使用的工具：
{TOOLS_DESCRIPTION}

【用户结构化偏好（Profile）】：{profile_text}
【Mem0 长期记忆】：{memories_text}

用户问题：{user_query}

输出格式（必须严格 JSON）：
- 如果要调用工具：{{"tool": "工具名称", "arguments": {{"参数名": "值"}} }}
- 如果不需要调用任何工具：{{"tool": "none"}}

注意：只输出 JSON，不要有其他文字。确保参数名与工具定义一致。
示例：
{{"tool": "search_attractions", "arguments": {{"city": "北京", "keyword": "著名景点", "limit": 5}}}}
{{"tool": "none"}}
"""

    try:
        response = llm.invoke([HumanMessage(content=extraction_prompt)])
        info = json.loads(response.content.strip())
        tool_name = info.get("tool")
        arguments = info.get("arguments", {})
    except Exception as e:
        logger.warning(f"解析查询信息失败: {e}，使用默认空值")
        tool_name = "none"
        arguments = {}

    context_parts = []

    if tool_name != "none" and tool_name in tool_dict:
        tool = tool_dict[tool_name]
        logger.info(f"调用工具：{tool_name}, 参数：{arguments}")
        if "days" in arguments and arguments["days"] is not None:
            try:
                arguments["days"] = int(arguments["days"])
            except (ValueError, TypeError):
                pass
        if "limit" in arguments and arguments["limit"] is not None:
            try:
                arguments["limit"] = int(arguments["limit"])
            except (ValueError, TypeError):
                pass

        try:
            result = await tool.ainvoke(arguments)
            if result and "未找到" not in result:
                context_parts.append(f"【{tool_name} 结果】\n{result}")
            else:
                logger.info(f"工具 {tool_name} 返回空结果或未找到")
        except Exception as e:
            logger.warning(f"调用工具 {tool_name} 失败: {e}")
    else:
        logger.info("LLM 决定不调用工具，将使用自身知识回答")

    context = "\n\n".join(context_parts) if context_parts else ""

    # ========== 生成最终回答 ==========
    final_prompt = f"""你是一位亲切、专业的旅行助手。请基于以下检索到的信息，结合用户偏好，回答用户的问题。
- 如果信息足以回答，请组织成流畅、有用的回复（可以使用列表或小标题）。
- 如果信息不足，请礼貌地说"暂未找到相关答案，建议你提供更详细的关键词或查看官方渠道"。
- 不要编造信息。
- 如果用户提到与历史偏好相关的内容，可以适当参考。

【用户结构化偏好（Profile）】：{profile_text}
【Mem0 长期记忆】：{memories_text}
【当前会话上下文】：{session_text}

用户问题：{user_query}

检索到的信息：
{context if context else "（无直接相关检索结果）"}

回答："""
    answer = llm.invoke([HumanMessage(content=final_prompt)]).content

    logger.info(f"General QA 回答已生成，长度 {len(answer)}")
    return {"qa_answer": answer, "should_end": True}
