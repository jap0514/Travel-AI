from app.config.settings import settings
from app.config.logger import logger
from langchain_mcp_adapters.client import MultiServerMCPClient, asyncio
import requests

_tools = None
_tools_lock=asyncio.Lock()


async def init_mcp_tools_async():
    global _tools
    if _tools is not None:
        return _tools
    async with _tools_lock:
        if _tools is not None:
            return _tools
    if not settings.MCP_SERVERS:
        logger.warning("未配置 MCP_SERVERS，使用模拟工具")
        _tools = get_mock_tools()
        return _tools

    try:


        mcp_client = MultiServerMCPClient(settings.MCP_SERVERS)

        _tools=await mcp_client.get_tools()
        logger.info(f"✅ 已加载 {len(_tools)} 个 MCP 工具")
        return _tools
    except Exception as e:
        logger.error(f"MCP 加载失败: {e}，使用模拟工具")
        _tools = get_mock_tools()
        return _tools

def init_mcp_tools_sync():
    """同步获取工具"""
    try:
        loop=asyncio.get_running_loop()
        logger.info("在异步事件循环中调用同步初始化，返回模拟工具")
        return get_mock_tools()
    except RuntimeError:
        return asyncio.run(init_mcp_tools_async())

def get_mock_tools():
    """模拟工具"""
    from langchain_core.tools import tool

    @tool
    def search_weather(city: str, date: str = None):
        """查询天气（优先使用心知天气API）"""
        logger.info(f"开始调用查询天气工具")
        if not settings.XINZHI_WEATHER_API_KEY:
            return f"[{city}] 近期天气晴朗，18-25℃，适合出行。（提示：请在 .env 中配置 XINZHI_WEATHER_API_KEY）"

        try:
            # 实时天气
            if date is None or date.lower() in ["today", "now", "今日", "今天"]:
                url = f"{settings.XINZHI_WEATHER_BASE_URL}/now.json"
                params = {
                    "key": settings.XINZHI_WEATHER_API_KEY,
                    "location": city,
                    "language": "zh-Hans",
                    "unit": "c"
                }
                resp = requests.get(url, params=params, timeout=10)
                resp.raise_for_status()
                data = resp.json()

                now = data["results"][0]["now"]
                location = data["results"][0]["location"]

                return f"""🌤️ {location['name']} 当前天气：
    • 天气：{now['text']}
    • 温度：{now['temperature']}℃
    • 更新时间：{data['results'][0]['last_update']}"""

            # 未来几天预报（简单实现）
            else:
                url = f"{settings.XINZHI_WEATHER_BASE_URL}/daily.json"
                params = {
                    "key": settings.XINZHI_WEATHER_API_KEY,
                    "location": city,
                    "language": "zh-Hans",
                    "unit": "c",
                    "start": 0,
                    "days": 3
                }
                resp = requests.get(url, params=params, timeout=10)
                data = resp.json()
                days = data["results"][0]["daily"]

                result = f"🌤️ {city} 未来天气预报：\n"
                for day in days[:3]:
                    result += f"• {day['date']}: {day['text_day']}，{day['low']}~{day['high']}℃，{day['wind_direction_day']}\n"
                return result

        except requests.exceptions.RequestException as e:
            logger.error(f"心知天气API请求失败: {e}")
            return f"查询 {city} 天气失败，请稍后重试或检查 API Key。"
        except Exception as e:
            logger.error(f"心知天气工具异常: {e}")
            return f"查询 {city} 天气时发生错误。"

    @tool
    def search_flights(departure: str, destination: str, date: str):
        """查询航班"""
        return f"从 {departure} 到 {destination}（{date}）：CA1234 ¥899，MU5678 ¥1200"

    @tool
    def search_hotels(city: str, checkin: str, checkout: str, budget: str = "medium"):
        """查询酒店"""
        return f"{city} 推荐酒店：丽思卡尔顿（¥1200/晚）、汉庭（¥350/晚）"

    return [search_weather, search_flights, search_hotels]
