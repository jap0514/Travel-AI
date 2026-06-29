"""
Travel AI — Web 服务入口

启动方式：
    cd travel_ai_python
    python web_app.py

要求先启动 MCP 工具服务（可选，不启动则使用模拟工具）：
    python -m app.mcp_servers.mcp_server           # 天气/航班/酒店 (port 9997)
    python -m app.mcp_servers.qdrant_rag_server    # RAG 知识检索 (port 9996)

然后打开浏览器访问 http://localhost:8000
"""

import uvicorn
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware

from app.web.api import router as api_router
from app.web.route_api import router as route_api_router
from app.config.logger import logger

# ========== 创建 FastAPI 应用 ==========
app = FastAPI(
    title="Travel AI — 智能旅行规划系统",
    description="基于多智能体架构的智能旅行规划系统 · 毕业设计",
    version="1.0.0",
)

# ========== CORS（允许前端跨域访问） ==========
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ========== 注册 API 路由 ==========
app.include_router(api_router)
app.include_router(route_api_router)

# ========== 挂载静态文件（前端页面） ==========
app.mount(
    "/",
    StaticFiles(directory="app/web/static", html=True),
    name="frontend",
)


@app.on_event("startup")
async def startup():
    logger.info("🌐 Travel AI Web 服务启动中...")
    logger.info("📄 前端页面: http://localhost:8000")
    logger.info("🔌 API 文档: http://localhost:8000/docs")
    logger.info("💡 确保 MCP 服务已启动以获得完整功能！")


@app.on_event("shutdown")
async def shutdown():
    logger.info("👋 Travel AI Web 服务已关闭")


if __name__ == "__main__":
    uvicorn.run(
        "web_app:app",
        host="0.0.0.0",
        port=8000,
        reload=False,          # 生产模式
        log_level="info",
    )
