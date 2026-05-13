package com.travel.util;

import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import com.alibaba.fastjson2.JSON;
import com.travel.dto.ChatMessageDTO;
import com.travel.entity.ChatMessage;
import com.travel.entity.TravelTask;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class MqMessageUtil {
    @Value("${travel.security.sign-secret}")
    private String signSecret;

    @Value("${travel.mq.content-topic:travel-content-exchange}")
    private String contentTopic;

    @Value("${travel.mq.content-tag:content-exchange}")
    private String contentTag;

    /**
     * 构建发送给 RocketMQ 的消息内容（JSON 格式）
     * 包含业务头、消息体、扩展信息，可复用签名逻辑
     */
    public String buildContentMessage(ChatMessage chatMessage) {

        String traceId=UUID.randomUUID()+ "_" + chatMessage.getSession_id() + "_" + System.currentTimeMillis();
        // 消息头
        Map<String, Object> header = new HashMap<>();
        header.put("traceId", traceId);
        header.put("msgId", UUID.randomUUID()+ "_" + chatMessage.getSession_id() + "_" + System.currentTimeMillis());
        header.put("businessType", "CHAT_CONTENT_SUBMIT");
        header.put("version", "1.0");
        header.put("timestamp", System.currentTimeMillis());
        header.put("userId", chatMessage.getUser_id());

        // 消息体（核心数据）
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", chatMessage.getSession_id());
        body.put("userId", chatMessage.getUser_id());
        body.put("role", chatMessage.getRole());
        body.put("content", chatMessage.getContent());
        body.put("planJson", chatMessage.getPlan_json());

        // 扩展字段
        Map<String, Object> extension = new HashMap<>();
        extension.put("mqTopic", contentTopic);
        extension.put("mqTag", contentTag);
        extension.put("timeout", 5000);

        // 签名内容：traceId + timestamp + JSON序列化的body + signSecret
        String signContent = traceId + header.get("timestamp") + JSON.toJSONString(body) + signSecret;

        // 计算签名（使用 HMacSHA256）
        HMac hmac = new HMac(HmacAlgorithm.HmacSHA256, signSecret.getBytes());
        String sign = hmac.digestHex(signContent);
        header.put("sign", sign);  // 将签名放入 header

        // 组装完整消息
        Map<String, Object> message = new HashMap<>();
        message.put("header", header);
        message.put("body", body);
        message.put("extension", extension);



        return JSON.toJSONString(message);
    }

    /**
     * 从任务对象中获取数据，构建传给Rocketmq的消息
     * @param task
     * @return
     */
    public String buildSubmitMessage(TravelTask task){
        //1、构建 Header
        Map<String,Object> header=new HashMap<>();
        header.put("traceId",task.getTrace_id());
        header.put("msgId", UUID.randomUUID().toString().replace("-",""));   //后续再换成--业务前缀 + 时间戳(毫秒) + 机器ID + 序列号
        header.put("businessType","TRAVEL_TASK_SUBMIT");   //businessType是业务类型
        header.put("version","1.0");
        header.put("timestamp",System.currentTimeMillis());
        header.put("userId",task.getUser_id());

        //2、构建业务体
        Map<String,Object> taskData=new HashMap<>();
        taskData.put("userQuery",task.getUser_query());
        taskData.put("days",task.getDays());
        taskData.put("budget",task.getBudget());
        taskData.put("pace",task.getPace());
        taskData.put("planId",task.getPlan_id());

        //3、外层的body
        Map<String,Object> body=new HashMap<>();
        body.put("taskId",task.getTask_id());
        body.put("taskData",taskData);

        //4、构建扩展字段
        Map<String,Object> extension=new HashMap<>();
        extension.put("mqTopic","travel-task-submit");
        extension.put("mqTag","task-submit");
        extension.put("timeout",300000);

        //5、整合成完整的消息体
        Map<String,Object> message=new HashMap<>();
        message.put("header",header);
        message.put("body",body);
        message.put("extension",extension);

        //6、生成签名
        String signContent=task.getTrace_id()+header.get("timestamp")+ JSON.toJSONString(body)+signSecret;
        String sign=new HMac(HmacAlgorithm.HmacSHA256,signSecret.getBytes()).digestHex(signContent);
        header.put("sign",sign);

        return JSON.toJSONString(message);
    }

    /**
     * 签名验证
     */
    public boolean verifySign(String messageJson){
        try{
            Map<String,Object> message=JSON.parseObject(messageJson,Map.class);
            Map<String,Object> header=(Map<String,Object>) message.get("header");
            Map<String,Object> body=(Map<String,Object>) message.get("body");

            String signContent=String.valueOf(header.get("traceId")) + String.valueOf(header.get("timestamp")) +JSON.toJSONString(body)+signSecret;
            String actualSign=new HMac(HmacAlgorithm.HmacSHA256,signSecret.getBytes()).digestHex(signContent);
            String expectSign=String.valueOf(header.get("sign"));

            return actualSign.equals(expectSign);
        }catch (Exception e){
            log.error("签名验证失败:"+e);
            return false;
        }
    }
}
