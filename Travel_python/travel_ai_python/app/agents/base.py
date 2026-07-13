from langchain_openai import ChatOpenAI
from app.config.settings import settings
from app.tools.mcp_tools import init_mcp_tools_async
import asyncio

# 全局共享的 LLM 和 Tools
llm = ChatOpenAI(
    model=settings.AI_MODEL_NAME,
    base_url=settings.AI_API_URL,
    api_key=settings.AI_API_KEY,
    temperature=0.3,
)

_tools = None
# MCP 工具调用并发限制，避免多个请求同时访问 MCP SSE 连接导致冲突
_mcp_semaphore = asyncio.Semaphore(1)


async def get_tools():
    """异步获取工具"""
    global _tools
    if _tools is None:
        _tools = await init_mcp_tools_async()
    return _tools


def get_mcp_semaphore() -> asyncio.Semaphore:
    """获取 MCP 并发限制信号量"""
    return _mcp_semaphore