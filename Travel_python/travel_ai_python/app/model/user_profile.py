from pydantic import BaseModel

class UserProfile(BaseModel):
    user_id: int
    preferred_destinations: list[str] = []   # 常去目的地
    preferred_paces: list[str] = []          # 节奏偏好 ["暴走", "适中", "休闲"]
    preferred_budgets: list[str] = []       # 预算偏好
    dietary_restrictions: list[str] = []    # 饮食禁忌
    preferred_foods: list[str] = []         # 饮食偏好
    accommodation_preference: str = ""       # 住宿偏好
    travel_style: list[str] = []            # 旅行风格 ["文化游", "美食游", "亲子"]
    last_updated: str = ""                   # ISO 时间戳