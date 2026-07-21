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
from app.config.settings import settings


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

7. selectEmptyRoom(city, startDate, days)
   - 查询城市指定日期范围内的有空房的酒店及房间

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

    输入：task（目的地、天数、预算、节奏）+ 前端传入的 start_date/days
    输出：research_results（研究报告）+ hotels（酒店列表）+ has_hotel_rooms
          若有酒店且有房：设置 interaction.status=waiting_user_hotel，等待用户选择
    """
    import requests
    from app.config.settings import settings

    task = state["task"]
    user_id = state.get("user_id", 0)
    tools = await get_tools()

    # 获取用户偏好
    profile = get_user_profile(user_id)
    profile_text = json.dumps(profile, ensure_ascii=False) if profile else "暂无"

    # 获取前端传入的日期参数
    start_date = state.get("start_date") or getattr(task, "start_date", None) or ""
    days = state.get("days") or getattr(task, "days", 3)
    destination = getattr(task, "destination", "未知")

    # 计算离店日期
    from datetime import datetime, timedelta
    checkout = ""
    if start_date:
        try:
            start = datetime.strptime(start_date, "%Y-%m-%d")
            end = start + timedelta(days=days)
            checkout = end.strftime("%Y-%m-%d")
        except ValueError:
            pass

    prompt = f"""{ROLE_DEFINITION}

## 用户任务
- 目的地：{destination}
- 天数：{days}天
- 预算：{getattr(task, 'budget', '中等')}
- 节奏：{getattr(task, 'pace', '适中')}
- 核心诉求：{getattr(task, 'user_query', '')}
- 入住日期：{start_date}
- 离店日期：{checkout}

## 用户偏好
{profile_text}

{TOOLS_DESCRIPTION}

{RESEARCH_REQUIREMENTS}

{OUTPUT_FORMAT}

## 输出
请基于用户任务进行全面研究，使用工具收集真实信息，输出实用研究报告（不包含酒店）。"""

    logger.info(f"[Researcher] 开始研究 {destination}，日期: {start_date}~{checkout}")

    # 创建 ReAct Agent（研究景点、交通、美食、天气等）
    agent = create_agent(
        model=llm,
        tools=tools,
        system_prompt=prompt
    )

    # 使用信号量限制 MCP 工具并发调用
    mcp_semaphore = get_mcp_semaphore()
    async with mcp_semaphore:
        result = await agent.ainvoke({
            "messages": state["messages"][-1:]
        })

    final_content = result["messages"][-1].content
    all_messages = state["messages"] + result["messages"]

    # ==================== 直接调用 Java 接口查询有空房的酒店 ====================
    hotels, has_hotel_rooms = _query_empty_rooms(destination, start_date, days)

    logger.info(f"[Researcher] 研究完成 | 酒店数量: {len(hotels)} | 有空房: {has_hotel_rooms}")

    return_dict = {
        "research_results": final_content,
        "messages": all_messages,
        "hotels": hotels,
        "has_hotel_rooms": has_hotel_rooms,
    }

    # 若有酒店且有房，设置交互状态，等待用户选择
    if hotels and has_hotel_rooms:
        from app.agents.user_interaction_node import handle_hotel_selection
        interaction_update = handle_hotel_selection(
            state=state,
            hotels=hotels,
            message="请从以下酒店列表中选择您心仪的酒店：",
        )
        return_dict["interaction"] = interaction_update["interaction"]
        logger.info(f"[Researcher] 设置 waiting_user_hotel，酒店数量: {len(hotels)}")
    elif not hotels:
        # 没有空房，设置 waiting_user_decision，让用户选择是否继续
        from app.agents.user_interaction_node import handle_no_hotel_decision
        interaction_update = handle_no_hotel_decision(
            state=state,
            message="很抱歉，当前没有查询到有空房的酒店。",
        )
        return_dict["interaction"] = interaction_update["interaction"]
        logger.info(f"[Researcher] 无空房酒店，设置 waiting_user_decision")

    return return_dict


def _query_empty_rooms(city: str, start_date: str, days: int) -> tuple:
    """
    直接调用 Java HTTP 接口查询有空房的酒店
    GET /hotel/hotelInfo/selectEmptyRoom?city=&startDate=&days=
    返回: (hotels: list, has_hotel_rooms: bool)
    """
    import requests
    if not city or not start_date:
        return [], False

    try:
        url = f"{settings.JAVA_API_BASE_URL}/hotel/hotelInfo/selectEmptyRoom"
        # 传 LocalDateTime 格式，Java 端用 LocalDateTime.parse 解析
        start_datetime = start_date + " 00:00:00"
        params = {
            "city": city,
            "startDate": start_datetime,
            "days": days,
        }
        response = requests.get(url, params=params, timeout=10)
        if response.status_code != 200:
            logger.warning(f"[Researcher] selectEmptyRoom 接口异常: status={response.status_code}")
            return [], False

        result = response.json()
        # 适配 Result<?> 包装格式：{"code":200,"data":[...]}
        data_list = []
        if isinstance(result, dict):
            if "data" in result:
                data_list = result["data"] or []
            elif isinstance(result, list):
                data_list = result
        elif isinstance(result, list):
            data_list = result

        hotels = []
        has_hotel_rooms = False
        for item in data_list:
            if not isinstance(item, dict):
                continue
            has_empty = item.get("hasEmptyRoom") or item.get("has_empty_room", False)
            if has_empty:
                has_hotel_rooms = True
                hotels.append({
                    "hotelId": item.get("hotelId") or item.get("hotel_id", ""),
                    "roomTypeId": item.get("roomTypeId") or item.get("room_type_id", ""),
                    "roomNo": item.get("roomNo") or item.get("room_no", ""),
                    "hotelName": item.get("hotelName") or item.get("hotel_name", "未知酒店"),
                    "roomTypeName": item.get("roomTypeName") or item.get("room_type_name", ""),
                    "address": item.get("address") or "",
                    "price": item.get("price") or item.get("price_per_night") or "",
                })

        logger.info(f"[Researcher] 查询到 {len(hotels)} 个有空房的酒店")
        return hotels, has_hotel_rooms

    except requests.exceptions.Timeout:
        logger.warning("[Researcher] selectEmptyRoom 接口超时")
        return [], False
    except Exception as e:
        logger.warning(f"[Researcher] selectEmptyRoom 接口调用失败: {e}")
        return [], False
