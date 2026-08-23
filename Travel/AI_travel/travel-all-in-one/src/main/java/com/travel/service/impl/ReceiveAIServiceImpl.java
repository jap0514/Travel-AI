package com.travel.service.impl;

import cn.hutool.http.HttpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.ChatMessageRoleEnum;
import com.travel.common.ResultCode;
import com.travel.common.TaskStatusEnum;
import com.travel.common.constant.SignatureConstant;
import com.travel.exception.BusinessException;
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
import com.travel.util.SignatureUtil;
import com.travel.util.TraceIdUtil;
import com.travel.vo.ChatMessageVO;
import com.travel.websocket.MessageWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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

    @Value("${travel.java-callback-base-url}")
    private String javaCallbackBaseUrl;

    @Value("${travel.signature.secret-key-java:travel-secret-key-java}")
    private String secretKeyForJava;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private MessageWebSocketHandler messageWebSocketHandler;

    /** HTTP调用专用线程池 */
    @Autowired
    @Qualifier("httpExecutor")
    private Executor httpExecutor;

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
        // 获取当前请求的 traceId（用于跨线程传递）
        String traceId = TraceIdUtil.getTraceId();

        // 构造请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sessionId", sessionId);
        requestBody.put("userId", userId);
        requestBody.put("messageId", msg_id);
        requestBody.put("content", content);
        // 设置回调地址，Python处理完成后回调此地址
        requestBody.put("callbackUrl", javaCallbackBaseUrl + "/sendMessageByPython/receiveAI");
        // 透传 traceId，让 Python 在回调时带回
        requestBody.put("traceId", traceId);
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

        // 使用 CompletableFuture 异步发送，不阻塞主线程（使用httpExecutor线程池）
        CompletableFuture.runAsync(() -> {
            // 将 traceId 传递到新线程的 MDC
//            TraceIdUtil.setTraceId(traceId);  线程池里面已经设置了traceId
            try {
                log.info("异步发送请求到Python，traceId={}, sessionId={}, userId={}, content={}",
                        traceId, sessionId, userId, content.substring(0, Math.min(50, content.length())));

                // 构造请求体
                String requestBodyJson = objectMapper.writeValueAsString(requestBody);

                // 生成签名
                String timestamp = String.valueOf(System.currentTimeMillis());
                String sign = SignatureUtil.generateSign(secretKeyForJava, timestamp, requestBodyJson);

                String responseJson = HttpUtil.createPost(pythonApiUrl + "/chatByJava")
                        .header(SignatureConstant.HEADER_APP_ID, SignatureConstant.APP_ID_JAVA)
                        .header(SignatureConstant.HEADER_TIMESTAMP, timestamp)
                        .header(SignatureConstant.HEADER_SIGN, sign)
                        .header("Content-Type", "application/json")
                        .timeout(10000)  // 10秒超时，只等待Python确认接收
                        .body(requestBodyJson)
                        .execute()
                        .body();

                log.info("Python API 响应: {}", responseJson);
            } catch (Exception e) {
                log.error("调用Python API失败，traceId={}, sessionId={}, userId={}, error={}",
                        traceId, sessionId, userId, e.getMessage());
            } finally {
                TraceIdUtil.clear();
            }
        }, httpExecutor);
    }

    /**
     * 接收python返回的AI回复，并且发送到前端显示
     * @param callbackDTO
     * @param userId
     * @return chatMessageVO
     */
    @Override
    public ChatMessageVO receiveAIByPython(AiMessageCallbackDTO callbackDTO, Long userId) throws JsonProcessingException {
        // 打印Python回调的完整数据，用于调试
        log.info("【Python回调】receiveAIByPython 被调用");
        log.info("【Python回调】callbackDTO={}", callbackDTO);
        log.info("【Python回调】sessionId={}, userId={}", callbackDTO.getSessionId(), callbackDTO.getUserId());
        log.info("【Python回调】flowId={}", callbackDTO.getFlowId());
        log.info("【Python回调】interaction={}", callbackDTO.getInteraction());
        log.info("【Python回调】content={}", callbackDTO.getContent());

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
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "保存AI回答消息失败");
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

            // WebSocket 推送给前端（交互状态）
            try {
                boolean pushed = messageWebSocketHandler.sendMessageToUser(userId, chatMessageVO);
                if (pushed) {
                    log.info("WebSocket 推送交互状态成功: userId={}, msgId={}", userId, chatMessageVO.getMsgId());
                } else {
                    // 消息已落库，前端可通过拉取会话消息补齐
                    log.warn("WebSocket 推送交互状态未送达（用户未连接）: userId={}, msgId={}", userId, chatMessageVO.getMsgId());
                }
            } catch (Exception e) {
                log.error("WebSocket 推送失败: userId={}, error={}", userId, e.getMessage());
            }

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
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "保存任务失败");
        }
        log.info("任务已保存, userId={}",task.getUserId());


        //先解析planJson, 反序列化：把字符串转成 Java 对象
        TravelPlanPojo travelPlanPojo = objectMapper.readValue(planJson, TravelPlanPojo.class);

        //从解析到的pojo中获取对应的数据，将plan存进数据库
        TravelParsePlan travelParsePlan = new TravelParsePlan();
        travelParsePlan.setTaskId(travelTask.getTask_id());
        travelParsePlan.setUserId(travelPlanPojo.getUserId());
        travelParsePlan.setTitle(travelPlanPojo.getTitle());
        travelParsePlan.setDestination(travelPlanPojo.getDestination());
        travelParsePlan.setDays(travelPlanPojo.getDays());
        travelParsePlan.setBudget(travelPlanPojo.getBudget());
        travelParsePlan.setPace(travelPlanPojo.getPace());
        travelParsePlan.setStartDate(travelPlanPojo.getStartDate());
        travelParsePlan.setDailyPlans(objectMapper.writeValueAsString(travelPlanPojo.getDailyPlans()));
        travelParsePlan.setTotalEstimatedCost(travelPlanPojo.getTotalEstimatedCost());
        travelParsePlan.setNotes(travelPlanPojo.getNotes());
        travelParsePlan.setRawMarkdown(travelPlanPojo.getRawMarkdown());
        //TODO:这里的trace_id我觉得应该在Java这边生成，而不是在python那边生成。并且不应该用userId+sessionId+时间戳，如果同一时间同一个用户在同一个会话中发送多条消息的话就会冲突了。
        travelParsePlan.setTraceId(callbackDTO.getTraceId());
        travelParsePlan.setCreateTime(LocalDateTime.now());
        travelParsePlan.setUpdateTime(LocalDateTime.now());

        int insert2 = travelParsePlanMapper.insert(travelParsePlan);
        if(insert2==0){
            log.error("保存行程规划失败，userId={},taskId={}",task.getUserId(),travelTask.getTask_id());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "保存行程规划失败");
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

        // WebSocket 推送给前端（正常流程）
        try {
            messageWebSocketHandler.sendMessageToUser(userId, chatMessageVO);
            log.info("WebSocket 推送消息成功: userId={}, msgId={}", userId, chatMessageVO.getMsgId());
        } catch (Exception e) {
            log.error("WebSocket 推送失败: userId={}, error={}", userId, e.getMessage());
        }

        return chatMessageVO;
    }
}
