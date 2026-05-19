from dataclasses import dataclass, asdict, field
from datetime import datetime
from typing import Any

@dataclass
class ChatMessage:
    msg_id: int
    session_id: int
    user_id: int
    role: Any
    content: str
    plan_json: Any
    create_time: datetime = field(default_factory=datetime.now)

    def to_dict(self):
        data = asdict(self)
        # 如果 create_time 需要序列化为字符串，可以在这里转换
        data['create_time'] = self.create_time.isoformat()
        return data

    @classmethod
    def from_dict(cls, data: dict):
        # 将 create_time 从字符串解析回 datetime
        if 'create_time' in data and isinstance(data['create_time'], str):
            data['create_time'] = datetime.fromisoformat(data['create_time'])
        return cls(**data)