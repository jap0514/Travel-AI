from app.config.settings import settings
from app.config.logger import logger
from langchain_mcp_adapters.client import MultiServerMCPClient, asyncio

tools = None


def init_mcp_tools():
    global tools
    if not settings.MCP_SERVERS:
        logger.warning("未配置 MCP_SERVERS，使用模拟工具")
        tools = get_mock_tools()
        return tools

    try:


        mcp_client = MultiServerMCPClient(settings.MCP_SERVERS)

        async def load_tools():
            return await mcp_client.get_tools()

        tools = asyncio.run(load_tools())
        logger.info(f"✅ 已加载 {len(tools)} 个 MCP 工具")
        return tools
    except Exception as e:
        logger.error(f"MCP 加载失败: {e}，使用模拟工具")
        tools = get_mock_tools()
        return tools


def get_mock_tools():
    """模拟工具"""
    from langchain_core.tools import tool

    @tool
    def search_weather(city: str, date: str = None):
        """查询天气"""
        return f"{city} 近期天气晴朗，18-25℃，适合出行。"

    @tool
    def search_flights(departure: str, destination: str, date: str):
        """查询航班"""
        return f"从 {departure} 到 {destination}（{date}）：CA1234 ¥899，MU5678 ¥1200"

    @tool
    def search_hotels(city: str, checkin: str, checkout: str, budget: str = "medium"):
        """查询酒店"""
        return f"{city} 推荐酒店：丽思卡尔顿（¥1200/晚）、汉庭（¥350/晚）"

    return [search_weather, search_flights, search_hotels]


# 模块导入时自动初始化
tools = init_mcp_tools()