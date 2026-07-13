"""Web API 路由 — 封装多智能体旅行规划系统为 REST API + SSE 实时进度"""

import time
import uuid
import json
import traceback
import asyncio
from datetime import datetime
from email.policy import default
import requests

from fastapi import APIRouter, HTTPException
from fastapi.responses import JSONResponse, StreamingResponse
from typing import Optional
from pydantic import BaseModel, Field

from app.model.message_model import ChatMessage
from app.model.plan_model import TravelPlan
from app.service.multi_agent_travel import process_with_agent
from app.utils.redis_client import get_session_context, save_session_context
from app.utils.sse_publisher import sse_emitter
from app.config.logger import logger

router = APIRouter(prefix="/api")


# ───────────────────────────── 请求/响应模型 ─────────────────────────────

class ChatRequest(BaseModel):
    content: str = Field(..., min_length=1, description="用户消息内容")
    user_id: int = Field(default=1, description="用户 ID")
    session_id: Optional[int] = Field(default=None, description="会话 ID（不传则自动分配）")


class ChatResponse(BaseModel):
    success: bool = True
    data: dict | None = None
    error: str | None = None


class HealthResponse(BaseModel):
    status: str = "ok"
    model: str = ""
    servers: dict = {}

class ChatRequestFromJava(BaseModel):
    sessionId: int = Field(default=3001, description="会话ID")
    userId: int = Field(default=1, description="用户ID")
    messageId: int = Field(default=1001, description="用户消息ID")
    content: str = Field(..., min_length=1, description="用户内容")
    callbackUrl: Optional[str] = Field(default=None, description="Java回调地址，Python处理完成后回调此地址")

class ChatResponseToJava(BaseModel):
    sessionId: int = Field(default=3001,description="会话ID")
    userId: int = Field(default=1,description="用户ID")
    relatedMsgId: int = Field(default=2001,description="关联的用户消息ID")
    content: str = Field(..., min_length=1, description="内容")
    planJson: dict | None = None
    traceId: str


# ───────────────────────────── API 端点 ─────────────────────────────

async def _call_java_callback(callback_url: str, payload: dict):
    """在后台线程中同步调用 Java 回调接口，避免阻塞事件循环"""
    loop = asyncio.get_event_loop()
    try:
        await loop.run_in_executor(None, lambda: requests.post(
            callback_url,
            json=payload,
            headers={"Content-Type": "application/json"},
            timeout=30
        ))
    except Exception as e:
        logger.error(f"回调Java失败: {e}, callbackUrl={callback_url}")


@router.post("/chatByJava", response_model=ChatResponseToJava)
async def chat_by_java(request: ChatRequestFromJava):
    """接收Java发过来的用户请求，立即返回，后台异步处理，完成后回调Java"""
    sessionId = request.sessionId
    messageId = request.messageId
    userId = request.userId
    trace_id = f"{request.userId}_{request.sessionId}_{time.time_ns()}"
    log = logger.bind(trace_id=trace_id)
    log.info(f"python端成功接收到Java发过来的消息: {request.content[:80]}...")

    # 立即返回，不等待处理完成
    asyncio.create_task(_process_and_callback(request, trace_id))

    return ChatResponseToJava(
        sessionId=sessionId,
        userId=userId,
        relatedMsgId=messageId,
        content="请求已接收，正在处理中...",
        planJson=None,
        traceId=trace_id,
    )


async def _process_and_callback(request: ChatRequestFromJava, trace_id: str):
    """后台任务：执行 agent，处理完成后回调 Java"""
    log = logger.bind(trace_id=trace_id)

    # 构造 message
    message = ChatMessage(
        msg_id=request.messageId,
        session_id=request.sessionId,
        user_id=request.userId,
        role="user",
        content=request.content,
        plan_json=None,
        create_time=datetime.now(),
    )

    try:
        # 调用核心多智能体处理
        task, text_result, parsed_plan = await process_with_agent(message, trace_id)

        # 安全处理 planJson
        plan_json_dict = parsed_plan.to_dict() if parsed_plan else None
        response_content = text_result or "无返回内容"

        # 构造回调 payload
        callback_payload = {
            "sessionId": request.sessionId,
            "userId": request.userId,
            "relatedMsgId": request.messageId,
            "content": response_content,
            "planJson": json.dumps(plan_json_dict, ensure_ascii=False) if plan_json_dict else None,
            "traceId": trace_id,
            "task": {
                "taskId": task.task_id if task else None,
                "userId": task.user_id if task else None,
                "userQuery": task.user_query if task else None,
                "days": task.days if task else None,
                "budget": task.budget if task else None,
                "pace": task.pace if task else None,
                "destination": task.destination if task else None,
                "planId": task.plan_id if task else None,
                "errorMsg": task.error_msg if task else None,
                "resultStatus": task.result_status if task else None,
            } if task else None,
        }

        # 如果提供了回调地址，则回调 Java
        if request.callbackUrl:
            await _call_java_callback(request.callbackUrl, callback_payload)
            log.info(f"✅ 已回调Java，callbackUrl={request.callbackUrl}")
        else:
            log.warning("未配置callbackUrl，跳过回调")

    except Exception as e:
        log.exception(f"❌ 后台处理失败: {e}")
        if request.callbackUrl:
            error_payload = {
                "sessionId": request.sessionId,
                "userId": request.userId,
                "relatedMsgId": request.messageId,
                "content": f"处理失败: {str(e)}",
                "planJson": None,
                "traceId": trace_id,
            }
            await _call_java_callback(request.callbackUrl, error_payload)


@router.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """发送消息给多智能体系统，返回旅行规划或问答结果"""
    # 自动分配 session_id
    session_id = request.session_id or int(time.time() * 1000) % 1000000
    msg_id = int(time.time() * 1000)
    trace_id = str(uuid.uuid4())

    log = logger.bind(trace_id=trace_id)
    log.info(f"🌐 Web API 收到消息: {request.content[:80]}...")

    # 构造 ChatMessage（与 RocketMQ 消费者同样的结构）
    message = ChatMessage(
        msg_id=msg_id,
        session_id=session_id,
        user_id=request.user_id,
        role="user",
        content=request.content,
        plan_json=None,
        create_time=datetime.now(),
    )

    try:
        # 调用核心多智能体处理
        task, text_result, parsed_plan = await process_with_agent(message, trace_id)

        # 判断返回类型
        # process_with_agent 在 QA 流程返回 (None, qa_answer, None)
        # 在规划流程返回 (task, final_plan, parsed_plan)
        is_qa = parsed_plan is None

        # 构建响应数据
        response_data = {
            "type": "qa" if is_qa else "plan",
            "content": text_result or "",
            "session_id": session_id,
            "msg_id": msg_id,
            "trace_id": trace_id,
        }

        if not is_qa and task is not None:
            response_data["task"] = {
                "destination": task.destination,
                "days": task.days,
                "budget": task.budget,
                "pace": task.pace,
                "user_query": task.user_query,
            }
            if parsed_plan is not None:
                response_data["parsed_plan"] = parsed_plan.to_dict()

        log.info(f"✅ Web API 响应完成 | type={response_data['type']} | "
                 f"content_len={len(response_data['content'])}")

        return ChatResponse(data=response_data)

    except Exception as e:
        log.exception(f"❌ Web API 处理失败: {e}")
        tb = traceback.format_exc()
        return ChatResponse(
            success=False,
            error=f"处理您的请求时出错: {str(e)}",
        )


@router.post("/chat/stream")
async def chat_stream(request: ChatRequest):
    """
    SSE 流式聊天接口 — 同时返回 Agent 执行进度和最终结果

    前端通过 EventSource 监听 /api/chat/stream/{trace_id} 获取实时进度
    """
    session_id = request.session_id or int(time.time() * 1000) % 1000000
    msg_id = int(time.time() * 1000)
    trace_id = str(uuid.uuid4())

    log = logger.bind(trace_id=trace_id)
    log.info(f"🌐 [SSE] Web API 收到消息: {request.content[:80]}...")

    # 构造 ChatMessage
    message = ChatMessage(
        msg_id=msg_id,
        session_id=session_id,
        user_id=request.user_id,
        role="user",
        content=request.content,
        plan_json=None,
        create_time=datetime.now(),
    )

    async def event_generator():
        """SSE 事件生成器"""
        try:
            # 1. 先发送初始化事件，包含 trace_id
            yield f"event: init\ndata: {json.dumps({'trace_id': trace_id, 'session_id': session_id}, ensure_ascii=False)}\n\n"

            # 2. 订阅 SSE 事件
            queue = sse_emitter.subscribe(trace_id)

            # 3. 启动后台任务执行 Agent
            async def run_agent():
                try:
                    task, text_result, parsed_plan = await process_with_agent(message, trace_id)

                    # 判断返回类型
                    is_qa = parsed_plan is None

                    # 发送完成事件
                    result_data = {
                        "type": "qa" if is_qa else "plan",
                        "content": text_result or "",
                        "session_id": session_id,
                        "msg_id": msg_id,
                        "trace_id": trace_id,
                    }

                    if not is_qa and task is not None:
                        result_data["task"] = {
                            "destination": task.destination,
                            "days": task.days,
                            "budget": task.budget,
                            "pace": task.pace,
                            "user_query": task.user_query,
                        }
                        if parsed_plan is not None:
                            result_data["parsed_plan"] = parsed_plan.to_dict()

                    sse_emitter.emit_agent_complete(trace_id, result_data)

                except Exception as e:
                    log.exception(f"Agent 执行失败: {e}")
                    sse_emitter.emit_error(trace_id, str(e))

            # 启动 Agent 执行任务
            agent_task = asyncio.create_task(run_agent())

            # 4. 持续监听 SSE 事件并发送给前端
            while True:
                try:
                    # 等待 SSE 事件，超时 60 秒
                    event = await asyncio.wait_for(queue.get(), timeout=60)
                    event_str = json.dumps(event, ensure_ascii=False)
                    yield f"event: {event['type']}\ndata: {event_str}\n\n"

                    # 如果是完成或错误事件，退出循环
                    if event['type'] in ('agent_complete', 'error'):
                        break

                except asyncio.TimeoutError:
                    # 超时，发送心跳
                    yield f"event: heartbeat\ndata: {json.dumps({'time': datetime.now().isoformat()})}\n\n"

                # 如果 Agent 任务异常结束，也退出
                if agent_task.done() and agent_task.exception():
                    break

            # 等待 Agent 任务完成
            if not agent_task.done():
                try:
                    await asyncio.wait_for(agent_task, timeout=5)
                except:
                    pass

        except Exception as e:
            log.exception(f"SSE 流式处理异常: {e}")
            yield f"event: error\ndata: {json.dumps({'error': str(e)}, ensure_ascii=False)}\n\n"
        finally:
            # 清理订阅
            sse_emitter.unsubscribe(trace_id)

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",  # 禁用 nginx 缓冲
        }
    )


@router.get("/sessions/{session_id}/history")
async def get_session_history(session_id: int):
    """获取会话历史记录"""
    try:
        messages = get_session_context(session_id)
        return {"success": True, "data": messages or []}
    except Exception as e:
        logger.warning(f"获取会话历史失败: {e}")
        return {"success": False, "error": str(e), "data": []}


@router.get("/health", response_model=HealthResponse)
async def health_check():
    """健康检查 — 验证各服务的连接状态"""
    from app.config.settings import settings

    # 检查 Redis
    redis_ok = False
    try:
        from app.utils.redis_client import redis_client
        redis_client.ping()
        redis_ok = True
    except Exception:
        pass

    # 检查 Mem0 / Qdrant 本地存储
    mem0_ok = False
    try:
        import os
        mem0_path = settings.MEM0_CONFIG["vector_store"]["config"]["path"]
        mem0_ok = os.path.isdir(mem0_path)
    except Exception:
        pass

    return HealthResponse(
        status="ok",
        model=settings.AI_MODEL_NAME,
        servers={
            "redis": "connected" if redis_ok else "disconnected",
            "mem0_qdrant": "available" if mem0_ok else "unavailable",
            "mcp_tools": f"http://localhost:9997/sse (未验证)",
            "qdrant_rag": f"http://localhost:9996/sse (未验证)",
        },
    )
