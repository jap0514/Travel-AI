from langchain_core.messages import SystemMessage
from app.agents.base import llm, get_tools
from langchain.agents import create_agent
from app.utils.redis_client import get_user_profile
import json


async def researcher_node(state):
    """2. Researcher"""
    task = state["task"]
    user_id = state.get("user_id", 0)
    tools = await get_tools()

    # 读取用户 Profile
    profile = get_user_profile(user_id)
    profile_text = json.dumps(profile, ensure_ascii=False) if profile else "暂无"

    prompt = f"""你是一位经验丰富的旅行研究专家，擅长为不同预算和节奏的旅客提供实用、高价值的信息。

    **任务**：
    为以下旅行需求收集最新、最实用的研究信息，你可以使用tools里面的工具来完成。

    **【用户结构化偏好（Profile）】**：
    {profile_text}

    **可用工具**：
    - search_classic_routes(destination, days, limit) ：获取经典行程模板
    - search_attractions(city, keyword, limit) ：获取景点历史、亮点、贴士
    - search_user_plans(destination, preferences, limit) ：获取真实用户行程
    - hybrid_search(query, collection_type, city, limit, alpha) ：混合检索（向量+关键词），适合 RAG 查询。collection_type 可选: attractions / classic_routes / user_plans
    - list_collections() ：列出所有可用的 collection
    - get_collection_stats(knowledge_type) ：获取 collection 统计信息（向量数量、维度等）
    - get_knowledge_by_id(knowledge_type, point_id) ：根据 ID 查询单条知识详情
    - search_weather(city, date) ：查询城市天气
    - search_hotels(city, checkin, checkout, budget) ：查询酒店推荐
    - search_flights(departure, destination, date) ：查询航班信息

    **旅行需求**：
    - 目的地：{task.destination or '用户指定城市'}
    - 天数：{task.days}天
    - 预算水平：{task.budget}
    - 节奏：{task.pace}

    **你需要重点研究的内容**：
    1. 核心必去景点及特色体验（参考 Profile 中的 travel_style 和 preferred_foods）
    2. 门票价格、开放时间、预订建议
    3. 交通方式推荐（含机场/高铁接驳）
    4. 当地美食推荐及人均消费（参考 Profile 中的 dietary_restrictions 和 preferred_foods）
    5. 最佳游玩顺序建议
    6. 天气与穿着建议
    7. 安全注意事项与文化禁忌
    8. 预算分配建议（住宿、餐饮、交通、门票）

    **输出要求**：
    提供结构清晰、信息量大、实用性强的研究报告。请使用Markdown格式组织内容。
    """

    # 创建 ReAct Agent（自动循环：LLM → Tool → LLM）
    agent = create_agent(
        model=llm,
        tools=tools,
        system_prompt=prompt
    )

    result = await agent.ainvoke({
        "messages": state["messages"][-1:]  # 只传入最新用户消息
    })

    final_content = result["messages"][-1].content

    return {
        "research_results": final_content,
        "messages": state["messages"]+ result["messages"]
    }