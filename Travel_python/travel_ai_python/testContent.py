
import signal
import sys
from datetime import datetime

import asyncio

from app.config.logger import logger
from app.model.message_model import ChatMessage
from app.mq.consumer import TaskConsumer
from app.mq.message_consumer import MessageConsumer
from app.service.multi_agent_travel import process_with_agent


def main1():
    message = ChatMessage(
        msg_id=1,
        session_id=1001,
        user_id=1,
        role="USER",
        content="广州3日游",
        plan_json=None,
        create_time=datetime.now()
    )
    # logger.info(f"收到消息，content={content_data}, trace_id={trace_id}")

    # 从消息中得到content后，封装好message对象。将content发送给大模型进行内容分析。
    task, final_plan, parsed_plan =asyncio.run(process_with_agent(message, "10001"))


if __name__ == "__main__":
    main1()