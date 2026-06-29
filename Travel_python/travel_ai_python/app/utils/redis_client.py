import json
import uuid
import redis

from app.config.settings import settings
from app.config.logger import logger

redis_client = redis.from_url(
    settings.REDIS_URL,
    decode_responses=True,
    socket_keepalive=True,
    socket_connect_timeout=5,
    socket_timeout=30,  # 30秒无数据读写超时，避免永久阻塞
)


# ==================== Profile 更新队列 ====================

PROFILE_UPDATE_QUEUE = "queue:profile_updates"


def push_profile_update_task(user_id: int, task_data: dict):
    """
    将 Profile 更新任务放入队列（先检查是否有待处理任务，避免重复）
    使用 LPUSH 放入队列
    """
    import time
    task_data["enqueue_time"] = time.time()
    redis_client.lpush(PROFILE_UPDATE_QUEUE, json.dumps(task_data, ensure_ascii=False))
    logger.debug(f"Profile 更新任务已入队: user_id={user_id}")


def pop_profile_update_task(block: bool = True, timeout: int = 0):
    """
    从队列取出 Profile 更新任务
    - block=True, timeout=0: 阻塞等待，有任务立即返回
    - block=True, timeout=N: 最多等 N 秒
    - block=False: 直接返回，没有任务就返回 None
    返回: (task_data) 或 None
    """
    if block:
        # 阻塞模式，timeout=0 表示永久阻塞
        result = redis_client.brpop(PROFILE_UPDATE_QUEUE, timeout=timeout)
        if result is None:
            return None
        _, task_json = result
        return json.loads(task_json)
    else:
        # 非阻塞模式
        task_json = redis_client.rpop(PROFILE_UPDATE_QUEUE)
        if task_json is None:
            return None
        return json.loads(task_json)


def get_profile_update_queue_length() -> int:
    """获取队列长度"""
    return redis_client.llen(PROFILE_UPDATE_QUEUE)


# ==================== 分布式锁（保留，作为备用） ====================

def acquire_profile_lock(user_id: int, timeout: int = 30) -> str | None:
    """
    获取用户 Profile 的分布式锁
    返回 lock_value（成功）或 None（失败）
    """
    lock_key = f"lock:profile:{user_id}"
    lock_value = str(uuid.uuid4())
    acquired = redis_client.set(lock_key, lock_value, nx=True, ex=timeout)
    if acquired:
        logger.debug(f"获取 Profile 锁成功: user_id={user_id}")
        return lock_value
    logger.warning(f"获取 Profile 锁失败（已被占用）: user_id={user_id}")
    return None


def release_profile_lock(user_id: int, lock_value: str) -> bool:
    """
    释放用户 Profile 的分布式锁
    只有锁的值匹配时才删除（防止误删其他请求的锁）
    """
    lock_key = f"lock:profile:{user_id}"
    # Lua 脚本保证原子性：检查值再删除
    script = """
    if redis.call('GET', KEYS[1]) == ARGV[1] then
        return redis.call('DEL', KEYS[1])
    else
        return 0
    end
    """
    result = redis_client.eval(script, 1, lock_key, lock_value)
    if result:
        logger.debug(f"释放 Profile 锁成功: user_id={user_id}")
        return True
    logger.warning(f"释放 Profile 锁失败（值不匹配）: user_id={user_id}")
    return False

def get_session_context(session_id: int) -> list:
    """获取会话短期上下文"""
    key = f"chat:session:{session_id}"
    data = redis_client.get(key)
    return json.loads(data) if data else []

def save_session_context(session_id: int, messages: list):
    """保存会话上下文"""
    key = f"chat:session:{session_id}"
    redis_client.set(key, json.dumps(messages), ex=settings.REDIS_SESSION_TTL)


def get_user_profile(user_id: int) -> dict:
    """获取用户结构化偏好（Profile）"""
    key = f"user:profile:{user_id}"
    data = redis_client.get(key)
    return json.loads(data) if data else {}


def save_user_profile(user_id: int, profile: dict):
    """保存用户结构化偏好（Profile）"""
    key = f"user:profile:{user_id}"
    redis_client.set(key, json.dumps(profile, ensure_ascii=False))