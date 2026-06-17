# app/config/settings.py
import os   # 用于读取系统环境变量。
from dotenv import load_dotenv  # 从项目根目录的 .env 文件中加载变量到环境变量中，使 os.getenv() 能读取到。

load_dotenv()  #默认查找当前目录或父目录中的 .env 文件，并将其中的键值对加载到进程的环境变量中。


class Settings:   # 将配置封装成一个类，便于统一管理和引用。
    # RocketMQ
    NAMESRV_ADDR: str = os.getenv("ROCKETMQ_NAMESRV", "192.168.71.140:8081")
    SUBMIT_TOPIC: str = os.getenv("ROCKETMQ_SUBMIT_TOPIC", "travel-task-submit")
    # RESULT_TOPIC: str = os.getenv("ROCKETMQ_RESULT_TOPIC", "travel-task-result")
    CONSUMER_GROUP: str = os.getenv("ROCKETMQ_CONSUMER_GROUP", "travel-task-python-consumer-group")
    PRODUCER_GROUP: str = os.getenv("ROCKETMQ_PRODUCER_GROUP", "travel-task-python-producer-group")

    # 新的和Rocketmq连接的Topic和tag
    CONTENT_TOPIC: str=os.getenv("CONTENT_TOPIC", "travel-content-exchange")
    TASK_TOPIC:str=os.getenv("TASK_TOPIC", "travel-task-exchange")
    RESULT_TOPIC:str=os.getenv("RESULT_TOPIC", "travel-result-exchange")


    # MQ Tag

    # 对标Java那边生产者的tag
    SUBMIT_TAG: str = "content-submit"
    # RESULT_TAG: str = "task-result"

    CONTENT_TAG: str="content-exchange"
    TASK_TAG:str="task-exchange"
    RESULT_TAG:str="result-exchange"

    # 签名密钥
    SIGN_SECRET: str = os.getenv("MQ_SIGN_SECRET", "defaultSecret123456")

    # AI模型
    AI_API_URL: str = os.getenv("LOCAL_AI_MODEL_API_URL", "")
    AI_API_KEY: str = os.getenv("LOCAL_AI_MODEL_API_KEY", "")
    AI_MODEL_NAME: str = os.getenv("LOCAL_MODEL_NAME", "deepseek-r1:1.5b")

    # 日志级别
    LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO")

    # Redis 配置
    REDIS_URL: str = os.getenv("REDIS_URL", "redis://192.168.71.140:6379/0")
    REDIS_SESSION_TTL: int = int(os.getenv("REDIS_SESSION_TTL", 86400))  # 24小时

    # Mem0 配置 - 适配通义千问 Dashscope（已修复字段问题）
    MEM0_CONFIG: dict = {
        "vector_store": {
            "provider": "qdrant",
            "config": {
                "path": "./mem0_qdrant_db",
                "collection_name": "travel_memories",
                "on_disk": True
            }
        },
        "embedder": {
            "provider": "openai",
            "config": {
                "model": "text-embedding-v2",
                "api_key": os.getenv("LOCAL_AI_MODEL_API_KEY"),
                "openai_base_url": os.getenv("LOCAL_AI_MODEL_API_URL")  # ← 关键字段！
            }
        },
        "llm": {
            "provider": "openai",
            "config": {
                "model": AI_MODEL_NAME,
                "api_key": os.getenv("LOCAL_AI_MODEL_API_KEY"),
                "openai_base_url": os.getenv("LOCAL_AI_MODEL_API_URL"),
                "temperature": 0.7
            }
        }
    }

    # 心知天气配置
    XINZHI_WEATHER_API_KEY: str = os.getenv("XINZHI_WEATHER_API_KEY", "")
    XINZHI_WEATHER_BASE_URL: str = "https://api.seniverse.com/v3/weather"

    # MCP 工具服务器（可选）
    MCP_SERVERS: dict = {
        "travel_tools": {
            "transport": "sse",
            "url": "http://localhost:9997/sse"
        },
        "qdrant_rag": {  # 新增
            "transport": "sse",
            "url": "http://localhost:9996/sse"
        }
    }


settings = Settings()
# 实例化 Settings 类，得到一个全局可用的 settings 对象。
#
# 其他模块可以直接 from config.settings import settings 来获取配置。