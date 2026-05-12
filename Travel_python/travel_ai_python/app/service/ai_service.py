# app/service/ai_service.py
import json
import random
import time
import requests
from app.config.settings import settings
from app.config.logger import logger


def call_ai_model(user_query: str, days: int, budget: str, pace: str) -> (str, int):
    """
    调用大模型生成行程单
    返回 (result_json_string, plan_id)
    """
    # 模拟耗时
    time.sleep(2)

    # 如果配置了真实的 API，请替换此处
    if settings.AI_API_URL and settings.AI_API_KEY:
        headers = {
            "Authorization": f"Bearer {settings.AI_API_KEY}",
            "Content-Type": "application/json"
        }
        url = f"{settings.AI_API_URL.rstrip('/')}/chat/completions"

        # 构造符合 OpenAI 兼容格式的请求体
        payload = {
            "model": settings.AI_MODEL_NAME,
            "messages": [
                {
                    "role": "user",
                    "content": f"请帮我规划一个{days}天的旅行，预算{budget}元，节奏{pace}，需求：{user_query}"
                }
            ],
            "temperature": 0.7,
            "max_tokens": 8192,  # 增加输出长度限制
        }
        try:
            resp = requests.post(url, json=payload, headers=headers, timeout=120,verify=False)
            resp.raise_for_status()
            data = resp.json()
            # 标准 OpenAI 返回格式
            plan_text = data["choices"][0]["message"]["content"]
            plan_id = random.randint(1000, 9999)
            result_data = json.dumps({"planId": plan_id, "content": plan_text}, ensure_ascii=False)
            return result_data, plan_id
        except Exception as e:
            logger.error(f"AI模型调用失败: {e}")
            # 可选：打印响应详情帮助调试
            if hasattr(e, 'response') and e.response is not None:
                logger.error(f"响应状态码: {e.response.status_code}")
                logger.error(f"响应内容: {e.response.text}")
            raise e
    else:
        # Mock 数据
        plan_id = random.randint(1000, 9999)
        result_data = json.dumps({
            "planId": plan_id,
            "title": f"根据'{user_query}'生成的{days}天行程",
            "days": days,
            "budget": budget,
            "pace": pace,
            "details": [
                {"day": 1, "activities": ["故宫", "天安门"]},
                {"day": 2, "activities": ["长城"]}
            ]
        }, ensure_ascii=False)
        return result_data, plan_id