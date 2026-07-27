package com.travel.mq;

public class TravelRocketMQConstant {
    //主题topic
    public static final String TRAVEL_TASK_TOPIC="travel-task-topic";

    //接收Python发过来的消息
    public static final String TRAVEL_TASK_RESULT_TOPIC="travel-task-result";

    //消费者组
    public static final String TRAVEL_CONSUMER_GROUP="travel-task-consumer-group";

    //========== 订单超时处理 ==========

    // 订单超时延迟消息 Topic
    public static final String ORDER_TIMEOUT_TOPIC = "order-timeout-topic";

    // 订单超时 Consumer Group
    public static final String ORDER_TIMEOUT_CONSUMER_GROUP = "order-timeout-consumer-group";
}
