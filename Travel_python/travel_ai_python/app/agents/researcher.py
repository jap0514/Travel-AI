# -*- coding: utf-8 -*-
"""
Researcher 节点 - 收集旅行相关信息

角色：旅行研究专家
输入：用户任务（目的地、天数、预算、节奏）
输出：研究报告（景点、交通、美食、天气等）
"""
from langchain_core.messages import SystemMessage
from app.agents.base import llm, get_tools, get_mcp_semaphore
from langchain.agents import create_agent
from app.utils.redis_client import get_user_profile
from app.config.logger import logger
import json


ROLE_DEFINITION = """
## 角色：资深旅行研究专家

### 核心技能
- 收集全面的目的地信息
- 查询真实有效的旅行数据
- 分析性价比和实用性
- 结合用户偏好推荐

### 能力范围
- 历史/景点/美食/交通/天气
- 当地文化/安全/礼仪
- 预订/门票/路线
"""

TOOLS_DESCRIPTION = """
## 可用工具（必看）

### RAG 知识库搜索
1. search_classic_routes(destination, days, limit=3)
   - 获取经典行程模板
   - 参考真实用户行程

2. search_attractions(city, keyword, limit=5)
   - 查询景点详情
   - 获取门票/开放时间/特色

3. search_user_plans(destination, preferences, limit=3)
   - 获取真实用户行程
   - 参考用户评价

4. hybrid_search(query, collection_type, city, limit=5, alpha=0.7)
   - 混合检索（向量+关键词）
   - collection_type: attractions/classic_routes/user_plans

5. get_knowledge_by_id(knowledge_type, point_id)
   - 根据ID查询详情

### 外部API查询
6. search_weather(city, date)
   - 查询天气预报
   - 用于行程安排参考

7. search_hotels(city, checkin, checkout, budget)
   - 查询酒店推荐

8. search_flights(departure, destination, date)
   - 查询航班信息
"""

RESEARCH_REQUIREMENTS = """
## 研究重点

### 必须研究的内容
1. 目的地核心景点（必去/推荐/小众）
2. 景点门票价格和开放时间
3. 交通方式（外部交通+城内交通）
4. 当地特色美食和餐厅（必须包含：店名+地址+人均价格）
5. 住宿推荐（符合预算）
6. 天气预报（出行参考）

### 重要：餐饮研究必须具体
- 每餐必须推荐1-2个具体餐厅名称
- 必须包含完整地址（XX区XX路XX号）
- 必须包含人均价格区间
- 示例格式：「烧烤」- 大漠烤肉（张店区世纪路123号，人均80元）

### 可选研究内容
1. 当地文化禁忌
2. 安全注意事项
3. 最佳游览顺序
4. 省钱攻略
"""

OUTPUT_FORMAT = """
## 输出规范

### 内容要求
1. 信息真实可靠
2. 符合用户预算和节奏
3. 包含实用 Tips
4. 标注信息来源（可选）

### 格式要求
- 使用 Markdown 组织
- 分段落呈现
- 重点信息加粗
- 列表清晰有序

### 禁止
- 不确定的信息不要编造
- 不添加与目的地无关的内容
- 不过度堆砌信息
"""


async def researcher_node(state):
    """
    Researcher 节点 - 收集旅行研究信息

    输入：task（目的地、天数、预算、节奏）
    输出：research_results（研究报告）
    """
    task = state["task"]
    user_id = state.get("user_id", 0)
    tools = await get_tools()

    # 获取用户偏好
    profile = get_user_profile(user_id)
    profile_text = json.dumps(profile, ensure_ascii=False) if profile else "暂无"

    prompt = f"""{ROLE_DEFINITION}

## 用户任务
- 目的地：{getattr(task, 'destination', '未知')}
- 天数：{getattr(task, 'days', 3)}天
- 预算：{getattr(task, 'budget', '中等')}
- 节奏：{getattr(task, 'pace', '适中')}
- 核心诉求：{getattr(task, 'user_query', '')}

## 用户偏好
{profile_text}

{TOOLS_DESCRIPTION}

{RESEARCH_REQUIREMENTS}

{OUTPUT_FORMAT}

## 输出
请基于用户任务进行全面研究，使用工具收集真实信息，输出实用研究报告。"""

    logger.info(f"[Researcher] 开始研究 {getattr(task, 'destination', '')}")

    # 创建 ReAct Agent
    agent = create_agent(
        model=llm,
        tools=tools,
        system_prompt=prompt
    )

    # 使用信号量限制 MCP 工具并发调用，避免多请求时 MCP SSE 连接冲突
    mcp_semaphore = get_mcp_semaphore()
    async with mcp_semaphore:
        result = await agent.ainvoke({
            "messages": state["messages"][-1:]
        })

    final_content = result["messages"][-1].content

    logger.info(f"[Researcher] 研究完成，字数: {len(final_content)}")

    return {
        "research_results": final_content,
        "messages": state["messages"] + result["messages"]
    }
