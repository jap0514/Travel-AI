@echo off
chcp 65001 >nul
title Travel AI Web Server

echo ==========================================
echo   Travel AI — 智能旅行规划系统
echo   Web 服务启动脚本
echo ==========================================
echo.

:: 激活虚拟环境（如果有）
if exist .venv\Scripts\activate.bat (
    call .venv\Scripts\activate.bat
) else if exist venv\Scripts\activate.bat (
    call venv\Scripts\activate.bat
) else (
    echo [INFO] 未找到虚拟环境，使用系统 Python
)

echo [1/3] 检查依赖...
pip install -q fastapi uvicorn python-multipart 2>nul

echo [2/3] 启动 MCP 工具服务（新窗口）...
start "MCP-Tools" cmd /c "python -m app.mcp_servers.mcp_server"
echo   - 工具服务端口: 9997
timeout /t 2 /nobreak >nul

echo [3/3] 启动 Web 服务...
echo.
echo ==========================================
echo   打开浏览器访问: http://localhost:8000
echo   API 文档:       http://localhost:8000/docs
echo ==========================================
echo.

python web_app.py

pause
