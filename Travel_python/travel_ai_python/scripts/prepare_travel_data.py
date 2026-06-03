import json
import os

# 确保数据目录存在
os.makedirs("data", exist_ok=True)

data = []

# ----- 1. 经典行程模板 -----
data.append({
    "type": "classic_route",
    "id": "1001",
    "destination": "北京",
    "days": 3,
    "title": "北京经典三日游",
    "content": "第一天：天安门广场→故宫→景山公园。第二天：八达岭长城→奥林匹克公园（鸟巢水立方）。第三天：颐和园→天坛→前门大街。",
    "tags": ["经典", "首次", "历史文化"],
    "embedding_text": "北京3日游经典行程，第一天参观天安门广场、故宫、景山，第二天爬八达岭长城和奥林匹克公园，第三天游览颐和园、天坛和前门大街。适合第一次来北京的游客。"
})

data.append({
    "type": "classic_route",
    "id": "1002",
    "destination": "西安",
    "days": 4,
    "title": "西安历史深度四日游",
    "content": "第一天：陕西历史博物馆→大雁塔。第二天：兵马俑→华清宫。第三天：西安古城墙骑行→回民街。第四天：碑林博物馆→钟鼓楼。",
    "tags": ["历史", "亲子", "研学"],
    "embedding_text": "西安4日游，侧重历史研学，包含陕历博、兵马俑、华清宫、古城墙、碑林等，适合家庭亲子游。"
})

# ----- 2. 景点知识（历史背景、亮点、贴士）-----
data.append({
    "type": "attraction",
    "id": "1003",
    "name": "故宫博物院",
    "city": "北京",
    "history": "始建于1406年，1420年建成，是明清两代的皇家宫殿，距今600余年。",
    "highlights": "太和殿、金水桥、九龙壁、珍宝馆、钟表馆。",
    "tips": "必须提前7天在官网预约门票；周一闭馆；建议上午8:30开门就进去，人少体验好。",
    "embedding_text": "故宫位于北京中轴线，是世界现存规模最大、最完整的木质结构古建筑群，被誉为世界五大宫之首。"
})

data.append({
    "type": "attraction",
    "id": "1004",
    "name": "秦始皇兵马俑",
    "city": "西安",
    "history": "建于公元前246年至208年，是秦始皇陵的陪葬坑，被誉为世界第八大奇迹。",
    "highlights": "一号坑（规模最大）、二号坑（多兵种）、三号坑（指挥部）、铜车马。",
    "tips": "建议请讲解员或租讲解器；参观时间约3小时；下午游客较少。",
    "embedding_text": "兵马俑是秦始皇陵的陪葬坑，出土了数以千计的真实比例陶俑，展示了秦朝强大的军事力量。"
})

# ----- 3. 历史用户行程 -----
data.append({
    "type": "user_plan",
    "id": "1005",
    "destination": "西安",
    "preferences": "亲子历史",
    "content": "第一天：中午抵达，入住回民街附近，下午逛回民街吃小吃。第二天：上午兵马俑，下午华清宫看《长恨歌》演出。第三天：陕西历史博物馆（提前预约），晚上大雁塔北广场音乐喷泉。第四天：古城墙骑行，碑林博物馆，返程。",
    "source": "马蜂窝游记",
    "embedding_text": "西安4日亲子历史游，包含兵马俑、华清宫、陕历博、古城墙，适合带孩子的家庭，节奏轻松。"
})

# 保存到 JSON 文件
with open("data/travel_knowledge.json", "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print("✅ 数据准备完成：data/travel_knowledge.json")