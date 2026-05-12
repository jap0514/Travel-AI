# app/utils/json_utils.py
import json
from typing import Any

def to_compact_json(obj: Any) -> str:
    """生成紧凑 JSON（无多余空格），与 Java fastjson2 默认行为一致"""
    return json.dumps(obj, separators=(',', ':'), ensure_ascii=False)

def parse_json(s: str) -> Any:
    """安全解析 JSON"""
    return json.loads(s)