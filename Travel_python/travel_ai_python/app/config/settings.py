# app/config/settings.py
import os   # 用于读取系统环境变量。
from dotenv import load_dotenv  # 从项目根目录的 .env 文件中加载变量到环境变量中，使 os.getenv() 能读取到。

load_dotenv()  #默认查找当前目录或父目录中的 .env 文件，并将其中的键值对加载到进程的环境变量中。


class Settings:   # 将配置封装成一个类，便于统一管理和引用。
    # RocketMQ
    NAMESRV_ADDR: str = os.getenv("ROCKETMQ_NAMESRV", "192.168.71.140:8081")
    SUBMIT_TOPIC: str = os.getenv("ROCKETMQ_SUBMIT_TOPIC", "travel-task-submit")
    RESULT_TOPIC: str = os.getenv("ROCKETMQ_RESULT_TOPIC", "travel-task-result")
    CONSUMER_GROUP: str = os.getenv("ROCKETMQ_CONSUMER_GROUP", "travel-task-python-consumer-group")
    PRODUCER_GROUP: str = os.getenv("ROCKETMQ_PRODUCER_GROUP", "travel-task-python-producer-group")

    # MQ Tag

    # 对标Java那边生产者的tag
    SUBMIT_TAG: str = "content-submit"
    RESULT_TAG: str = "task-result"

    # 签名密钥
    SIGN_SECRET: str = os.getenv("MQ_SIGN_SECRET", "defaultSecret123456")

    # AI模型
    AI_API_URL: str = os.getenv("AI_MODEL_API_URL", "")
    AI_API_KEY: str = os.getenv("AI_MODEL_API_KEY", "")
    AI_MODEL_NAME: str = os.getenv("AI_MODEL_NAME", "qwen-max")

    # 日志级别
    LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO")


settings = Settings()
# 实例化 Settings 类，得到一个全局可用的 settings 对象。
#
# 其他模块可以直接 from config.settings import settings 来获取配置。