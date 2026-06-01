from fastmcp import FastMCP
import requests
from typing import Optional
from app.config.settings import settings
from app.config.logger import logger

mcp = FastMCP("Travel Tools Server")

@mcp.tool
def search_weather(city: str, date: str = None):
    """查询城市天气，支持今天和未来几天"""
    logger.info(f"进入天气查询工具")
    if not settings.XINZHI_WEATHER_API_KEY:
        return f"[{city}] 天气查询服务未配置 API Key"

    try:
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
            loc = data["results"][0]["location"]
            return f"""🌤️ {loc['name']} 当前天气：
• 天气：{now['text']}
• 温度：{now['temperature']}℃
• 体感：{now['feels_like']}℃   
• 湿度：{now['humidity']}%"""
        else:
            # 未来天气...
            return f"{city} 未来几天天气预报（开发中）"
    except Exception as e:
        return f"查询天气失败: {str(e)}"


@mcp.tool
def search_flights(departure: str, destination: str, date: str):
    """查询航班信息"""
    return f"从 {departure} 到 {destination}（{date}）：CA1234 ¥899，MU5678 ¥1200"


@mcp.tool
def search_hotels(city: str, checkin: str, checkout: str, budget: str = "medium"):
    """查询酒店推荐"""
    return f"{city} 推荐酒店：丽思卡尔顿（¥1200/晚）、汉庭（¥350/晚）"


if __name__ == "__main__":
    mcp.run(transport="sse", host="127.0.0.1", port=9997)