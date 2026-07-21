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


# ==================== Flow State（酒店预定交互流程状态） ====================

FLOW_STATE_TTL = 3600  # flow state 过期时间：1小时


def _serialize_messages(messages):
    """将消息列表中的 langchain 消息对象转换为可 JSON 序列化的 dict"""
    result = []
    for msg in messages:
        msg_dict = {
            "type": type(msg).__name__,
            "content": msg.content,
        }
        # 保留 additional_kwargs（如 tool_calls 等）
        if hasattr(msg, "additional_kwargs") and msg.additional_kwargs:
            msg_dict["additional_kwargs"] = msg.additional_kwargs
        if hasattr(msg, "name") and msg.name:
            msg_dict["name"] = msg.name
        result.append(msg_dict)
    return result


def _deserialize_messages(messages_data):
    """将 dict 列表还原为 langchain 消息对象"""
    from langchain_core.messages import HumanMessage, AIMessage, SystemMessage, ToolMessage
    result = []
    for d in messages_data:
        msg_type = d.get("type", "")
        content = d.get("content", "")
        additional_kwargs = d.get("additional_kwargs", {})
        name = d.get("name")
        if msg_type == "HumanMessage":
            result.append(HumanMessage(content=content, name=name, additional_kwargs=additional_kwargs))
        elif msg_type == "AIMessage":
            result.append(AIMessage(content=content, name=name, additional_kwargs=additional_kwargs))
        elif msg_type == "SystemMessage":
            result.append(SystemMessage(content=content, name=name))
        elif msg_type == "ToolMessage":
            result.append(ToolMessage(content=content, name=name, tool_call_id=additional_kwargs.get("tool_call_id", "")))
        else:
            result.append(HumanMessage(content=content))
    return result


def _serialize_task(task):
    """序列化 TravelTask（dataclass）"""
    import dataclasses
    if hasattr(task, "model_dump"):
        return task.model_dump()
    elif dataclasses.is_dataclass(task):
        return dataclasses.asdict(task)
    elif hasattr(task, "dict"):
        return task.dict()
    return str(task)


def _serialize_plan(plan):
    """序列化 TravelPlan（dataclass）"""
    import dataclasses
    if hasattr(plan, "to_dict"):
        return plan.to_dict()
    elif hasattr(plan, "model_dump"):
        return plan.model_dump()
    elif dataclasses.is_dataclass(plan):
        return dataclasses.asdict(plan)
    elif hasattr(plan, "dict"):
        return plan.dict()
    return str(plan)


def save_flow_state(session_id: int, flow_id: str, state: dict):
    """
    保存 flow 状态到 Redis
    key: agent:state:{session_id}:{flow_id}
    TTL: 3600 秒（1小时）
    只保存恢复流程需要的字段：messages, interaction, task, research_results, draft_plan 等
    """
    # 只保留需要恢复的字段
    fields_to_save = [
        "messages", "interaction", "task", "research_results", "draft_plan",
        "final_plan", "parsed_plan", "critiques", "scores", "iteration",
        "next", "flow_id", "user_id", "session_id", "msg_id", "trace_id",
        "booking_status", "needs_replan", "start_date", "days", "callback_url",
        "intent", "qa_answer", "should_end", "max_iterations",
    ]
    state_to_save = {}
    for key in fields_to_save:
        if key in state and state[key] is not None:
            state_to_save[key] = state[key]

    # 序列化 messages
    if "messages" in state_to_save:
        state_to_save["messages"] = _serialize_messages(state_to_save["messages"])

    # 序列化 task (TravelTask 是 dataclass)
    if "task" in state_to_save:
        state_to_save["task"] = _serialize_task(state_to_save["task"])

    # 序列化 parsed_plan（TravelPlan 是 dataclass）
    if "parsed_plan" in state_to_save:
        state_to_save["parsed_plan"] = _serialize_plan(state_to_save["parsed_plan"])

    key = f"agent:state:{session_id}:{flow_id}"
    redis_client.set(key, json.dumps(state_to_save, ensure_ascii=False), ex=FLOW_STATE_TTL)
    logger.debug(f"Flow state 已保存: session_id={session_id}, flow_id={flow_id}")


def _deserialize_task(task_data):
    """将 dict 还原为 TravelTask"""
    if task_data is None:
        return None
    from dataclasses import dataclass
    # TravelTask 是 dataclass
    @dataclass
    class TravelTask:
        task_id: int
        trace_id: str
        user_id: int
        user_query: str
        days: int
        budget: str
        pace: str
        plan_id: int = None
        error_msg: str = None
        result_status: str = None
        destination: str = "未知城市"
        start_date: str = None
    return TravelTask(**task_data)


def _deserialize_plan(plan_data):
    """将 dict 还原为 TravelPlan"""
    if plan_data is None:
        return None
    from dataclasses import dataclass, fields
    # 还原 DailyActivity 和 Activity
    @dataclass
    class Activity:
        name: str
        time: str = None
        description: str = None
        location: str = None
        transportation: str = None
        cost: int = None

    @dataclass
    class DailyActivity:
        day: int
        theme: str
        activities: list
        location: str = None
        transportation: str = None
        meals: list = None
        tips: str = None
        estimated_cost: int = None

    # 还原嵌套的 Activity 和 DailyActivity
    daily_plans = []
    for dp in plan_data.get("daily_plans", []):
        activities = [Activity(**a) for a in dp.get("activities", [])]
        daily_plans.append(DailyActivity(
            day=dp["day"],
            theme=dp["theme"],
            activities=activities,
            location=dp.get("location"),
            transportation=dp.get("transportation"),
            meals=dp.get("meals"),
            tips=dp.get("tips"),
            estimated_cost=dp.get("estimated_cost"),
        ))

    from datetime import date
    plan_data["daily_plans"] = daily_plans
    if plan_data.get("start_date"):
        try:
            plan_data["start_date"] = date.fromisoformat(plan_data["start_date"])
        except (ValueError, TypeError):
            plan_data["start_date"] = None

    @dataclass
    class TravelPlan:
        user_id: int
        daily_plans: list
        title: str
        days: int
        budget: str
        pace: str
        task_id: int = None
        plan_id: int = None
        destination: str = "北京"
        start_date: date = None
        total_estimated_cost: int = None
        notes: str = None
        raw_markdown: str = ""
    return TravelPlan(**plan_data)


def get_flow_state(session_id: int, flow_id: str) -> dict | None:
    """
    从 Redis 获取 flow 状态
    返回: state dict 或 None（不存在或已过期）
    """
    key = f"agent:state:{session_id}:{flow_id}"
    data = redis_client.get(key)
    if data:
        logger.debug(f"Flow state 已恢复: session_id={session_id}, flow_id={flow_id}")
        state = json.loads(data)
        if "messages" in state:
            state["messages"] = _deserialize_messages(state["messages"])
        if "task" in state and state["task"]:
            state["task"] = _deserialize_task(state["task"])
        if "parsed_plan" in state and state["parsed_plan"]:
            state["parsed_plan"] = _deserialize_plan(state["parsed_plan"])
        return state
    logger.debug(f"Flow state 不存在: session_id={session_id}, flow_id={flow_id}")
    return None


def clear_flow_state(session_id: int, flow_id: str):
    """
    从 Redis 删除 flow 状态
    """
    key = f"agent:state:{session_id}:{flow_id}"
    redis_client.delete(key)
    logger.debug(f"Flow state 已清除: session_id={session_id}, flow_id={flow_id}")