# -*- coding: utf-8 -*-
"""
General QA 节点 - 回答用户旅行相关问答

角色：旅行助手问答专家
输入：用户问题
输出：回答文本
"""
from app.agents.base import llm, get_tools
from app.agents.state import AgentState
from app.config.logger import logger
from langchain_core.messages import HumanMessage
from app.utils.redis_client import get_user_profile, get_session_context
from app.utils.mem0_client import get_user_memories
import json
import re


# ============================
# 角色定义
# ============================
ROLE_DEFINITION = """
## 角色：专业旅行助手

### 核心技能
- 回答旅行相关问题
- 理解用户偏好和上下文
- 调用工具获取准确信息
- 礼貌、清晰地表达

### 回答原则
- 有信息依据时：详细、有条理地回答
- 信息不足时：诚实说明，建议查看官方渠道
- 尊重用户偏好：参考 Profile 调整回答
- 不编造信息
"""

# ============================
# 工具描述
# ============================
TOOLS_DESCRIPTION = """
## 可用工具

### RAG 知识库
1. search_attractions(city, keyword, limit=5)
   - 查询景点历史、亮点、贴士
2. search_classic_routes(destination, days, limit=3)
   - 获取经典行程模板
3. search_user_plans(destination, preferences, limit=3)
   - 获取真实用户行程
4. hybrid_search(query, collection_type, city, limit=5, alpha=0.7)
   - 混合检索（向量+关键词）
   - collection_type: attractions/classic_routes/user_plans
5. get_collection_stats(knowledge_type)
   - 查看 collection 统计信息
6. get_knowledge_by_id(knowledge_type, point_id)
   - 根据 ID 查询单条详情

### 外部 API
7. search_weather(city, date=None)
   - 查询天气预报
8. search_hotels(city, checkin, checkout, budget)
   - 查询酒店推荐
9. search_flights(departure, destination, date)
   - 查询航班信息
"""

# ============================
# 输出格式
# ============================
OUTPUT_RULES = """
### 回答规范
- 有信息时：用 Markdown 组织，有条理地回答
- 信息不足时：诚实说"暂未找到"，建议用户查看官方渠道
- 结合 Profile：适当参考用户偏好
- 禁止：编造信息、模糊回答
"""

# ============================
# 查询改写
# ============================
QUERY_REWRITE_EXAMPLES = """
## 查询改写示例

原始：["长城多少钱"]
改写：["北京长城门票价格及预约方式"]

原始：["成都3日游推荐"]
改写：["成都3日经典旅游路线推荐"]

原始：["有什么好吃的"]
改写：["当地特色美食推荐"]
"""


async def rewrite_query(original_query: str, state: AgentState) -> str:
    """将用户问题改写成更适合检索的格式"""
    prompt = f"""
你是一个查询优化专家。将用户问题改写得更完整、更适合检索。

### 示例
{QUERY_REWRITE_EXAMPLES}

原始问题：{original_query}

直接输出改写后的问题，不要解释。
"""
    try:
        response = llm.invoke([HumanMessage(content=prompt)])
        rewritten = response.content.strip()
        return rewritten if len(rewritten) > 5 else original_query
    except Exception as e:
        logger.warning(f"查询改写失败: {e}")
        return original_query


# ============================
# 工具选择专家
# ============================
TOOL_SELECTION_EXPERT = """
## 角色：工具选择决策专家

### 职责
根据用户问题，判断是否需要调用工具，调用哪个工具

### 决策规则
- 问题涉及具体信息（价格、时间、开放日）→ 调用工具
- 纯闲聊或无法工具化的问题 → 输出 tool: none
- 问题中的参数尽量补全（如城市名）

### 输出格式
调用工具：
{{"tool": "search_attractions", "arguments": {{"city": "北京", "keyword": "景点", "limit": 5}}}

不调用：
{{"tool": "none"}}

注意：只输出 JSON，不要其他内容。
"""


async def general_qa_node(state: AgentState):
    """
    General QA 节点 - 回答旅行相关问答

    流程：
    1. 改写用户问题
    2. 判断是否调用工具
    3. 检索/调用 API
    4. 生成回答
    """
    user_query = state["messages"][-1].content
    user_id = state.get("user_id", 0)
    session_id = state.get("session_id", 0)

    logger.info(f"[GeneralQA] 用户问题: {user_query}")

    # ============================
    # 读取上下文
    # ============================
    profile = get_user_profile(user_id)
    profile_text = json.dumps(profile, ensure_ascii=False) if profile else "暂无"

    memories = get_user_memories(user_id)
    memories_text = "\n".join([m.get("memory", "") for m in memories]) if memories else "暂无"

    session_msgs = get_session_context(session_id)
    session_text = "\n".join([f"{m.get('role')}: {m.get('content')}" for m in session_msgs[-6:]]) if session_msgs else "暂无"

    # ============================
    # 改写查询
    # ============================
    rewritten_query = await rewrite_query(user_query, state)
    logger.info(f"[GeneralQA] 改写: {user_query} → {rewritten_query}")

    # ============================
    # 判断工具
    # ============================
    tools = await get_tools()
    tool_dict = {tool.name: tool for tool in tools}

    tool_selection_prompt = f"""{TOOL_SELECTION_EXPERT}

### 用户偏好 Profile
{profile_text}

### 上下文
当前问题：{user_query}
改写后：{rewritten_query}

### 工具列表
{TOOLS_DESCRIPTION}

直接输出 JSON 对象，不要其他内容。"""

    try:
        # 使用 json_mode 强制 JSON 输出，避免 LLM 返回单引号格式导致解析失败
        tool_response = llm.with_structured_output(dict, method="json_mode").invoke(
            [HumanMessage(content=tool_selection_prompt)]
        )
        info = tool_response
        tool_name = info.get("tool", "none") if info else "none"
        arguments = info.get("arguments", {}) if info else {}
    except Exception as e:
        logger.warning(f"[GeneralQA] 工具选择失败: {e}")
        tool_name = "none"
        arguments = {}

    # ============================
    # 调用工具
    # ============================
    context_parts = []

    if tool_name != "none" and tool_name in tool_dict:
        tool = tool_dict[tool_name]
        logger.info(f"[GeneralQA] 调用工具: {tool_name}, 参数: {arguments}")

        # 参数类型转换
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
            tool_result = await tool.ainvoke(arguments)
            if tool_result and "未找到" not in tool_result:
                context_parts.append(f"【{tool_name} 结果】\n{tool_result}")
            else:
                logger.info(f"[GeneralQA] 工具 {tool_name} 返回空")
        except Exception as e:
            logger.warning(f"[GeneralQA] 工具 {tool_name} 调用失败: {e}")
    else:
        logger.info(f"[GeneralQA] 不调用工具（tool={tool_name}）")

    context = "\n\n".join(context_parts) if context_parts else ""

    # ============================
    # 生成回答
    # ============================
    final_prompt = f"""{ROLE_DEFINITION}

### 用户偏好 Profile
{profile_text}

### 历史记忆
{memories_text}

### 当前会话
{session_text}

### 用户问题
{user_query}

### 检索/查询结果
{context if context else "（无相关结果）"}

{OUTPUT_RULES}

请回答用户问题。"""

    answer = llm.invoke([HumanMessage(content=final_prompt)]).content

    logger.info(f"[GeneralQA] 回答完成，字数: {len(answer)}")

    return {
        "qa_answer": answer,
        "should_end": True
    }
