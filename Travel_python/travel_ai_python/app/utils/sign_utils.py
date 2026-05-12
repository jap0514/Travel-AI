# app/utils/sign_utils.py
import hmac
import hashlib
from app.utils.json_utils import to_compact_json

def generate_sign(trace_id: str, timestamp: int, body_dict: dict, secret: str) -> str:
    """生成签名：traceId + timestamp + compact_json(body) + secret"""
    body_json = to_compact_json(body_dict)
    sign_content = f"{trace_id}{timestamp}{body_json}{secret}".encode("utf-8")
    sign = hmac.new(secret.encode("utf-8"), sign_content, hashlib.sha256).hexdigest()
    return sign

def verify_sign(message_dict: dict, secret: str) -> bool:
    """验证完整消息的签名"""
    try:
        header = message_dict.get("header", {})
        body = message_dict.get("body", {})
        trace_id = header.get("traceId")
        timestamp = header.get("timestamp")
        expect_sign = header.get("sign")
        if not all([trace_id, timestamp, expect_sign]):
            return False
        actual_sign = generate_sign(trace_id, timestamp, body, secret)
        return hmac.compare_digest(actual_sign, expect_sign)
    except Exception:
        return False