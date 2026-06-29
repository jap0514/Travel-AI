"""
Profile 更新 Worker - 从 Redis 队列消费 Profile 更新任务，串行执行避免并发冲突

启动方式：
    python -m app.service.profile_worker
"""


import json
import signal
import sys
import time
import os
from datetime import datetime

_current = os.path.dirname(os.path.abspath(__file__))
_root = os.path.dirname(os.path.dirname(_current))  # travel_ai_python/
if _root not in sys.path:
    sys.path.insert(0, _root)

from app.config.logger import logger
from app.utils.redis_client import pop_profile_update_task, get_profile_update_queue_length, redis_client
from app.model.task_model import TravelTask
from app.agents.base import llm
from langchain_core.messages import SystemMessage


_running = True


def signal_handler(signum, frame):
    """处理停止信号"""
    global _running
    logger.info("收到停止信号，Worker 即将关闭...")
    _running = False


def _do_update_profile(user_id: int, task: TravelTask, final_plan_text: str, session_messages: list):
    """执行真正的 Profile 更新逻辑"""
    from app.utils.redis_client import get_user_profile, save_user_profile

    profile = get_user_profile(user_id) if user_id else {}

    # 提取基础字段
    if task.destination and task.destination not in profile.get("preferred_destinations", []):
        profile.setdefault("preferred_destinations", []).append(task.destination)
    if task.budget and task.budget not in profile.get("preferred_budgets", []):
        profile.setdefault("preferred_budgets", []).append(task.budget)
    if task.pace and task.pace not in profile.get("preferred_paces", []):
        profile.setdefault("preferred_paces", []).append(task.pace)

    # 用 LLM 提取更多偏好
    history_text = "\n".join([
        f"{m.get('role', 'user')}: {m.get('content', '')}"
        for m in session_messages
    ])

    prompt = f"""你是一个用户偏好提取专家。从以下行程和对话历史中提取用户的旅行偏好，
只返回有明确证据的偏好，不要臆造。

【最终确认行程】：
{final_plan_text}

【对话历史】：
{history_text}

请以 JSON 格式返回以下字段（没有明确证据的字段返回空数组或空字符串）：
{{
    "dietary_restrictions": ["不吃辣", "素食"],
    "preferred_foods": ["海鲜", "川菜"],
    "accommodation_preference": "经济型/舒适型/豪华型",
    "travel_style": ["文化游", "亲子"]
}}

只输出 JSON，不要有任何解释。"""

    try:
        extra_prefs = llm.with_structured_output(dict, method="json_mode").invoke(
            [SystemMessage(content=prompt)]
        )

        # 增量追加
        for food in extra_prefs.get("dietary_restrictions", []):
            if food and food not in profile.get("dietary_restrictions", []):
                profile.setdefault("dietary_restrictions", []).append(food)

        for food in extra_prefs.get("preferred_foods", []):
            if food and food not in profile.get("preferred_foods", []):
                profile.setdefault("preferred_foods", []).append(food)

        acc = extra_prefs.get("accommodation_preference", "")
        if acc and profile.get("accommodation_preference") != acc:
            profile["accommodation_preference"] = acc

        for style in extra_prefs.get("travel_style", []):
            if style and style not in profile.get("travel_style", []):
                profile.setdefault("travel_style", []).append(style)

        logger.info(f"LLM 偏好提取结果: {extra_prefs}")

    except Exception as e:
        logger.warning(f"LLM 偏好提取失败: {e}，仅保存基础偏好")

    profile["last_updated"] = datetime.now().isoformat()
    save_user_profile(user_id, profile)
    logger.info(f"✅ Profile 更新成功: user_id={user_id}, profile={profile}")


def process_one_task(task_data: dict) -> bool:
    """处理单个更新任务，返回是否成功"""
    try:
        user_id = task_data.get("user_id")
        task_dict = task_data.get("task", {})
        final_plan_text = task_data.get("final_plan_text", "")
        session_messages = task_data.get("session_messages", [])
        enqueue_time = task_data.get("enqueue_time", 0)

        # 构造 TravelTask 对象
        task = TravelTask(
            task_id=None,
            trace_id=None,
            user_id=user_id,
            user_query=task_dict.get("user_query", ""),
            destination=task_dict.get("destination"),
            days=task_dict.get("days"),
            budget=task_dict.get("budget"),
            pace=task_dict.get("pace")
        )

        # 计算等待时间
        wait_time = time.time() - enqueue_time
        if wait_time > 1:
            logger.info(f"任务在队列中等待了 {wait_time:.2f} 秒: user_id={user_id}")

        _do_update_profile(user_id, task, final_plan_text, session_messages)

        # 打印醒目提示
        dest = task.destination or "未知"
        logger.info(f"""
╔════════════════════════════════════════╗
║  ✅ Profile 更新完成。                   ║
║  用户ID: {user_id:<28}                  ║
║  目的地: {dest:<28}。                    ║
╚════════════════════════════════════════╝""")
        return True

    except Exception as e:
        logger.exception(f"处理 Profile 更新任务失败: {e}")
        return False


def run_worker():
    """运行 Worker 主循环"""
    global _running

    logger.info("🚀 Profile Update Worker 启动")

    # 注册信号处理
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    task_count = 0
    error_count = 0

    while _running:
        try:
            # 阻塞等待任务，超时 5 秒检查是否该停止
            logger.info("⏳ 等待 Profile 更新任务...")
            task_data = pop_profile_update_task(block=True, timeout=5)

            if task_data is None:
                # 超时，继续循环检查 _running
                continue

            logger.info(f"📦 取出任务: user_id={task_data.get('user_id')}")

            if process_one_task(task_data):
                task_count += 1
            else:
                error_count += 1

            # 每处理 100 个任务打印一次统计
            if task_count % 100 == 0 and task_count > 0:
                logger.info(f"📊 Worker 统计: 成功={task_count}, 失败={error_count}")

        except Exception as e:
            logger.exception(f"Worker 主循环异常: {e}")
            time.sleep(1)  # 避免疯狂报错

    logger.info(f"Worker 关闭 | 总处理: {task_count} 个任务, 失败: {error_count} 个")


if __name__ == "__main__":
    run_worker()
