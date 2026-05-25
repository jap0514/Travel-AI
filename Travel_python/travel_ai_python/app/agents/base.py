from langchain_openai import ChatOpenAI
from app.config.settings import settings
from app.tools.mcp_tools import init_mcp_tools

# 全局共享的 LLM 和 Tools
llm = ChatOpenAI(
    model=settings.AI_MODEL_NAME,
    base_url=settings.AI_API_URL,
    api_key=settings.AI_API_KEY,
    temperature=0.3,
)

tools = init_mcp_tools()