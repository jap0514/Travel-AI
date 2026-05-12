# app/config/logger.py
import sys
from loguru import logger
from app.config.settings import settings

# 移除默认控制台输出
logger.remove()

# 控制台输出（带颜色，支持 traceId 动态注入）
logger.add(
    sys.stdout,
    format="<green>{time:YYYY-MM-DD HH:mm:ss.SSS}</green> | <level>{level: <8}</level> | <cyan>{extra[trace_id]}</cyan> | <level>{message}</level>",
    level=settings.LOG_LEVEL,
    filter=lambda record: record["extra"].get("trace_id", "N/A") != ""
)

# 按天分割的文件日志（JSON格式，便于ELK采集）
logger.add(
    "logs/travel_ai_{time:YYYY-MM-DD}.log",
    rotation="00:00",
    retention="30 days",
    format="{time:YYYY-MM-DD HH:mm:ss.SSS} | {level: <8} | {extra[trace_id]} | {message}",
    level=settings.LOG_LEVEL,
    enqueue=True,
)

# 错误单独文件
logger.add(
    "logs/travel_ai_error_{time:YYYY-MM-DD}.log",
    rotation="00:00",
    retention="90 days",
    level="ERROR",
    format="{time:YYYY-MM-DD HH:mm:ss.SSS} | {level: <8} | {extra[trace_id]} | {message}",
    enqueue=True,
)

# 默认绑定 trace_id 为 "N/A"
logger = logger.bind(trace_id="N/A")