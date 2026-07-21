package com.travel.service.impl;

import cn.hutool.http.HttpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.ChatMessageRoleEnum;
import com.travel.common.TaskStatusEnum;
import com.travel.dto.AiMessageCallbackDTO;
import com.travel.entity.ChatMessage;
import com.travel.entity.TravelParsePlan;
import com.travel.entity.TravelTask;
import com.travel.mapper.ChatMessageMapper;
import com.travel.mapper.TravelParsePlanMapper;
import com.travel.mapper.TravelTaskMapper;
import com.travel.mapper.UserMapper;
import com.travel.pojo.TravelPlanPojo;
import com.travel.service.ReceiveAIService;
import com.travel.util.RedisUtil;
import com.travel.vo.ChatMessageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class ReceiveAIServiceImpl implements ReceiveAIService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private TravelTaskMapper travelTaskMapper;

    @Autowired
    private TravelParsePlanMapper travelParsePlanMapper;

    @Value("${travel.python-api-url}")
    private String pythonApiUrl;

    @Autowired
    private RedisUtil redisUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 异步发送请求到Python，不等待响应立即返回
     * Python处理完成后会回调 /sendMessageByPython/receiveAI 接口
     *
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param msg_id 消息ID
     * @param content 用户消息内容
     * @param startDate 出发日期（可选）
     * @param days 旅游天数（可选）
     * @param flowId flow ID（用于中断恢复）
     */
    @Override
    public void sendRequestToPythonAsync(Long sessionId, Long userId, Long msg_id, String content, String startDate, Integer days, String flowId) {
        // 构造请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sessionId", sessionId);
        requestBody.put("userId", userId);
        requestBody.put("messageId", msg_id);
        requestBody.put("content", content);
        // 设置回调地址，Python处理完成后回调此地址
        requestBody.put("callbackUrl", "http://localhost:9999/sendMessageByPython/receiveAI");
        // 透传日期参数
        if (startDate != null && !startDate.isBlank()) {
            requestBody.put("startDate", startDate);
        }
        if (days != null) {
            requestBody.put("days", days);
        }
        if (flowId != null && !flowId.isBlank()) {
            requestBody.put("flowId", flowId);
        }

        // 使用 CompletableFuture 异步发送，不阻塞主线程
        CompletableFuture.runAsync(() -> {
            try {
                log.info("异步发送请求到Python，sessionId={}, userId={}, content={}",
                        sessionId, userId, content.substring(0, Math.min(50, content.length())));

                String responseJson = HttpUtil.createPost(pythonApiUrl + "/chatByJava")
                        .header("Content-Type", "application/json")
                        .timeout(10000)  // 10秒超时，只等待Python确认接收
                        .body(objectMapper.writeValueAsString(requestBody))
                        .execute()
                        .body();

                log.info("Python API 响应: {}", responseJson);
            } catch (Exception e) {
                log.error("调用Python API失败，sessionId={}, userId={}, error={}",
                        sessionId, userId, e.getMessage());
            }
        });
    }

    /**
     * 接收python返回的AI回复，并且发送到前端显示
     * @param callbackDTO
     * @param userId
     * @return chatMessageVO
     */
    @Override
    public ChatMessageVO receiveAIByPython(AiMessageCallbackDTO callbackDTO, Long userId) throws JsonProcessingException {
        //1、解析DTO，获取里面的content和planjson
        String content = callbackDTO.getContent();
        String planJson = callbackDTO.getPlanJson();
        Long sessionId = callbackDTO.getSessionId();
        AiMessageCallbackDTO.TaskInfo task = callbackDTO.getTask();
        Object interaction = callbackDTO.getInteraction();
        String flowId = callbackDTO.getFlowId();
        String interactionJson = null;

        // 2、如果是 waiting_ 交互状态，保存到 Redis
        if (interaction != null && flowId != null) {
            interactionJson = objectMapper.writeValueAsString(interaction);
            try {
                String redisKey = "interaction:" + sessionId + ":" + flowId;
                redisUtil.set(redisKey, interactionJson, 3600L);
                log.info("交互状态已存入Redis: key={}", redisKey);
            } catch (Exception e) {
                log.error("保存交互状态到Redis失败: {}", e.getMessage());
            }
        }

        //将消息保存到数据库（不管是不是 waiting_ 状态，都保存）
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContent(content);
        chatMessage.setRole(ChatMessageRoleEnum.ASSISTANT);
        chatMessage.setPlanJson(planJson);
        chatMessage.setUserId(userId);
        chatMessage.setSessionId(sessionId);
        chatMessage.setFlowId(flowId);
        chatMessage.setInteraction(interactionJson);
        chatMessage.setCreateTime(LocalDateTime.now());

        int insert = chatMessageMapper.insert(chatMessage);
        if(insert==0){
            log.error("保存AI回答消息失败，sessionId={}, userId={}",sessionId,userId);
            throw new RuntimeException("保存AI回答消息失败");
        }
        log.info("AI回答消息已保存, msgId={}",chatMessage.getMsgId());

        // 如果是 waiting_ 交互状态，此时已经保存到数据库，直接返回
        if (interaction != null && flowId != null) {
            ChatMessageVO chatMessageVO = new ChatMessageVO();
            chatMessageVO.setContent(content);
            chatMessageVO.setPlanJson(null);
            chatMessageVO.setUserId(userId);
            chatMessageVO.setSessionId(sessionId);
            chatMessageVO.setRole(ChatMessageRoleEnum.ASSISTANT);
            chatMessageVO.setMsgId(chatMessage.getMsgId());
            chatMessageVO.setCreateTime(LocalDateTime.now());
            chatMessageVO.setUserNickname(userMapper.selectById(userId).getNickname());
            chatMessageVO.setFlowId(flowId);
            chatMessageVO.setInteraction(interactionJson);
            return chatMessageVO;
        }

        //将行程规划plan和任务task存进数据库
        System.out.println("先看一下具体的plan结构：----------------");
        System.out.println(callbackDTO.getPlanJson());

        TravelTask travelTask = new TravelTask();
        travelTask.setUser_id(task.getUserId());
        travelTask.setUser_query(task.getUserQuery());
        travelTask.setDays(task.getDays());
        travelTask.setBudget(task.getBudget());
        travelTask.setPace(task.getPace());
        travelTask.setDestination(task.getDestination());
        //TODO:这里需要python那边在解析任务时带上状态的处理，现在固定初始化，后面将计划生成存进数据库后就需要根据python传过来的数据来修改status
        travelTask.setStatus(TaskStatusEnum.INIT);
        travelTask.setError_msg(task.getErrorMsg());

        int insert1 = travelTaskMapper.insert(travelTask);
        if(insert1==0){
            log.error("保存任务失败，userId={}",task.getUserId());
            throw new RuntimeException("保存任务失败");
        }
        log.info("任务已保存, userId={}",task.getUserId());


        //先解析planJson, 反序列化：把字符串转成 Java 对象
        TravelPlanPojo travelPlanPojo = objectMapper.readValue(planJson, TravelPlanPojo.class);

        //从解析到的pojo中获取对应的数据，将plan存进数据库
        TravelParsePlan travelParsePlan = new TravelParsePlan();
        travelParsePlan.setTask_id(travelTask.getTask_id());
        travelParsePlan.setUser_id(travelPlanPojo.getUserId());
        travelParsePlan.setTitle(travelPlanPojo.getTitle());
        travelParsePlan.setDestination(travelPlanPojo.getDestination());
        travelParsePlan.setDays(travelPlanPojo.getDays());
        travelParsePlan.setBudget(travelPlanPojo.getBudget());
        travelParsePlan.setPace(travelPlanPojo.getPace());
        travelParsePlan.setStart_date(travelPlanPojo.getStartDate());
        travelParsePlan.setDaily_plans(objectMapper.writeValueAsString(travelPlanPojo.getDailyPlans()));
        travelParsePlan.setTotal_estimated_cost(travelPlanPojo.getTotalEstimatedCost());
        travelParsePlan.setNotes(travelPlanPojo.getNotes());
        travelParsePlan.setRaw_markdown(travelPlanPojo.getRawMarkdown());
        //TODO:这里的trace_id我觉得应该在Java这边生成，而不是在python那边生成。并且不应该用userId+sessionId+时间戳，如果同一时间同一个用户在同一个会话中发送多条消息的话就会冲突了。
        travelParsePlan.setTrace_id(callbackDTO.getTraceId());
        travelParsePlan.setCreate_time(LocalDateTime.now());
        travelParsePlan.setUpdate_time(LocalDateTime.now());

        int insert2 = travelParsePlanMapper.insert(travelParsePlan);
        if(insert2==0){
            log.error("保存行程规划失败，userId={},taskId={}",task.getUserId(),travelTask.getTask_id());
            throw new RuntimeException("保存行程规划失败");
        }
        log.info("行程规划已保存, userId={},taskId={}",task.getUserId(),travelTask.getTask_id());


        //封装 chatMessageVO 返回给前端
        ChatMessageVO chatMessageVO = new ChatMessageVO();
        chatMessageVO.setContent(content);
        chatMessageVO.setPlanJson(planJson);
        chatMessageVO.setUserId(userId);
        chatMessageVO.setSessionId(sessionId);
        chatMessageVO.setRole(ChatMessageRoleEnum.ASSISTANT);
        chatMessageVO.setMsgId(chatMessage.getMsgId());  // 使用插入后自增的ID
        chatMessageVO.setCreateTime(LocalDateTime.now());
        chatMessageVO.setUserNickname(userMapper.selectById(userId).getNickname());
        chatMessageVO.setFlowId(flowId);
        chatMessageVO.setInteraction(interactionJson);

        return chatMessageVO;
    }
}
