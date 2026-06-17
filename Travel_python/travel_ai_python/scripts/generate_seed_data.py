import json
import os
import sys
import time
from typing import Optional

# 把项目根目录加到 path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from dotenv import load_dotenv
load_dotenv()

from openai import OpenAI

# ==================== 配置 ====================
LLM_BASE_URL = os.getenv("LOCAL_AI_MODEL_API_URL", "")
LLM_API_KEY = os.getenv("LOCAL_AI_MODEL_API_KEY", "")
LLM_MODEL = os.getenv("LOCAL_MODEL_NAME", "MiniMax-M3")

client = OpenAI(base_url=LLM_BASE_URL, api_key=LLM_API_KEY)

# 覆盖国内主要旅游城市
CITIES = [
  "北京", "上海", "广州", "深圳", "成都", "杭州", "重庆", "武汉",
  "西安", "南京", "长沙", "苏州", "天津", "郑州",
  "厦门", "三亚", "丽江", "大理", "桂林", "张家界", "黄山",
  "青岛", "大连", "哈尔滨", "拉萨", "昆明", "贵阳", "海口",
  "乌鲁木齐", "呼伦贝尔", "西双版纳", "敦煌", "洛阳", "开封",
]

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "data")
OUTPUT_FILE = os.path.join(OUTPUT_DIR, "travel_knowledge_v2.json")


def call_llm(prompt: str, max_retries: int = 3) -> Optional[str]:
  for attempt in range(max_retries):
      try:
          resp = client.chat.completions.create(
              model=LLM_MODEL,
              messages=[{"role": "user", "content": prompt}],
              temperature=0.3,
              max_tokens=8192,
          )
          return resp.choices[0].message.content
      except Exception as e:
          print(f"  ⚠️ 第{attempt+1}次调用失败: {e}")
          if attempt < max_retries - 1:
              time.sleep(3)
  return None


def parse_json(text: str):
  """从LLM回复中提取JSON"""
  text = text.strip()
  if text.startswith("```"):
      lines = text.split("\n")
      text = "\n".join(lines[1:])
      if text.endswith("```"):
          text = text[:-3]
  try:
      return json.loads(text)
  except json.JSONDecodeError:
      # 尝试找到 JSON 的起止位置
      if text.startswith("["):
          end = text.rfind("]")
      else:
          end = text.rfind("}")
      if end != -1:
          try:
              return json.loads(text[:end+1])
          except:
              pass
      print(f"  ❌ JSON解析失败: {text[:200]}...")
      return None


# ==================== 1. 生成景点数据 ====================
def generate_attractions(city: str, count: int = 8) -> list:
  print(f"  🏛️  生成 {city} 景点...")

  prompt = f"""你是资深旅游专家。为{city}生成{count}个核心景点信息。
必须真实存在，覆盖不同类型（历史文化、自然风光、美食、现代地标、亲子、小众）。

严格输出JSON数组（不要markdown代码块）：
[
{{
  "name": "景点名",
  "city": "{city}",
  "category": "历史文化/自然风光/美食体验/现代地标/亲子乐园/小众秘境",
  "history": "历史背景，2-3句话",
  "highlights": "3-5个亮点，顿号分隔",
  "tips": "游览贴士：最佳时间、门票参考价、预约方式、注意事项",
  "suitable_for": "适合人群",
  "duration": "建议游览时长，如2-3小时",
  "ticket_price": "门票参考价",
  "opening_hours": "开放时间",
  "transportation": "交通方式"
}}
]"""

  content = call_llm(prompt)
  if not content:
      return []
  return parse_json(content) or []


# ==================== 2. 生成经典路线 ====================
def generate_routes(city: str) -> list:
  print(f"  🗺️  生成 {city} 路线...")

  configs = [(3, "紧凑打卡"), (5, "深度体验"), (7, "慢游全览")]
  routes = []

  for days, style in configs:
      prompt = f"""你是资深行程规划师。为{city}设计{days}天{style}经典行程。
每天3-5个活动，景点顺序逻辑顺畅，标注交通和用餐建议。

严格输出JSON对象：
{{
"title": "{city}{days}日{style}经典游",
"destination": "{city}",
"days": {days},
"style": "{style}",
"tags": ["标签1", "标签2"],
"daily_plan": [
  {{
    "day": 1,
    "theme": "当日主题",
    "activities": ["活动1", "活动2", "活动3"],
    "location": "主要活动区域",
    "transportation": "建议交通",
    "meals": ["午餐建议", "晚餐建议"],
    "tips": "当日注意事项"
  }}
]
}}"""

      content = call_llm(prompt)
      if content:
          route = parse_json(content)
          if route:
              all_acts = []
              for d in route.get("daily_plan", []):
                  all_acts.extend(d.get("activities", []))
              route["embedding_text"] = (
                  f"{city}{days}日游{style}风格，"
                  f"途经{'、'.join(all_acts[:10])}"
              )
              routes.append(route)

  return routes


# ==================== 3. 生成模拟用户行程 ====================
def generate_user_plans(city: str, count: int = 3) -> list:
  print(f"  👤  生成 {city} 用户行程...")

  styles = [
      ("亲子家庭", "带孩子，节奏慢，有教育意义"),
      ("情侣蜜月", "浪漫，拍照好看，美食"),
      ("穷游背包客", "省钱，青旅，公共交通，免费景点"),
  ]

  plans = []
  import random
  for style, desc in styles[:count]:
      days_val = random.choice([3, 4, 5])

      prompt = f"""你是真实旅行者。以第一人称写{city}{days_val}日游分享。
用户画像：{style}（{desc}）

严格输出JSON：
{{
"destination": "{city}",
"days": {days_val},
"style": "{style}",
"source": "模拟用户分享",
"daily_plan": [
  {{
    "day": 1,
    "theme": "当日主题",
    "activities": ["活动1", "活动2"],
    "location": "主要区域",
    "cost_estimate": 300
  }}
],
"total_cost": 3000,
"highlights_summary": "最精彩的3个点",
"regrets": "改进之处"
}}"""

      content = call_llm(prompt)
      if content:
          plan = parse_json(content)
          if plan:
              all_acts = []
              for d in plan.get("daily_plan", []):
                  all_acts.extend(d.get("activities", []))
              plan["embedding_text"] = (
                  f"{city}{days_val}日{style}游，"
                  f"包含{'、'.join(all_acts[:8])}"
              )
              plans.append(plan)

  return plans


# ==================== 主流程 ====================
def main():
  os.makedirs(OUTPUT_DIR, exist_ok=True)

  all_data = []
  data_id = 10000

  print("=" * 60)
  print(f"🏗️  Travel-AI 种子数据生成 | 模型: {LLM_MODEL} | 城市: {len(CITIES)}个")
  print("=" * 60)

  for i, city in enumerate(CITIES):
      print(f"\n[{i+1}/{len(CITIES)}] {city}")

      # 景点
      for attr in generate_attractions(city, count=8):
          data_id += 1
          attr["type"] = "attraction"
          attr["id"] = str(data_id)
          attr["embedding_text"] = (
              f"{attr['name']}位于{attr['city']}，"
              f"{attr.get('history','')[:100]}，"
              f"亮点：{attr.get('highlights','')}"
          )
          all_data.append(attr)

      # 路线
      for route in generate_routes(city):
          data_id += 1
          route["type"] = "classic_route"
          route["id"] = str(data_id)
          all_data.append(route)

      # 用户行程
      for plan in generate_user_plans(city):
          data_id += 1
          plan["type"] = "user_plan"
          plan["id"] = str(data_id)
          all_data.append(plan)

      time.sleep(0.5)  # 防限流

  # 保存
  with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
      json.dump(all_data, f, ensure_ascii=False, indent=2)

  print("\n" + "=" * 60)
  print(f"✅ 完成！共 {len(all_data)} 条")
  print(f"   景点: {sum(1 for d in all_data if d['type']=='attraction')} 条")
  print(f"   路线: {sum(1 for d in all_data if d['type']=='classic_route')} 条")
  print(f"   用户行程: {sum(1 for d in all_data if d['type']=='user_plan')} 条")
  print(f"   输出: {OUTPUT_FILE}")
  print(f"\n下一步: 修改 init_qdrant_rag.py 中的 JSON_PATH 指向此文件后运行")
  print("=" * 60)


if __name__ == "__main__":
  main()