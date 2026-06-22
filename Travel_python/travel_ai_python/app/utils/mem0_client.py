from app.config.settings import settings
from app.config.logger import logger
from mem0 import Memory


# ==================== 初始化 Mem0 ====================
try:
    memory = Memory.from_config(settings.MEM0_CONFIG)
    logger.info("✅ Mem0 初始化成功 | Qdrant 持久化已启用")
except Exception as e:
    logger.error(f"❌ Mem0 初始化失败: {e}")
    try:
        import os

        os.environ.setdefault("LOCAL_AI_MODEL_API_KEY", settings.AI_API_KEY)
        os.environ.setdefault("LOCAL_AI_MODEL_API_URL", settings.AI_API_URL)
        memory = Memory()
        logger.info("✅ Mem0 已使用默认配置初始化")
    except Exception as e2:
        logger.error(f"Mem0 默认初始化失败: {e2}")
        raise


def get_user_memories(user_id: int, query: str = None, top_k: int = 5):
    """获取用户长期记忆"""
    try:
        filters = {"user_id": str(user_id)}
        if query:
            results = memory.search(query, filters=filters, top_k=top_k)
        else:
            results = memory.get_all(filters=filters)

        if isinstance(results, dict):
            return results.get("results", [])
        return results
    except Exception as e:
        logger.error(f"获取 Mem0 记忆失败: {e}")
        return []


def add_to_memory(messages: list, user_id: int):
    """将对话存入长期记忆"""
    try:
        if messages and len(messages) > 0:
            memory.add(messages, user_id=str(user_id))
            logger.info(f"✅ Mem0 记忆更新成功 - user_id: {user_id}")
    except Exception as e:
        logger.error(f"❌ Mem0 添加记忆失败: {e}")