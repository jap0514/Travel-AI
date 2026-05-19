import json
import redis

from app.config.settings import settings
from app.config.logger import logger

redis_client = redis.from_url(settings.REDIS_URL, decode_responses=True)

def get_session_context(session_id: int) -> list:
    """获取会话短期上下文"""
    key = f"chat:session:{session_id}"
    data = redis_client.get(key)
    return json.loads(data) if data else []

def save_session_context(session_id: int, messages: list):
    """保存会话上下文"""
    key = f"chat:session:{session_id}"
    redis_client.setex(key, settings.REDIS_SESSION_TTL, json.dumps(messages))