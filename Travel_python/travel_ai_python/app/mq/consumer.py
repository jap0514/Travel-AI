import json
import os
from langchain_openai import ChatOpenAI
from rocketmq.v5.client import ClientConfiguration,Credentials
from rocketmq.v5.consumer.push.push_consumer import PushConsumer
from rocketmq.v5.consumer.push.message_listener import MessageListener, ConsumeResult
from rocketmq.v5.model.filter_expression import FilterExpression, FilterType

from app.config.settings import settings
from app.config.logger import logger
from app.utils.sign_utils import verify_sign
from app.model.task_model import TravelTask
from app.service.task_service import process_task
from app.mq.producer import ResultProducer


class TaskMessageListener(MessageListener):
    def __init__(self, result_producer: ResultProducer):
        self.result_producer = result_producer

    def consume(self, message):
        """
        注意：v5 SDK 的 consume 方法通常只接收一个 message 参数
        """
        try:
            # 获取消息体
            raw_body = message.body.decode("utf-8")
            msg_dict = json.loads(raw_body)

            # 1. 签名验证
            if not verify_sign(msg_dict, settings.SIGN_SECRET):
                logger.error(f"签名验证失败，丢弃消息: {raw_body[:200]}")
                return ConsumeResult.SUCCESS  # 验证失败通常不需要重试，直接丢弃

            # 2. 解析数据
            header = msg_dict.get("header", {})
            body = msg_dict.get("body", {})
            task_data = body.get("taskData", {})

            trace_id = header.get("trace_id") or header.get("traceId")
            log = logger.bind(trace_id=trace_id)

            task = TravelTask(
                task_id=body.get("taskId"),
                trace_id=trace_id,
                user_id=header.get("userId"),
                user_query=task_data.get("userQuery"),
                days=task_data.get("days"),
                budget=task_data.get("budget"),
                pace=task_data.get("pace")
            )
            log.info(f"收到任务，taskId={task.task_id}, query={task.user_query}")

            # 3. 执行业务处理
            result_status, result_data, plan_id, error_msg = process_task(task)

            # 先不发送结果回去，先测试
            # if result_status == "SUCCESS":
            #     logger.info(f"AI 处理成功，planId={plan_id}, 结果是：{result_data[:200]}...")  # 只打印前200字符
            # else:
            #     logger.error(f"AI 处理失败，错误信息：{error_msg}")


            # 4. 发送结果
            self.result_producer.send_result(
                trace_id=trace_id,
                task_id=task.task_id,
                user_id=task.user_id,
                result_status=result_status,
                result_data=result_data,
                plan_id=plan_id,
                error_msg=error_msg
            )
            return ConsumeResult.SUCCESS

        except Exception as e:
            logger.exception(f"消息处理异常，将稍后重试: {e}")
            return ConsumeResult.FAILURE


class TaskConsumer:
    def __init__(self):
        self.consumer = None
        # 确保 ResultProducer 也使用的是 v5 版本的 Producer
        self.result_producer = ResultProducer()

    def start(self):
        self.result_producer.start()

        # 重要：这里的 settings.NAMESRV_ADDR 必须映射到虚拟机的 8081 端口
        # 例如: "192.168.71.140:8081"
        config = ClientConfiguration(
            endpoints=settings.NAMESRV_ADDR,
            credentials=Credentials()
        )

        listener = TaskMessageListener(self.result_producer)

        # v5 必须在初始化时明确订阅关系
        topic_subscriptions = {
            settings.SUBMIT_TOPIC: FilterExpression(settings.SUBMIT_TAG, FilterType.TAG)
        }

        # 创建 PushConsumer
        self.consumer = PushConsumer(
            client_configuration=config,
            consumer_group=settings.CONSUMER_GROUP,
            subscription=topic_subscriptions,
            message_listener=listener,
        )

        try:
            self.consumer.startup()
            logger.info(f"TaskConsumer 启动成功，连接至 Proxy 地址: {settings.NAMESRV_ADDR}")
        except Exception as e:
            logger.error(f"TaskConsumer 启动失败: {e}")
            raise e

    def shutdown(self):
        if self.consumer:
            try:
                self.consumer.shutdown()
            except Exception as e:
                logger.error(f"Consumer 关闭异常: {e}")
        self.result_producer.shutdown()
        logger.info("TaskConsumer 已关闭")