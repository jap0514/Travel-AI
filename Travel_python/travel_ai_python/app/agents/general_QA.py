from app.agents.base import llm, get_tools
from app.agents.state import AgentState
from app.config.logger import logger
from langchain_core.messages import SystemMessage
from app.tools.mcp_tools import init_mcp_tools_async
import json
import re

# 可用工具的描述（供 LLM 选择）
TOOLS_DESCRIPTION = """
可用工具：
1. search_attractions(city, keyword, limit=5) - 查询景点的历史、亮点、贴士。
2. search_user_plans(destination, preferences, limit=3) - 查找历史用户的真实行程规划。
3. search_classic_routes(destination, days, limit=3) - 查找经典的行程模板（如北京3日游）。
4. search_weather(city) - 查询城市天气。
5. 如果不适合以上任何工具，返回 {"tool": "none"}。
"""

async def general_qa_node(state: AgentState):
    user_query = state["messages"][-1].content
    logger.info(f"General QA 处理用户问题: {user_query}")

    # 1. 获取所有可用工具
    tools =await get_tools()
    # 工具名称映射，方便调用（假设工具是 callable 对象，具有 name 和 args_schema）
    tool_dict = {tool.name: tool for tool in tools}

    # 2. 让 LLM 提取查询关键信息（目的地、天数、关键词）
    extraction_prompt = f"""你是一个旅行助手决策器。根据用户问题，判断最适合调用哪个工具，并以 JSON 格式输出。
你可以使用的工具：
{TOOLS_DESCRIPTION}

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
        response = llm.invoke([SystemMessage(content=extraction_prompt)])
        info = json.loads(response.content.strip())
        tool_name=info.get("tool")
        arguments = info.get("arguments",{})
    except Exception as e:
        logger.warning(f"解析查询信息失败: {e}，使用默认空值")
        tool_name = "none"
        arguments = {}

    # 3. 根据信息调用相应工具，收集上下文
    context_parts = []

    if tool_name !="none" and tool_name in tool_dict:
        tool=tool_dict[tool_name]
        logger.info(f"调用工具：{tool_name},参数：{arguments}")
        if "days" in arguments and arguments["days"] is not None:
            try:
                # 参数类型转换，确保days为int类型
                arguments["days"] = int(arguments["days"])
            except(ValueError,TypeError):
                pass
        if "limit" in arguments and arguments["limit"] is not None:
            try:
                arguments["limit"] = int(arguments["limit"])
            except(ValueError,TypeError):
                pass

        try:
            result=await tool.ainvoke(arguments)
            if result and "未找到" not in result:
                context_parts.append(f"【{tool_name} 结果】\n{result}")
            else:
                logger.info(f"工具 {tool_name} 返回空结果或未找到")
        except Exception as e:
            logger.warning(f"调用工具 {tool_name} 失败: {e}")
    else:
        logger.info("LLM 决定不调用工具，将使用自身知识回答")

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