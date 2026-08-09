# -*- coding: utf-8 -*-
"""
User Interaction Node - 统一交互入口

根据 interaction.status 调用对应 handler，处理酒店选择、无替代时的决策、有替代时的选择、闹钟设置等交互流程。
"""
from app.agents.base import llm
from app.agents.state import AgentState
from app.config.logger import logger
from langchain_core.messages import HumanMessage, AIMessage
import re


# ==================== 工具函数：构建 Prompt ====================

def build_hotel_selection_prompt(hotels: list, message: str = "") -> str:
    """构建酒店选择提示"""
    hotel_lines = []
    for i, h in enumerate(hotels, 1):
        name = h.get("name", "未知酒店")
        address = h.get("address", "")
        price = h.get("price", h.get("price_per_night", "未知"))
        rating = h.get("rating", "")
        hotel_lines.append(f"{i}. **{name}**")
        if address:
            hotel_lines.append(f"   地址：{address}")
        if price:
            hotel_lines.append(f"   价格：¥{price}/晚")
        if rating:
            hotel_lines.append(f"   评分：{rating}")
        hotel_lines.append("")

    hotel_list_str = "\n".join(hotel_lines)
    prompt = f"""以下是为您查询到的酒店列表，请选择您心仪的酒店：

{hotel_list_str}
{message}

请回复您想选择的酒店序号或名称。"""
    return prompt


def build_no_hotel_prompt(message: str = "") -> str:
    """构建无房时的提示（询问用户是否继续规划或取消）"""
    prompt = f"""非常抱歉，您选择的所有酒店目前都没有空房了。

{message}

请问您希望：
1. 继续规划（不包含酒店）
2. 重新生成酒店列表
3. 取消本次规划
"""
    return prompt


def build_alternatives_prompt(alternatives: list, original_hotel: str, message: str = "") -> str:
    """构建替代酒店选择提示"""
    alt_lines = []
    for i, h in enumerate(alternatives, 1):
        name = h.get("name", "未知酒店")
        address = h.get("address", "")
        price = h.get("price", h.get("price_per_night", "未知"))
        alt_lines.append(f"{i}. **{name}**")
        if address:
            alt_lines.append(f"   地址：{address}")
        if price:
            alt_lines.append(f"   价格：¥{price}/晚")
        alt_lines.append("")

    alt_list_str = "\n".join(alt_lines)
    prompt = f"""您之前选择的 [{original_hotel}] 已经没有空房了。
为您找到了以下替代酒店：

{alt_list_str}
{message}

请选择您心仪的替代酒店，或回复"不需要酒店"继续规划。"""
    return prompt


def build_alarm_prompt(hotel_name: str, message: str = "") -> str:
    """构建闹钟设置提示"""
    prompt = f"""非常抱歉，[{hotel_name}] 已经没有空房，也没有找到合适的替代酒店。

{message}

您可以设置有空房提醒，我们会在酒店有新空房时通知您。
请告诉我您希望设置提醒的时间和日期（例如：明天下午3点），或者直接回复"不需要提醒"。"""
    return prompt


# ==================== 工具函数：解析用户输入 ====================

def parse_hotel_choice(user_input: str, hotels: list) -> dict | None:
    """
    解析用户输入，返回选中的酒店对象
    支持：序号（1/2/3）、酒店名称、模糊匹配
    返回：酒店对象 或 None（无法识别）
    """
    if not user_input or not hotels:
        return None

    user_input = user_input.strip()

    # 尝试序号匹配
    if user_input.isdigit():
        idx = int(user_input) - 1
        if 0 <= idx < len(hotels):
            return hotels[idx]

    # 尝试精确名称匹配
    for h in hotels:
        name = h.get("hotelName") or h.get("hotel_name") or h.get("name", "")
        if name == user_input:
            return h

    # 尝试模糊匹配（用户输入包含在酒店名中）
    for h in hotels:
        name = h.get("hotelName") or h.get("hotel_name") or h.get("name", "")
        if user_input in name or name in user_input:
            return h

    # 无法识别
    return None


def parse_guest_contact(user_input: str) -> tuple[str, str] | None:
    """
    从用户文本中解析入住人姓名 + 手机号
    支持：
      "张三 13800138000"
      "姓名：张三，手机号：13800138000"
      "张三，13800138000"
      "入住人张三 13800138000"
    手机号正则与 Java HotelBookingDTO.@Pattern("^1[3-9]\\d{9}$") 保持一致
    返回: (name, phone) 或 None
    """
    import re

    text = (user_input or "").strip()
    if not text:
        return None

    phone_match = re.search(r"(?<!\d)(1[3-9]\d{9})(?!\d)", text)
    if not phone_match:
        return None
    phone = phone_match.group(1)

    # 姓名优先取「姓名/入住人/联系人:」前缀；否则取手机号前的部分去掉标点
    name_match = re.search(
        r"(?:姓名|入住人|联系人)\s*[:：]?\s*([一-龥A-Za-z·]{2,20})",
        text,
    )
    if name_match:
        name = name_match.group(1)
    else:
        before = text[: phone_match.start()].strip(" ，,。.：:;；、")
        before = re.sub(
            r"^(?:姓名|入住人|联系人)\s*[:：]?\s*",
            "",
            before,
        )
        name = before.strip()

    if not re.fullmatch(r"[一-龥A-Za-z·]{2,20}", name):
        return None

    return name, phone


# ==================== Handler：处理各类交互状态 ====================

def handle_hotel_selection(state: AgentState, hotels: list, message: str = "") -> dict:
    """
    处理 waiting_user_hotel 状态
    场景：researcher 返回了酒店列表，等待用户选择
    """
    interaction = state.get("interaction") or {}
    prompt = build_hotel_selection_prompt(hotels, message)
    return {
        "interaction": {
            **interaction,
            "type": "hotel_select",
            "status": "waiting_user_hotel",
            "hotels": hotels,
            "message": prompt,
            "interaction_interrupted": True,
        }
    }


def handle_no_hotel_decision(state: AgentState, message: str = "") -> dict:
    """
    处理 waiting_user_decision 状态
    场景：researcher 发现全部没房，询问用户是继续规划还是取消
    """
    interaction = state.get("interaction") or {}
    prompt = build_no_hotel_prompt(message)
    return {
        "interaction": {
            **interaction,
            "type": "no_hotel_decision",
            "status": "waiting_user_decision",
            "message": prompt,
            "interaction_interrupted": True,
        }
    }


def handle_hotel_alternatives(state: AgentState, alternatives: list, original_hotel: str, message: str = "") -> dict:
    """
    处理 waiting_user_alternatives 状态
    场景：confirm 时原酒店没房，有替代酒店
    """
    interaction = state.get("interaction") or {}
    prompt = build_alternatives_prompt(alternatives, original_hotel, message)
    return {
        "interaction": {
            **interaction,
            "type": "hotel_alternatives",
            "status": "waiting_user_alternatives",
            "hotels": alternatives,
            "original_hotel": original_hotel,
            "message": prompt,
            "interaction_interrupted": True,
        }
    }


def handle_hotel_alarm_decision(state: AgentState, hotel_name: str, message: str = "") -> dict:
    """
    处理 waiting_user_alarm 状态
    场景：confirm 时原酒店没房，无替代酒店，询问是否设闹钟
    """
    interaction = state.get("interaction") or {}
    prompt = build_alarm_prompt(hotel_name, message)
    return {
        "interaction": {
            **interaction,
            "type": "hotel_alarm",
            "status": "waiting_user_alarm",
            "hotel_name": hotel_name,
            "message": prompt,
            "interaction_interrupted": True,
        }
    }


def handle_guest_contact(state: AgentState, hotel_name: str, message: str = "") -> dict:
    """
    处理 waiting_user_contact 状态
    场景：confirm 时房间可预约，等待用户填写入住人姓名和手机号
    """
    interaction = state.get("interaction") or {}
    default_msg = f"「{hotel_name}」当前有房，请填写入住人姓名和手机号后预订。"
    return {
        "interaction": {
            **interaction,
            "type": "guest_contact",
            "status": "waiting_user_contact",
            "hotel_name": hotel_name,
            "message": message or default_msg,
            "interaction_interrupted": True,
        },
        "booking_status": "waiting_contact",
    }


# ==================== 主入口 ====================

def user_interaction_node(state: AgentState) -> dict:
    """
    User Interaction Node - 统一交互入口

    根据 interaction.status 调用对应 handler。
    各 handler 负责更新 interaction 字段，返回给 supervisor 继续路由。
    """
    interaction = state.get("interaction") or {}
    status = interaction.get("status", "")
    messages = state.get("messages") or []
    user_msg = messages[-1].content if messages else ""

    logger.info(f"[UserInteraction] status={status}, user_msg={user_msg[:50]}...")
    hotels = interaction.get("hotels", [])
    logger.info(f"[UserInteraction] hotels count: {len(hotels)}, hotels type: {type(hotels)}, first hotel: {hotels[0] if hotels else None}")

    # 如果处于 waiting_ 状态，但最新消息不是用户输入（说明是同一轮 AI 回复，没有有效用户输入）
    # 设置 interaction_interrupted=True，supervisor 会跳过，继续等待外部输入
    if status and status.startswith("waiting_") and messages:
        last_msg = messages[-1]
        if not isinstance(last_msg, HumanMessage):
            logger.info(f"[UserInteraction] 无有效用户输入，设置 interaction_interrupted=True")
            return {
                "interaction": {
                    **interaction,
                    "interaction_interrupted": True,
                }
            }

    # 解析用户回复中的酒店选择
    if status == "waiting_user_hotel" and user_msg:
        logger.info(f"[UserInteraction] 进入解析分支，user_msg='{user_msg}', hotels length={len(hotels)}")
        chosen = parse_hotel_choice(user_msg, hotels)
        logger.info(f"[UserInteraction] parse_hotel_choice result: {chosen}")
        if chosen:
            hotel_name = chosen.get("hotelName") or chosen.get("hotel_name") or chosen.get("name")
            logger.info(f"[UserInteraction] 用户选择酒店: {hotel_name}")
            return {
                "interaction": {
                    **interaction,
                    "status": None,
                    "interaction_interrupted": False,
                    "chosen": chosen,
                }
            }

        else:
            # 无法识别，返回提示让用户重新选择
            prompt = build_hotel_selection_prompt(
                hotels,
                "⚠️ 无法识别您的选择，请回复酒店序号或名称。"
            )
            return {
                "interaction": {
                    **interaction,
                    "interaction_interrupted": True,
                    "status": "waiting_user_hotel",
                    "message": prompt,
                }
            }

    # waiting_user_decision：用户选择继续/重新生成/取消
    if status == "waiting_user_decision" and user_msg:
        if "继续" in user_msg or "不包含酒店" in user_msg:
            logger.info("[UserInteraction] 用户选择继续规划（不包含酒店）")
            return {
                "interaction": {
                    **interaction,
                    "status": None,
                    "interaction_interrupted": False,
                    "chosen": None,
                }
            }
        elif "重新" in user_msg or "再试" in user_msg:
            logger.info("[UserInteraction] 用户选择重新生成酒店列表")
            return {
                "interaction": {
                    **interaction,
                    "status": None,
                    "interaction_interrupted": False,
                    "needs_replan": True,
                }
            }
        elif "取消" in user_msg:
            logger.info("[UserInteraction] 用户选择取消")
            return {
                "should_end": True,
                "interaction_interrupted": False,
            }
        else:
            prompt = build_no_hotel_prompt("⚠️ 无法识别您的选择，请回复：继续/重新生成/取消")
            return {
                "interaction": {
                    **interaction,
                    "interaction_interrupted": True,
                    "message": prompt,
                }
            }

    # waiting_user_alternatives：用户选择替代酒店或不要酒店
    if status == "waiting_user_alternatives" and user_msg:
        hotels = interaction.get("hotels", [])
        if "不需要酒店" in user_msg or "不要酒店" in user_msg:
            logger.info("[UserInteraction] 用户选择不要酒店")
            return {
                "interaction": {
                    **interaction,
                    "status": None,
                    "interaction_interrupted": False,
                    "chosen": None,
                }
            }
        chosen = parse_hotel_choice(user_msg, hotels)
        if chosen:
            logger.info(f"[UserInteraction] 用户选择替代酒店: {chosen.get('hotelName') or chosen.get('name')}")
            return {
                "interaction": {
                    **interaction,
                    "status": None,
                    "interaction_interrupted": False,
                    "chosen": chosen,
                }
            }
        else:
            prompt = build_alternatives_prompt(
                hotels,
                interaction.get("original_hotel", ""),
                "⚠️ 无法识别您的选择，请回复酒店序号或名称。"
            )
            return {
                "interaction": {
                    **interaction,
                    "interaction_interrupted": True,
                    "message": prompt,
                }
            }

    # waiting_user_contact：用户提交入住人姓名 + 手机号
    if status == "waiting_user_contact" and user_msg:
        parsed = parse_guest_contact(user_msg)
        if not parsed:
            logger.info("[UserInteraction] 入住人信息格式无法识别")
            prompt = (interaction.get("message")
                      or "⚠️ 信息格式不正确，请重新填写。示例：张三 13800138000")
            return {
                "interaction": {
                    **interaction,
                    "status": "waiting_user_contact",
                    "interaction_interrupted": True,
                    "message": prompt,
                }
            }
        name, phone = parsed
        logger.info(f"[UserInteraction] 收到入住人信息: name={name}, phone={phone}")
        return {
            "interaction": {
                **interaction,
                "status": None,
                "interaction_interrupted": False,
                "contact": {"guestName": name, "guestPhone": phone},
            },
            "booking_status": "ready_to_book",
        }

    # waiting_user_alarm：用户设置闹钟时间或拒绝
    if status == "waiting_user_alarm" and user_msg:
        if "不需要" in user_msg or "不提醒" in user_msg or "算了" in user_msg:
            logger.info("[UserInteraction] 用户拒绝设置闹钟")
            return {
                "interaction": {
                    **interaction,
                    "status": None,
                    "interaction_interrupted": False,
                    "chosen": None,  # 关键：清 chosen 让 supervisor 规则7 不再触发
                    "message": "好的，本次不预订酒店，也不设置提醒。",
                },
                "booking_status": "declined",
            }
        # 尝试从用户消息中提取日期时间
        alarm_time = _parse_alarm_time(user_msg)
        if alarm_time:
            logger.info(f"[UserInteraction] 用户设置闹钟: {alarm_time}")
            return {
                "interaction": {
                    **interaction,
                    "status": None,
                    "interaction_interrupted": False,
                    "chosen": None,  # 设完闹钟同样不应再下单
                    "alarm_set": {
                        "hotel_name": interaction.get("hotel_name", ""),
                        "hotel_id": interaction.get("hotel_id", ""),
                        "checkin": interaction.get("checkin", ""),
                        "checkout": interaction.get("checkout", ""),
                        "alarm_time": alarm_time,
                        "user_message": user_msg,
                    },
                },
                "booking_status": "alarm_set",
            }
        else:
            prompt = build_alarm_prompt(
                interaction.get("hotel_name", ""),
                "⚠️ 无法识别时间，请回复您希望提醒的日期和时间，例如：明天下午3点。"
            )
            return {
                "interaction": {
                    **interaction,
                    "interaction_interrupted": True,
                    "message": prompt,
                }
            }

    # 未知状态：记录警告，返回原 state
    logger.warning(f"[UserInteraction] 未知 interaction.status: {status}，跳过处理")
    return {}


def _parse_alarm_time(user_input: str) -> str | None:
    """
    从用户输入中解析闹钟时间，返回 YYYY-MM-DD HH:MM 格式
    支持：明天下午3点、后天早上9点、7月25号15:30 等
    若无法精确解析，返回 None
    """
    from datetime import datetime, timedelta

    now = datetime.now()
    user_input = user_input.strip()

    # 精确格式：YYYY-MM-DD HH:MM
    match = re.search(r"(\d{4}-\d{2}-\d{2})\s*(\d{1,2}:\d{2})", user_input)
    if match:
        return f"{match.group(1)} {match.group(2)}"

    # 相对时间：明天/后天 + 时间
    days_offset = 0
    if "明天" in user_input:
        days_offset = 1
    elif "后天" in user_input:
        days_offset = 2

    time_match = re.search(r"早上|上午|下午|晚上|凌晨|中午|傍晚|夜里|中午|早|上午|下午|晚|夜", user_input)
    hour_match = re.search(r"(\d{1,2})[点时]", user_input)
    minute_match = re.search(r"(\d{1,2})分", user_input)

    if days_offset > 0 or time_match or hour_match:
        target = now + timedelta(days=days_offset)

        hour = 12
        minute = 0
        if "早上" in user_input or "上午" in user_input or "早" in user_input:
            hour = 9
        elif "下午" in user_input or "下午" in user_input:
            hour = 15
        elif "晚上" in user_input or "晚" in user_input or "夜" in user_input:
            hour = 20
        elif "中午" in user_input or "午" in user_input:
            hour = 12
        elif "凌晨" in user_input or "夜里" in user_input:
            hour = 3

        if hour_match:
            hour = int(hour_match.group(1))
            hour = max(0, min(23, hour))
        if minute_match:
            minute = int(minute_match.group(1))

        target = target.replace(hour=hour, minute=minute, second=0)
        return target.strftime("%Y-%m-%d %H:%M")

    # 带具体日期：7月25号 / 7-25
    date_match = re.search(r"(\d{1,2})[月\-](\d{1,2})", user_input)
    if date_match:
        month = int(date_match.group(1))
        day = int(date_match.group(2))
        try:
            target = now.replace(month=month, day=day)
            if target < now:
                target = target.replace(year=target.year + 1)
            time_match2 = re.search(r"(\d{1,2})[点时]", user_input)
            if time_match2:
                target = target.replace(hour=int(time_match2.group(1)), minute=0, second=0)
            else:
                target = target.replace(hour=12, minute=0, second=0)
            return target.strftime("%Y-%m-%d %H:%M")
        except ValueError:
            pass

    return None
