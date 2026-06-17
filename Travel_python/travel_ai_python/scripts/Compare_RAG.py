import json
import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from dotenv import load_dotenv
load_dotenv()

from openai import OpenAI
from sentence_transformers import SentenceTransformer
from qdrant_client import QdrantClient
from qdrant_client.models import Filter, FieldCondition, MatchValue

# ==================== 配置 ====================
LLM_BASE_URL = os.getenv("LOCAL_AI_MODEL_API_URL", "")
LLM_API_KEY = os.getenv("LOCAL_AI_MODEL_API_KEY", "")
LLM_MODEL = os.getenv("LOCAL_MODEL_NAME", "deepseek-chat")
QDRANT_HOST = os.getenv("QDRANT_HOST", "192.168.71.140")
QDRANT_PORT = int(os.getenv("QDRANT_PORT", 6333))
EMBEDDING_MODEL = "paraphrase-multilingual-MiniLM-L12-v2"

client_llm = OpenAI(base_url=LLM_BASE_URL, api_key=LLM_API_KEY)
embedding_model = SentenceTransformer(EMBEDDING_MODEL)
qdrant = QdrantClient(host=QDRANT_HOST, port=QDRANT_PORT)


# ==================== 测试用例 ====================
# 每个测试用例：原始查询 + 期望命中的关键词（用于评估命中率）
TEST_CASES = [
  {
      "query": "北京有什么好玩的",
      "expected_keywords": ["故宫", "长城", "颐和园", "天坛"],
      "expected_city": "北京",
      "min_expected_hits": 2,
  },
  {
      "query": "故宫好不好玩",
      "expected_keywords": ["故宫", "明清", "皇家", "紫禁城"],
      "expected_city": "北京",
      "min_expected_hits": 1,
  },
  {
      "query": "带娃去哪玩比较好",
      "expected_keywords": ["亲子", "科技馆", "动物园", "海洋馆"],
      "expected_city": "",
      "min_expected_hits": 1,
  },
  {
      "query": "西安3天怎么安排",
      "expected_keywords": ["兵马俑", "古城墙", "陕西历史博物馆", "回民街"],
      "expected_city": "西安",
      "min_expected_hits": 2,
  },
  {
      "query": "便宜又好玩的地方",
      "expected_keywords": ["免费", "经济", "性价比"],
      "expected_city": "",
      "min_expected_hits": 1,
  },
  {
      "query": "杭州西湖值得去吗",
      "expected_keywords": ["西湖", "苏堤", "雷峰塔"],
      "expected_city": "杭州",
      "min_expected_hits": 1,
  },
  {
      "query": "三亚几天合适",
      "expected_keywords": ["三亚", "海滩", "亚龙湾", "天涯海角"],
      "expected_city": "三亚",
      "min_expected_hits": 2,
  },
  {
      "query": "成都必吃美食",
      "expected_keywords": ["火锅", "小吃", "川菜", "锦里"],
      "expected_city": "成都",
      "min_expected_hits": 1,
  },
  {
      "query": "厦门3日游攻略",
      "expected_keywords": ["鼓浪屿", "厦门大学", "曾厝垵"],
      "expected_city": "厦门",
      "min_expected_hits": 2,
  },
  {
      "query": "丽江古城怎么样",
      "expected_keywords": ["丽江", "古城", "纳西", "玉龙雪山"],
      "expected_city": "丽江",
      "min_expected_hits": 1,
  },
]


# ==================== 改写前：直接用原查询检索 ====================
def search_before_rewrite(query: str, city: str = "", limit: int = 5) -> list:
  """改写前：原始查询直接做向量检索"""
  vector = embedding_model.encode(query).tolist()
  qfilter = None
  if city:
      qfilter = Filter(must=[FieldCondition(key="city", match=MatchValue(value=city))])

  # 检索所有3个 collection
  all_results = []
  for coll in ["attractions", "classic_routes", "user_plans"]:
      try:
          res = qdrant.query_points(
              collection_name=coll,
              query=vector,
              query_filter=qfilter,
              limit=limit,
              with_payload=True,
          )
          for hit in res.points:
              all_results.append({
                  "collection": coll,
                  "score": hit.score,  # 相似度分数
                  "content": hit.payload.get("content", "")
              })
      except Exception as e:
          pass

  all_results.sort(key=lambda x: x["score"], reverse=True)
  return all_results[:limit]


# ==================== 改写后：查询改写 + 多路检索 ====================
REWRITE_PROMPT = """你是旅行搜索优化专家。将用户的口语化问题改写为精确的检索关键词。

改写规则：
1. 去掉口语词（"吗""呢""啊""好不好""怎么样"）
2. 扩展核心词的同义词（"好玩"→"景点 评价 亮点 推荐"）
3. 补充隐含信息（"带娃"→"亲子 适合儿童 家庭友好"，"便宜"→"免费 经济 性价比"）
4. 提取目的地
5. 同时生成 2 个不同角度的检索变体

输出严格JSON：
{{
"rewritten": "主检索关键词",
"city": "提取到的城市名，没有则为空",
"variants": ["变体1", "变体2"]
}}

示例：
"北京有什么好玩的" → {{"rewritten": "北京 热门景点 必去 推荐", "city": "北京", "variants": ["北京 经典 攻略", "北京
地标 特色"]}}
"故宫好不好玩" → {{"rewritten": "故宫 评价 亮点 游玩体验", "city": "北京", "variants": ["故宫 攻略 必看", "故宫 历史
特色"]}}
"""


def rewrite_query(query: str) -> dict:
  """查询改写"""
  try:
      resp = client_llm.chat.completions.create(
          model=LLM_MODEL,
          messages=[{"role": "user", "content": f"{REWRITE_PROMPT}\n\n用户问题：{query}"}],
          temperature=0.1,
          max_tokens=4096,
      )
      return json.loads(resp.choices[0].message.content.strip())
  except Exception as e:
      return {"rewritten": query, "city": "", "variants": []}


def search_after_rewrite(query: str, limit: int = 5) -> list:
  """改写后：改写查询 + 多路检索 + 合并去重"""
  rewrite = rewrite_query(query)
  main_query = rewrite["rewritten"]
  variants = rewrite.get("variants", [])
  city = rewrite.get("city", "")

  queries_to_try = [main_query] + variants
  all_results = []
  seen = set()

  qfilter = None
  if city:
      qfilter = Filter(must=[FieldCondition(key="city", match=MatchValue(value=city))])

  for q in queries_to_try[:3]:
      vector = embedding_model.encode(q).tolist()
      for coll in ["attractions", "classic_routes", "user_plans"]:
          try:
              res = qdrant.query_points(
                  collection_name=coll,
                  query=vector,
                  query_filter=qfilter,
                  limit=3,
                  with_payload=True,
              )
              for hit in res.points:
                  key = hit.payload.get("content", "")[:80]
                  if key not in seen:
                      seen.add(key)
                      all_results.append({
                          "collection": coll,
                          "score": hit.score,
                          "content": hit.payload.get("content", ""),
                          "matched_query": q,
                      })
          except Exception:
              pass

  all_results.sort(key=lambda x: x["score"], reverse=True)
  return all_results[:limit]


# ==================== 评估函数 ====================
def evaluate(test_case: dict, results: list) -> dict:
  """评估单条结果：命中率 + 内容覆盖度"""
  all_content = " ".join([r.get("content", "") for r in results])
  expected = test_case["expected_keywords"]
  hits = [kw for kw in expected if kw in all_content]

  return {
      "hit_count": len(hits),
      "hit_keywords": hits,
      "miss_keywords": [kw for kw in expected if kw not in all_content],
      "min_hit_satisfied": len(hits) >= test_case["min_expected_hits"],
      "result_count": len(results),
      "top_score": results[0]["score"] if results else 0,
  }


# ==================== 主流程 ====================
def main():
  print("=" * 70)
  print("🔍 查询改写效果对比测试")
  print(f"   测试用例: {len(TEST_CASES)} 条")
  print(f"   检索集合: attractions / classic_routes / user_plans")
  print("=" * 70)

  # 测试
  detailed_results = []
  before_total_hits = 0
  after_total_hits = 0
  before_pass = 0
  after_pass = 0

  for i, tc in enumerate(TEST_CASES, 1):
      query = tc["query"]
      print(f"\n[{i}/{len(TEST_CASES)}] {query}")
      print("-" * 70)

      # 改写前
      before_results = search_before_rewrite(query, tc.get("expected_city", ""))
      before_eval = evaluate(tc, before_results)
      before_total_hits += before_eval["hit_count"]
      if before_eval["min_hit_satisfied"]:
          before_pass += 1

      # 改写后
      after_results = search_after_rewrite(query)
      after_eval = evaluate(tc, after_results)
      after_total_hits += after_eval["hit_count"]
      if after_eval["min_hit_satisfied"]:
          after_pass += 1

      # 打印对比
      print(f"  【改写前】命中 {before_eval['hit_count']}/{len(tc['expected_keywords'])} 关键词")
      print(f"     命中: {before_eval['hit_keywords']}")
      if before_eval['miss_keywords']:
          print(f"     缺失: {before_eval['miss_keywords']}")
      print(f"     {'✅ 通过' if before_eval['min_hit_satisfied'] else '❌ 未达标'}")

      print(f"  【改写后】命中 {after_eval['hit_count']}/{len(tc['expected_keywords'])} 关键词")
      print(f"     命中: {after_eval['hit_keywords']}")
      if after_eval['miss_keywords']:
          print(f"     缺失: {after_eval['miss_keywords']}")
      print(f"     {'✅ 通过' if after_eval['min_hit_satisfied'] else '❌ 未达标'}")

      # 改写结果
      rewrite = rewrite_query(query)
      print(f"  【改写示例】→ {rewrite['rewritten']}")
      if rewrite.get("variants"):
          for v in rewrite["variants"][:2]:
              print(f"              → {v}")

      detailed_results.append({
          "query": query,
          "before": before_eval,
          "after": after_eval,
          "rewrite": rewrite,
      })

      time.sleep(0.3)  # 防 API 限流

  # ==================== 总结 ====================
  print("\n" + "=" * 70)
  print("📊 总 体 对 比")
  print("=" * 70)

  n = len(TEST_CASES)
  print(f"\n{'指标':<25} {'改写前':<15} {'改写后':<15} {'提升':<10}")
  print("-" * 70)
  print(f"{'总命中关键词数':<25} {before_total_hits:<15} {after_total_hits:<15} "
        f"+{after_total_hits - before_total_hits}")
  print(f"{'通过测试用例数':<25} {before_pass}/{n:<13} {after_pass}/{n:<13} "
        f"+{after_pass - before_pass}")
  print(f"{'通过率':<25} {before_pass/n*100:.1f}%{'':<10} "
        f"{after_pass/n*100:.1f}%{'':<10} "
        f"{(after_pass - before_pass)/n*100:+.1f}%")
  print(f"{'平均每条命中关键词':<25} {before_total_hits/n:.1f}{'':<10} "
        f"{after_total_hits/n:.1f}{'':<10} "
        f"+{after_total_hits/n - before_total_hits/n:.2f}")

  # 保存详细结果
  output = {
      "summary": {
          "total_cases": n,
          "before_total_hits": before_total_hits,
          "after_total_hits": after_total_hits,
          "before_pass": before_pass,
          "after_pass": after_pass,
          "hit_improvement": after_total_hits - before_total_hits,
          "pass_rate_improvement": (after_pass - before_pass) / n * 100,
      },
      "detailed": detailed_results,
  }

  output_path = os.path.join(os.path.dirname(__file__), "data", "rewrite_comparison.json")
  with open(output_path, "w", encoding="utf-8") as f:
      json.dump(output, f, ensure_ascii=False, indent=2)

  print(f"\n💾 详细结果已保存: {output_path}")
  print("\n" + "=" * 70)
  print("✅ 测试完成")
  print("=" * 70)


if __name__ == "__main__":
  main()
