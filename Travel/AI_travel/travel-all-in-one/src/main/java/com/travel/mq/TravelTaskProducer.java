package com.travel.mq;

import com.travel.common.TaskStatusEnum;
import com.travel.entity.TravelTask;
import com.travel.service.TravelTaskService;
import com.travel.util.MqMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.function.BiConsumer;

@Slf4j
@Component
public class TravelTaskProducer {
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private MqMessageUtil mqMessageUtil;

    /**
     * 异步发送旅行内容消息,带回调确认，确保消息不丢
     */
    public void sendTaskWithCallback(TravelTask task,
                                     BiConsumer<String,Void> successCallback,
                                     BiConsumer<Throwable,Void> failureCallback){

        try{
            //1、构建标准消息
            String jsonMsg=mqMessageUtil.buildSubmitMessage(task);
            Message<String> message = MessageBuilder
                    .withPayload(jsonMsg)
                    .setHeader("TRACE_ID", task.getTrace_id())
                    .build();

            //2、异步发送+回调
            rocketMQTemplate.asyncSend(
                    "travel-content-submit:content-submit",   //Topic:Tag
                    message,
                    new SendCallback() {
                        //发送成功回调
                        @Override
                        public void onSuccess(SendResult sendResult) {
                            if(sendResult.getSendStatus()== SendStatus.SEND_OK){
                                log.info("MQ发送成功 taskId={},msgId={}",task.getTask_id(),sendResult.getMsgId());

                                //消息发送成功后执行成功回调
                                successCallback.accept(sendResult.getMsgId(),null);
                            }else {
                                // 发送状态不是 SEND_OK，也算失败
                                String errorMsg="发送状态异常: "+sendResult.getSendStatus();
                                log.error("MQ发送状态异常 taskId={},{}",task.getTask_id(),errorMsg);

                                if(failureCallback!=null){
                                    failureCallback.accept(new RuntimeException(errorMsg),null);
                                }
                            }
                        }

                        //发送失败回调
                        @Override
                        public void onException(Throwable throwable) {
                            log.error("MQ发送失败 taskId={}",task.getTask_id(),throwable);

                            //失败后的操作
                            if(failureCallback!=null){
                                failureCallback.accept(throwable,null);
                            }
                        }
                    }
            );
        }catch (Exception e){
            log.error("MQ发送异常 taskId={}",task.getTask_id(),e);
            if(failureCallback!=null){
                failureCallback.accept(e,null);
            }
        }



    }
}
