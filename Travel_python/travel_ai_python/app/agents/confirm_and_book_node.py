# -*- coding: utf-8 -*-
"""
Confirm and Book Node - 确认并预定酒店节点

流程：
1. 进入节点 → 调用 verifyRoom 验证用户选择的房间是否仍然可预约
2. 可预约 → 调用 Java createOrder 正式下单
3. 不可预约 → 返回等待用户重新选择（从 researcher 返回的列表中）
"""
import requests
from app.agents.state import AgentState
from app.config.logger import logger
from app.config.settings import settings


def confirm_and_book_node(state: AgentState) -> dict:
    """
    Confirm and Book Node - 确认并预定酒店

    流程：
    1. chosen 为空 → 跳过（用户不要酒店）
    2. 调用 verifyRoom 验证房间是否仍然可预约
    3. 可预约 → 调用 Java createOrder 正式下单
    4. 不可预约 → 返回等待用户重新选择替代酒店
    """
    interaction = state.get("interaction") or {}
    chosen = interaction.get("chosen")

    logger.info(f"[ConfirmAndBook] 进入节点，chosen={chosen}")

    # 场景1：用户不要酒店，直接跳过
    if not chosen:
        logger.info("[ConfirmAndBook] 用户未选酒店，跳过预定")
        return {
            "booking_status": "skipped",
        }

    hotel_id = chosen.get("hotelId") or chosen.get("hotel_id") or ""
    room_type_id = chosen.get("roomTypeId") or chosen.get("room_type_id") or ""
    room_no = chosen.get("roomNo") or chosen.get("room_no") or ""
    hotel_name = chosen.get("hotelName") or chosen.get("hotel_name") or "未知酒店"
    checkin = _get_checkin(state)
    checkout = _get_checkout(state)
    user_id = state.get("user_id", 0)

    if not all([hotel_id, room_type_id, room_no]):
        logger.warning(f"[ConfirmAndBook] 房间信息不完整: hotel_id={hotel_id}, room_type_id={room_type_id}, room_no={room_no}")
        return _build_room_unavailable_response(state, hotel_name)

    # ==================== 验证房间是否仍然可预约 ====================
    logger.info(f"[ConfirmAndBook] 验证房间: hotelId={hotel_id}, roomTypeId={room_type_id}, roomNo={room_no}, checkin={checkin}, checkout={checkout}")
    available = _verify_room(hotel_id, room_type_id, room_no, checkin, checkout)

    if not available:
        logger.info("[ConfirmAndBook] 房间已被预订，触发重新选择")
        return _build_room_unavailable_response(state, hotel_name)

    # ==================== 房间可预约，正式下单 ====================
    logger.info(f"[ConfirmAndBook] 房间可预约，正式下单")
    book_result = _create_order(hotel_id, room_type_id, room_no, checkin, checkout, user_id)

    if not book_result:
        logger.warning("[ConfirmAndBook] 下单接口失败")
        return _build_room_unavailable_response(state, hotel_name)

    success = book_result.get("success", False)
    booking_id = book_result.get("bookingId") or book_result.get("booking_id") or ""
    error = book_result.get("errorMessage") or book_result.get("error") or ""

    if success:
        logger.info(f"[ConfirmAndBook] 下单成功: booking_id={booking_id}")
        return {
            "booking_status": "confirmed",
            "interaction": {
                **interaction,
                "status": None,
                "booking_id": booking_id,
                "message": f"✅ 酒店预定成功！{hotel_name}，入住日期：{checkin}，离店日期：{checkout}，订单号：{booking_id}",
            }
        }
    else:
        logger.warning(f"[ConfirmAndBook] 下单失败: {error}")
        return _build_room_unavailable_response(state, hotel_name)


def _get_checkin(state: AgentState) -> str:
    """获取入住日期"""
    interaction = state.get("interaction") or {}
    chosen = interaction.get("chosen") or {}
    task = state.get("task")

    checkin = chosen.get("checkInDate") or chosen.get("check_in_date") \
              or getattr(task, "start_date", None) or ""
    return checkin


def _get_checkout(state: AgentState) -> str:
    """获取离店日期"""
    interaction = state.get("interaction") or {}
    chosen = interaction.get("chosen") or {}
    task = state.get("task")

    checkout = chosen.get("checkOutDate") or chosen.get("check_out_date") or ""
    if checkout:
        return checkout

    start_date = _get_checkin(state)
    if start_date and task:
        days = getattr(task, "days", 3)
        from datetime import datetime, timedelta
        try:
            start = datetime.strptime(start_date, "%Y-%m-%d")
            end = start + timedelta(days=days)
            return end.strftime("%Y-%m-%d")
        except ValueError:
            pass
    return ""


def _verify_room(hotel_id, room_type_id, room_no, checkin, checkout) -> bool:
    """
    调用 Java verifyRoom 接口，验证指定房间是否可预约
    GET /hotel/api/hotel/verifyRoom?hotelId=&roomTypeId=&roomNo=&checkInDate=&checkOutDate=
    返回: True=可预约，False=已被预订
    """
    if not all([hotel_id, room_type_id, room_no, checkin, checkout]):
        return False

    try:
        url = f"{settings.JAVA_API_BASE_URL}/hotel/api/hotel/verifyRoom"
        params = {
            "hotelId": hotel_id,
            "roomTypeId": room_type_id,
            "roomNo": room_no,
            "checkInDate": checkin,
            "checkOutDate": checkout,
        }
        response = requests.get(url, params=params, timeout=10)
        if response.status_code == 200:
            result = response.json()
            data = result.get("data", {})
            return data.get("available", False)
        else:
            logger.warning(f"[VerifyRoom] 接口异常: status={response.status_code}")
            return False
    except requests.exceptions.Timeout:
        logger.warning("[VerifyRoom] 接口超时")
        return False
    except Exception as e:
        logger.warning(f"[VerifyRoom] 接口调用失败: {e}")
        return False


def _create_order(hotel_id, room_type_id, room_no, checkin, checkout, user_id) -> dict | None:
    """
    调用 Java createOrder 接口正式下单
    POST /hotel/order/createOrder
    """
    if not all([hotel_id, room_type_id, room_no, checkin, checkout]):
        return None

    try:
        url = f"{settings.JAVA_API_BASE_URL}/hotel/order/createOrder"
        payload = {
            "userId": int(user_id) if user_id else 1,
            "hotelId": int(hotel_id),
            "roomTypeId": int(room_type_id),
            "roomNo": room_no,
            "checkInDate": checkin + "T00:00:00",
            "checkOutDate": checkout + "T00:00:00",
            "guestName": "用户",  # TODO: 前端传入真实姓名
            "guestPhone": "00000000000",  # TODO: 前端传入真实电话
        }
        response = requests.post(url, json=payload, timeout=10)
        if response.status_code == 200:
            result = response.json()
            # 尝试从 Result 包装中取 data
            if result.get("data"):
                return result["data"]
            return result
        else:
            logger.warning(f"[CreateOrder] 接口异常: status={response.status_code}")
            return None
    except requests.exceptions.Timeout:
        logger.warning("[CreateOrder] 接口超时")
        return None
    except Exception as e:
        logger.warning(f"[CreateOrder] 接口调用失败: {e}")
        return None


def _build_room_unavailable_response(state: AgentState, hotel_name: str) -> dict:
    """
    房间不可用：从 researcher 返回的酒店列表中获取替代方案
    若有其他可选房间 → waiting_user_alternatives
    若无 → waiting_user_alarm
    """
    from app.agents.user_interaction_node import handle_hotel_alternatives, handle_hotel_alarm_decision
    interaction = state.get("interaction") or {}
    hotels = interaction.get("hotels") or []
    chosen = interaction.get("chosen") or {}

    # 过滤掉已被预订的房间
    available_hotels = []
    for h in hotels:
        h_room_no = h.get("roomNo") or h.get("room_no", "")
        chosen_room_no = chosen.get("roomNo") or chosen.get("room_no", "")
        if h_room_no != chosen_room_no:
            available_hotels.append(h)

    if available_hotels:
        logger.info(f"[ConfirmAndBook] 有 {len(available_hotels)} 个替代房间")
        return handle_hotel_alternatives(
            state=state,
            alternatives=available_hotels,
            original_hotel=hotel_name,
            message="您选择的房间已被预订，请从以下可用房间中选择：",
        )
    else:
        logger.info("[ConfirmAndBook] 无替代房间，询问是否设置闹钟")
        result = handle_hotel_alarm_decision(
            state=state,
            hotel_name=hotel_name,
            message="很遗憾，该房间已被预订，也暂无其他空房。",
        )
        # 补充闹钟所需信息
        result["interaction"]["hotel_id"] = chosen.get("hotelId") or chosen.get("hotel_id") or ""
        result["interaction"]["checkin"] = chosen.get("checkInDate") or chosen.get("check_in_date") or ""
        result["interaction"]["checkout"] = chosen.get("checkOutDate") or chosen.get("check_out_date") or ""
        return result
