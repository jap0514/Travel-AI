# app/mq/producer.py
import time

from rocketmq.v5.client import ClientConfiguration,Credentials
from rocketmq.v5.producer import Producer
from rocketmq.v5.model import Message
from app.config.settings import settings
from app.config.logger import logger
from app.utils.sign_utils import generate_sign
from app.utils.json_utils import to_compact_json


class ResultProducer:
    def __init__(self):
        self.producer = None
        self.started = False

    def start(self):
        if not self.started:

            cofig=ClientConfiguration(
                endpoints=settings.NAMESRV_ADDR,
                credentials=Credentials()
            )

            self.producer = Producer(
                client_configuration=cofig,
                # producer_group=settings.PRODUCER_GROUP
            )
            # self.producer.set_namesrv_addr(settings.NAMESRV_ADDR)
            self.producer.startup()
            self.started = True
            logger.info("ResultProducer 启动成功")

    def shutdown(self):
        if self.started and self.producer:
            self.producer.shutdown()
            self.started = False
            logger.info("ResultProducer 已关闭")

    def send_result(self, trace_id: str, task_id: int, user_id: int,
                    result_status: str, result_data: str, plan_id: int, error_msg: str = ""):
        """
        构建并发送结果消息（格式与Java端一致）
        """
        timestamp = int(time.time() * 1000)
        body = {
            "taskId": task_id,
            "resultStatus": result_status,
            "resultData": result_data,
            "planId": plan_id if plan_id else None,
            "errorMsg": error_msg
        }
        sign = generate_sign(trace_id, timestamp, body, settings.SIGN_SECRET)
        header = {
            "traceId": trace_id,
            "msgId": f"PYTHON_{timestamp}_{task_id}",
            "businessType": "TRAVEL_TASK_RESULT",
            "version": "1.0",
            "timestamp": timestamp,
            "sign": sign,
            "userId": user_id
        }
        ext = {
            "mqTopic": settings.RESULT_TOPIC,
            "mqTag": settings.RESULT_TAG,
            "timeout": 300000
        }
        full_msg = {
            "header": header,
            "body": body,
            "ext": ext
        }
        msg_json = to_compact_json(full_msg)

        msg = Message()
        msg.topic=settings.RESULT_TOPIC
        msg.tag=settings.RESULT_TAG
        msg.body=msg_json.encode('utf-8')
        ret = self.producer.send(msg)
        msg_id=ret.message_id if hasattr(ret, 'message_id') else str(ret)
        logger.info(f"结果消息发送成功, msgId={msg_id}, taskId={task_id}, traceId={trace_id}")
        return ret