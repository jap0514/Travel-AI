# app/service/task_service.py
import json
from app.config.logger import logger
from app.model.task_model import TravelTask
from app.service.ai_service import call_ai_model

def process_task(task: TravelTask):
    """
    处理任务，调用AI模型，返回 (result_status, result_data, plan_id, error_msg)
    """
    log = logger.bind(trace_id=task.trace_id)
    log.info(f"开始处理任务 taskId={task.task_id}")
    try:
        result_json, plan_id = call_ai_model(
            user_query=task.user_query,
            days=task.days,
            budget=task.budget,
            pace=task.pace
        )
        log.info(f"AI处理成功 taskId={task.task_id}, planId={plan_id}")
        return "SUCCESS", result_json, plan_id, ""
    except Exception as e:
        error_msg = f"AI模型错误: {str(e)}"
        log.error(f"处理失败 taskId={task.task_id}, error={error_msg}")
        return "FAILED", "", 0, error_msg