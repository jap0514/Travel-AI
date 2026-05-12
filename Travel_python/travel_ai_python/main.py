# main.py
import signal
import sys
from app.config.logger import logger
from app.mq.consumer import TaskConsumer

def main():
    consumer = TaskConsumer()
    def signal_handler(sig, frame):
        logger.info("收到停止信号，正在关闭...")
        consumer.shutdown()
        sys.exit(0)
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    consumer.start()
    logger.info("Python 消费者已启动，等待消息...")
    while True:
        import time
        time.sleep(1)

if __name__ == "__main__":
    main()