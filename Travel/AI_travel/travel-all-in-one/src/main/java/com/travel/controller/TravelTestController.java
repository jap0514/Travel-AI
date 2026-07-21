package com.travel.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.common.Result;
import com.travel.dto.ChatMessageDTO;
import com.travel.entity.ChatMessage;
import com.travel.mapper.ChatMessageMapper;
import com.travel.service.ChatMessageService;
import com.travel.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 旅行规划完整流程测试页面
 * 访问 http://localhost:9999/test/travel/page
 */
@RestController
@RequestMapping("/test/travel")
public class TravelTestController {

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private RedisUtil redisUtil;

    @GetMapping("/page")
    public String testPage() {
        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>旅行规划完整流程测试</title>
    <style>
        * { box-sizing: border-box; }
        body { font-family: Arial, sans-serif; max-width: 1000px; margin: 0 auto; padding: 20px; background: #f5f5f5; }
        h1 { text-align: center; color: #333; }
        .container { background: white; border-radius: 8px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        .form-group { margin-bottom: 15px; }
        label { display: inline-block; width: 120px; font-weight: bold; color: #555; }
        input, textarea, select { width: calc(100%% - 130px); padding: 10px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; }
        textarea { height: 80px; resize: vertical; }
        button { background: #4CAF50; color: white; padding: 12px 30px; border: none; border-radius: 4px; cursor: pointer; font-size: 16px; margin-top: 10px; }
        button:hover { background: #45a049; }
        button:disabled { background: #ccc; cursor: not-allowed; }
        .btn-secondary { background: #2196F3; }
        .btn-secondary:hover { background: #1976D2; }
        .btn-warning { background: #FF9800; }
        .btn-warning:hover { background: #F57C00; }
        pre { background: #1e1e1e; color: #d4d4d4; padding: 15px; border-radius: 4px; overflow-x: auto; font-size: 13px; max-height: 400px; overflow-y: auto; }
        .section { margin-top: 30px; padding-top: 20px; border-top: 2px solid #eee; }
        .chat-box { border: 1px solid #ddd; border-radius: 4px; height: 300px; overflow-y: auto; padding: 15px; background: #fafafa; margin-bottom: 15px; }
        .msg { margin: 10px 0; padding: 10px 15px; border-radius: 8px; max-width: 80%%; }
        .msg.user { background: #dcf8c6; margin-left: auto; text-align: right; }
        .msg.ai { background: white; border: 1px solid #ddd; }
        .msg .time { font-size: 11px; color: #999; margin-top: 5px; }
        .msg .content { margin-top: 5px; }
        .status { padding: 10px; border-radius: 4px; margin: 10px 0; font-weight: bold; text-align: center; }
        .status.waiting { background: #fff3cd; color: #856404; }
        .status.success { background: #d4edda; color: #155724; }
        .status.error { background: #f8d7da; color: #721c24; }
        .interaction-panel { background: #e8f4fd; border: 2px solid #2196F3; border-radius: 8px; padding: 20px; margin: 15px 0; }
        .interaction-panel h3 { margin-top: 0; color: #1976D2; }
        .hotel-card { background: white; border: 1px solid #4CAF50; border-radius: 4px; padding: 12px; margin: 8px 0; cursor: pointer; }
        .hotel-card:hover { background: #f1f8e9; }
        .hotel-card.selected { background: #c8e6c9; border-width: 2px; }
        .info { font-size: 12px; color: #666; }
        .loading { text-align: center; padding: 20px; color: #666; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🗺️ 旅行规划完整流程测试</h1>

        <!-- 输入区 -->
        <div class="form-group">
            <label>用户ID:</label>
            <input type="number" id="userId" value="1">
        </div>
        <div class="form-group">
            <label>会话ID:</label>
            <input type="number" id="sessionId" value="1001">
        </div>
        <div class="form-group">
            <label>出发日期:</label>
            <input type="date" id="startDate" value="2026-08-01">
        </div>
        <div class="form-group">
            <label>天数:</label>
            <input type="number" id="days" value="3" min="1" max="30">
        </div>
        <div class="form-group">
            <label>JWT Token:</label>
            <input type="text" id="jwtToken" placeholder="请输入JWT Token（从登录接口获取）" style="width:100%">
        </div>
        <div class="form-group">
            <label>用户消息:</label>
            <textarea id="message" placeholder="请输入旅行规划需求，例如：我想去北京玩3天，帮我规划一下">我想去北京玩3天，帮我规划一下</textarea>
        </div>
        <button id="sendBtn" onclick="sendMessage()">🚀 发送规划请求</button>
        <button class="btn-secondary" onclick="clearChat()">🗑️ 清空聊天</button>

        <!-- 状态区 -->
        <div id="statusBox" style="display:none"></div>

        <!-- 交互区（等待用户选择酒店等） -->
        <div id="interactionPanel" class="interaction-panel" style="display:none">
            <h3 id="interactionTitle">📋 请选择：</h3>
            <div id="interactionContent"></div>
            <div class="form-group" style="margin-top:15px">
                <label>您的回复:</label>
                <input type="text" id="interactionReply" placeholder="输入您的选择">
            </div>
            <button onclick="sendInteractionReply()">✅ 提交回复</button>
        </div>

        <!-- 聊天记录区 -->
        <div class="section">
            <h2>💬 聊天记录</h2>
            <div class="chat-box" id="chatBox"></div>
        </div>

        <!-- 原始响应区 -->
        <div class="section">
            <h2>📄 Python 原始响应</h2>
            <pre id="rawResponse">等待发送请求...</pre>
        </div>
    </div>

    <script>
        const BASE = 'http://localhost:9999';
        let currentFlowId = null;          // 当前 flow ID
        let lastShownAiMsgId = null;       // 已显示的最后一条AI消息ID，避免重复显示
        let pollingTimer = null;

        // 发送消息
        function sendMessage() {
            const userId = document.getElementById('userId').value;
            const sessionId = document.getElementById('sessionId').value;
            const startDate = document.getElementById('startDate').value;
            const days = document.getElementById('days').value;
            const content = document.getElementById('message').value.trim();

            if (!content) { alert('请输入消息'); return; }

            // 发送前清空交互面板
            hideInteractionPanel();
            document.getElementById('message').value = '';

            const btn = document.getElementById('sendBtn');
            btn.disabled = true;
            setStatus('发送中...', 'waiting');

            // 用户消息立即显示（带临时ID标记）
            const userMsgDiv = addChat('user', content);

            const payload = {
                sessionId: parseInt(sessionId),
                userId: parseInt(userId),
                role: 'USER',
                content: content,
                startDate: startDate,
                days: parseInt(days),
                flowId: currentFlowId,  // 首次为null，后续带上前一个flowId
            };

            fetch(`${BASE}/message/sendMessage`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (document.getElementById('jwtToken').value || '') },
                body: JSON.stringify(payload)
            })
            .then(r => r.json())
            .then(r => {
                document.getElementById('rawResponse').textContent = JSON.stringify(r, null, 2);
                setStatus('等待响应...', 'waiting');
                btn.disabled = false;
                // 从响应中提取 flowId（首次请求会返回null，后续请求带flowId）
                currentFlowId = r.data && r.data.flowId;
                lastShownAiMsgId = null;  // 重置，开始监控新消息
                startPolling();
            })
            .catch(e => {
                setStatus('发送失败: ' + e.message, 'error');
                btn.disabled = false;
            });
        }

        // 轮询获取Python响应
        function startPolling() {
            if (pollingTimer) clearInterval(pollingTimer);
            pollingTimer = setInterval(() => {
                const sessionId = document.getElementById('sessionId').value;
                const flowId = currentFlowId;
                const pollHeaders = { 'Authorization': 'Bearer ' + (document.getElementById('jwtToken').value || '') };

                // 1. 轮询Redis中的interaction状态（有flowId时）
                if (flowId) {
                    fetch(`${BASE}/test/travel/interaction/` + sessionId + '/' + flowId, { headers: pollHeaders })
                        .then(r => r.json())
                        .then(r => {
                            if (r.data) {
                                const interaction = typeof r.data === 'string' ? JSON.parse(r.data) : r.data;
                                if (interaction && interaction.status && interaction.status.startsWith('waiting_')) {
                                    // Python要求用户交互，显示交互面板
                                    showInteraction(interaction.status, interaction);
                                    clearInterval(pollingTimer);
                                }
                            }
                        })
                        .catch(e => console.error('interaction轮询失败', e));
                }

                // 2. 轮询数据库中的AI消息（只显示新消息）
                fetch(`${BASE}/test/travel/messages/` + sessionId, { headers: pollHeaders })
                    .then(r => r.json())
                    .then(r => {
                        const data = r.data || [];
                        // 找最后一条ASSISTANT消息
                        const allAiMsgs = data.filter(m => m.role && m.role.toUpperCase().includes('ASSISTANT'));
                        if (allAiMsgs.length === 0) return;

                        const latestAi = allAiMsgs[allAiMsgs.length - 1];

                        // 只显示新的AI消息（msgId大于已显示的）
                        if (lastShownAiMsgId === null || latestAi.msgId > lastShownAiMsgId) {
                            // 如果有交互状态，显示交互面板；否则作为普通AI回复显示
                            const interactionData = latestAi.interaction
                                ? (typeof latestAi.interaction === 'string' ? JSON.parse(latestAi.interaction) : latestAi.interaction)
                                : null;

                            if (interactionData && interactionData.status && interactionData.status.startsWith('waiting_')) {
                                // 有交互状态，保存flowId用于后续
                                if (latestAi.flowId) currentFlowId = latestAi.flowId;
                                showInteraction(interactionData.status, interactionData);
                                clearInterval(pollingTimer);
                            } else {
                                // 无交互状态，作为普通AI消息显示（规划完成等）
                                addChat('ai', latestAi.content);
                                setStatus('处理完成', 'success');
                                clearInterval(pollingTimer);
                            }
                            lastShownAiMsgId = latestAi.msgId;
                        }
                        document.getElementById('rawResponse').textContent = JSON.stringify(r, null, 2);
                    })
                    .catch(e => console.error('消息轮询失败', e));
            }, 2000);
        }

        // 添加聊天消息到面板
        function addChat(role, content, time) {
            const box = document.getElementById('chatBox');
            const div = document.createElement('div');
            div.className = `msg ${role}`;
            div.innerHTML = `<div class="content">${escapeHtml(content)}</div><div class="time">${time || new Date().toLocaleTimeString()}</div>`;
            box.appendChild(div);
            box.scrollTop = box.scrollHeight;
            return div;
        }

        // 显示状态
        function setStatus(msg, type) {
            const box = document.getElementById('statusBox');
            box.style.display = 'block';
            box.className = `status ${type}`;
            box.textContent = msg;
        }

        // HTML转义
        function escapeHtml(text) {
            if (!text) return '';
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }

        // 显示交互面板
        function showInteraction(status, data) {
            const panel = document.getElementById('interactionPanel');
            const title = document.getElementById('interactionTitle');
            const content = document.getElementById('interactionContent');
            panel.style.display = 'block';

            if (status === 'waiting_user_hotel') {
                title.textContent = '🏨 请选择酒店：';
                let html = '';
                (data.hotels || []).forEach((h, i) => {
                    html += `<div class="hotel-card" onclick="selectHotel(${i})">
                        <strong>${h.hotelName || h.hotel_name}</strong> - ${h.roomTypeName || h.room_type_name}
                        <div class="info">房间号: ${h.roomNo || h.room_no} | 价格: ¥${h.price || h.price_per_night}/晚</div>
                    </div>`;
                });
                content.innerHTML = html || '<p>暂无可选酒店</p>';
            } else if (status === 'waiting_user_decision') {
                title.textContent = '⚠️ 无酒店可用，请选择：';
                content.innerHTML = '<p>所有酒店已无空房</p><ol><li>继续规划（不包含酒店）</li><li>重新生成</li><li>取消</li></ol>';
            } else if (status === 'waiting_user_alternatives') {
                title.textContent = '🔄 原酒店无房，请选择替代：';
                let html = '';
                (data.hotels || []).forEach((h, i) => {
                    html += `<div class="hotel-card" onclick="selectHotel(${i})">
                        <strong>${h.hotelName || h.hotel_name}</strong> - ${h.roomTypeName || h.room_type_name}
                        <div class="info">房间号: ${h.roomNo || h.room_no}</div>
                    </div>`;
                });
                content.innerHTML = html || '<p>暂无语替代酒店</p>';
            } else if (status === 'waiting_user_alarm') {
                title.textContent = '⏰ 设置有空房提醒：';
                content.innerHTML = '<p>该酒店暂无空房，是否设置提醒？</p><p>请在下方输入您希望收到提醒的日期和时间</p>';
            } else {
                title.textContent = '💬 请回复：';
                content.innerHTML = `<p>${data.message || ''}</p>`;
            }
            setStatus('等待您的选择...', 'waiting');
        }

        // 隐藏交互面板
        function hideInteractionPanel() {
            document.getElementById('interactionPanel').style.display = 'none';
            document.getElementById('interactionReply').value = '';
        }

        // 选择酒店
        function selectHotel(index) {
            document.querySelectorAll('.hotel-card').forEach((el, i) => {
                el.classList.toggle('selected', i === index);
            });
            // 自动填入序号
            document.getElementById('interactionReply').value = String(index + 1);
        }

        // 提交交互回复
        function sendInteractionReply() {
            const reply = document.getElementById('interactionReply').value.trim();
            if (!reply) { alert('请输入回复'); return; }

            // 隐藏面板，消息作为新对话发送
            hideInteractionPanel();
            document.getElementById('message').value = reply;
            sendMessage();
        }

        // 清空聊天
        function clearChat() {
            document.getElementById('chatBox').innerHTML = '';
            document.getElementById('rawResponse').textContent = '等待发送请求...';
            hideInteractionPanel();
            document.getElementById('statusBox').style.display = 'none';
            currentFlowId = null;
            lastShownAiMsgId = null;
            if (pollingTimer) clearInterval(pollingTimer);
        }
    </script>
</body>
</html>
                """;
    }

    /**
     * 获取会话的所有消息（用于轮询）
     */
    @GetMapping("/messages/{sessionId}")
    public Result<?> getMessages(@PathVariable Long sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreateTime);
        var messages = chatMessageMapper.selectList(wrapper);
        return Result.success(messages);
    }

    /**
     * 从 Redis 获取交互状态（用于轮询 interaction）
     * key: interaction:{sessionId}:{flowId}
     */
    @GetMapping("/interaction/{sessionId}/{flowId}")
    public Result<?> getInteraction(@PathVariable Long sessionId, @PathVariable String flowId) {
        String key = "interaction:" + sessionId + ":" + flowId;
        Object interaction = redisUtil.get(key);
        return Result.success(interaction);
    }
}
