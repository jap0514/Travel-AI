#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Qdrant RAG Server 测试脚本
测试内容：
1. 混合检索功能（向量 + 关键词融合）
2. 重排序功能
3. 检索时间性能
4. 检索准确性
"""

import os
import sys
import time
import json
from typing import List, Dict, Any

# 添加项目路径
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from sentence_transformers import SentenceTransformer, CrossEncoder
from qdrant_client import QdrantClient
from qdrant_client.models import Filter, FieldCondition, MatchValue

# 导入被测试模块（复用其初始化逻辑）
from qdrant_rag_server import (
    model, qdrant_client, reranker, _search, _search_old
)

# 配置
QDRANT_HOST = os.getenv("QDRANT_HOST", "192.168.71.140")
QDRANT_PORT = int(os.getenv("QDRANT_PORT", 6333))

# ============================================================
# 辅助函数
# ============================================================

def print_header(title: str):
    """打印分隔标题"""
    print("\n" + "=" * 60)
    print(f"  {title}")
    print("=" * 60)


def print_result(index: int, hit, score: float = None):
    """打印单条检索结果"""
    p = hit.payload
    score_str = f"{score:.4f}" if score else "N/A"
    print(f"\n--- 结果 {index + 1} (分数: {score_str}) ---")
    print(f"  ID: {hit.id}")
    print(f"  名称: {p.get('name') or p.get('title', '未知')}")
    content = p.get('content') or p.get('embedding_text', '')[:100]
    print(f"  内容摘要: {content}...")


def measure_time(func, *args, **kwargs):
    """测量函数执行时间"""
    start = time.perf_counter()
    result = func(*args, **kwargs)
    elapsed = time.perf_counter() - start
    return result, elapsed


# ============================================================
# 测试用例定义
# ============================================================

TEST_QUERIES = [
    {
        "query": "北京故宫历史背景和亮点特色",
        "city": "北京",
        "collection": "attractions",
        "expected_keywords": ["故宫", "历史", "明清", "皇帝", "建筑"]
    },
    {
        "query": "上海外滩景点推荐",
        "city": "上海",
        "collection": "attractions",
        "expected_keywords": ["外滩", "黄浦江", "万国建筑", "夜景"]
    },
    {
        "query": "西安三日游经典行程",
        "city": "西安",
        "collection": "classic_routes",
        "expected_keywords": ["西安", "三日", "行程", "兵马俑", "华山"]
    },
    {
        "query": "成都旅游美食攻略",
        "city": "成都",
        "collection": "attractions",
        "expected_keywords": ["成都", "美食", "火锅", "熊猫", "川菜"]
    },
]


# ============================================================
# 测试1: 混合检索功能测试
# ============================================================

def test_hybrid_search():
    """测试混合检索（向量+关键词融合）"""
    print_header("测试1: 混合检索功能")

    # 测试不同 alpha 值
    alpha_values = [0.0, 0.3, 0.5, 0.7, 1.0]

    test_case = TEST_QUERIES[0]  # 使用第一个测试用例
    query = test_case["query"]
    city = test_case["city"]
    collection = test_case["collection"]

    print(f"\n查询: {query}")
    print(f"城市: {city}")
    print(f"Collection: {collection}")

    results_by_alpha = {}

    for alpha in alpha_values:
        print(f"\n--- Alpha = {alpha} (向量权重={alpha}, 关键词权重={1-alpha}) ---")

        results, elapsed = measure_time(
            _search,
            collection=collection,
            query=query,
            city=city,
            limit=5,
            alpha=alpha,
            rerank=False,  # 暂时关闭重排序
            rerank_top_k=10
        )

        results_by_alpha[alpha] = results
        print(f"检索耗时: {elapsed*1000:.2f} ms")
        print(f"召回数量: {len(results)}")

        for i, hit in enumerate(results[:3]):
            p = hit.payload
            print(f"  [{i+1}] {p.get('name') or p.get('title', '未知')} (score: {hit.score:.4f})")

    # 分析不同 alpha 的结果差异
    print("\n--- Alpha 值影响分析 ---")
    for alpha, results in results_by_alpha.items():
        if results:
            top_name = results[0].payload.get('name') or results[0].payload.get('title', '未知')
            print(f"alpha={alpha}: Top1='{top_name}', 共{len(results)}条结果")

    return results_by_alpha


# ============================================================
# 测试2: 重排序功能测试
# ============================================================

def test_reranking():
    """测试重排序功能"""
    print_header("测试2: 重排序功能")

    test_case = TEST_QUERIES[0]
    query = test_case["query"]
    city = test_case["city"]
    collection = test_case["collection"]

    print(f"\n查询: {query}")

    # 不带重排序
    results_no_rerank, time_no_rerank = measure_time(
        _search,
        collection=collection,
        query=query,
        city=city,
        limit=5,
        alpha=0.7,
        rerank=False,
        rerank_top_k=20
    )

    # 带重排序
    results_with_rerank, time_with_rerank = measure_time(
        _search,
        collection=collection,
        query=query,
        city=city,
        limit=5,
        alpha=0.7,
        rerank=True,
        rerank_top_k=20
    )

    print(f"\n不带重排序: {len(results_no_rerank)}条结果, 耗时 {time_no_rerank*1000:.2f} ms")
    print(f"带重排序:   {len(results_with_rerank)}条结果, 耗时 {time_with_rerank*1000:.2f} ms")
    print(f"重排序额外耗时: {(time_with_rerank - time_no_rerank)*1000:.2f} ms")

    print("\n--- 对比 Top 5 结果 ---")
    print("\n【不带重排序】")
    for i, hit in enumerate(results_no_rerank[:5]):
        p = hit.payload
        print(f"  {i+1}. {p.get('name') or p.get('title', '未知')} (hybrid score: {hit.score:.4f})")

    print("\n【带重排序】")
    for i, hit in enumerate(results_with_rerank[:5]):
        p = hit.payload
        print(f"  {i+1}. {p.get('name') or p.get('title', '未知')} (score: {hit.score:.4f})")

    # 计算结果差异
    ids_no_rerank = set(h.id for h in results_no_rerank[:5])
    ids_with_rerank = set(h.id for h in results_with_rerank[:5])

    common = ids_no_rerank & ids_with_rerank
    only_no_rerank = ids_no_rerank - ids_with_rerank
    only_with_rerank = ids_with_rerank - ids_no_rerank

    print(f"\n结果差异分析:")
    print(f"  两者共有: {len(common)} 条")
    print(f"  仅在无重排序中出现: {len(only_no_rerank)} 条")
    print(f"  仅在重排序后出现: {len(only_with_rerank)} 条")

    if only_with_rerank:
        print(f"  重排序后新纳入的 ID: {list(only_with_rerank)[:3]}")

    return results_with_rerank


# ============================================================
# 测试3: 检索时间性能测试
# ============================================================

def test_performance():
    """测试检索时间性能"""
    print_header("测试3: 检索时间性能测试")

    print("\n对所有测试用例进行性能测试...\n")

    performance_data = []

    for i, test_case in enumerate(TEST_QUERIES):
        query = test_case["query"]
        city = test_case["city"]
        collection = test_case["collection"]

        print(f"--- 测试用例 {i+1}: {query[:20]}... ---")

        # 执行多次取平均
        times = []
        for _ in range(3):
            _, elapsed = measure_time(
                _search,
                collection=collection,
                query=query,
                city=city,
                limit=5,
                alpha=0.7,
                rerank=True,
                rerank_top_k=20
            )
            times.append(elapsed)

        avg_time = sum(times) / len(times)
        min_time = min(times)
        max_time = max(times)

        print(f"  平均耗时: {avg_time*1000:.2f} ms")
        print(f"  最小耗时: {min_time*1000:.2f} ms")
        print(f"  最大耗时: {max_time*1000:.2f} ms")

        performance_data.append({
            "query": query,
            "avg_time_ms": avg_time * 1000,
            "min_time_ms": min_time * 1000,
            "max_time_ms": max_time * 1000
        })

    # 性能总结
    print("\n--- 性能总结 ---")
    total_avg = sum(p["avg_time_ms"] for p in performance_data) / len(performance_data)
    print(f"所有用例平均检索耗时: {total_avg:.2f} ms")

    return performance_data


# ============================================================
# 测试4: 检索准确性测试
# ============================================================

def test_accuracy():
    """测试检索准确性"""
    print_header("测试4: 检索准确性测试")

    def calculate_keyword_match(results: list, expected_keywords: List[str]) -> Dict[str, Any]:
        """计算关键词匹配率"""
        all_text = " ".join([
            str(hit.payload.get('content', '')) +
            str(hit.payload.get('name', '')) +
            str(hit.payload.get('title', '')) +
            str(hit.payload.get('history', '')) +
            str(hit.payload.get('highlights', ''))
            for hit in results
        ])

        matches = []
        for kw in expected_keywords:
            if kw in all_text:
                matches.append(kw)

        return {
            "matched_keywords": matches,
            "match_rate": len(matches) / len(expected_keywords) if expected_keywords else 0,
            "total_keywords": len(expected_keywords)
        }

    accuracy_results = []

    for i, test_case in enumerate(TEST_QUERIES):
        query = test_case["query"]
        city = test_case["city"]
        collection = test_case["collection"]
        expected = test_case["expected_keywords"]

        print(f"\n--- 测试用例 {i+1} ---")
        print(f"查询: {query}")
        print(f"期望关键词: {expected}")

        # 执行检索
        results, elapsed = measure_time(
            _search,
            collection=collection,
            query=query,
            city=city,
            limit=5,
            alpha=0.7,
            rerank=True,
            rerank_top_k=20
        )

        # 计算准确性
        if results:
            top1 = results[0].payload
            top1_name = top1.get('name') or top1.get('title', '未知')
            top1_relevant = any(kw in str(top1) for kw in expected[:2])  # 至少匹配2个关键词

            keyword_stats = calculate_keyword_match(results, expected)

            print(f"Top1 结果: {top1_name}")
            print(f"Top1 相关性: {'✓ 命中' if top1_relevant else '✗ 未命中'}")
            print(f"关键词匹配率: {keyword_stats['match_rate']*100:.1f}% ({len(keyword_stats['matched_keywords'])}/{len(expected)})")
            print(f"匹配关键词: {keyword_stats['matched_keywords']}")
            print(f"检索耗时: {elapsed*1000:.2f} ms")

            accuracy_results.append({
                "query": query,
                "top1_name": top1_name,
                "top1_relevant": top1_relevant,
                "keyword_match_rate": keyword_stats['match_rate'],
                "elapsed_ms": elapsed * 1000
            })
        else:
            print("未检索到任何结果 ✗")
            accuracy_results.append({
                "query": query,
                "error": "no results"
            })

    # 准确性总结
    print("\n--- 准确性总结 ---")
    valid_results = [r for r in accuracy_results if "error" not in r]
    if valid_results:
        top1_hit_rate = sum(1 for r in valid_results if r["top1_relevant"]) / len(valid_results)
        avg_keyword_match = sum(r["keyword_match_rate"] for r in valid_results) / len(valid_results)

        print(f"Top1 命中率: {top1_hit_rate*100:.1f}%")
        print(f"平均关键词匹配率: {avg_keyword_match*100:.1f}%")

    return accuracy_results


# ============================================================
# 测试5: Collection 状态检查
# ============================================================

def check_collections():
    """检查所有 Collection 的状态"""
    print_header("测试0: Collection 状态检查")

    collections = ["attractions", "classic_routes", "user_plans"]

    for name in collections:
        try:
            info = qdrant_client.get_collection(collection_name=name)
            count = qdrant_client.count(collection_name=name).count
            print(f"\n【{name}】")
            print(f"  状态: {info.status}")
            print(f"  向量数量: {count}")
            print(f"  向量维度: {info.config.params.vectors.size}")
            print(f"  距离度量: {info.config.params.vectors.distance}")
        except Exception as e:
            print(f"\n【{name}】")
            print(f"  错误: {e}")


# ============================================================
# 测试6: 端到端混合检索 + 重排序演示
# ============================================================

def test_end_to_end():
    """端到端测试：完整的混合检索 + 重排序流程"""
    print_header("测试5: 端到端混合检索 + 重排序演示")

    test_case = TEST_QUERIES[0]
    query = test_case["query"]
    city = test_case["city"]
    collection = test_case["collection"]

    print(f"\n执行查询: \"{query}\"")
    print(f"城市: {city}")
    print(f"Collection: {collection}")
    print(f"参数: alpha=0.7, rerank=True, limit=5")

    results, elapsed = measure_time(
        _search,
        collection=collection,
        query=query,
        city=city,
        limit=5,
        alpha=0.7,
        rerank=True,
        rerank_top_k=20
    )

    print(f"\n检索完成，耗时: {elapsed*1000:.2f} ms")
    print(f"返回结果数: {len(results)}")

    print("\n" + "-" * 50)
    print("检索结果详情:")
    print("-" * 50)

    for i, hit in enumerate(results):
        p = hit.payload
        print(f"\n【结果 {i+1}】")
        print(f"  ID: {hit.id}")
        print(f"  名称: {p.get('name') or p.get('title', '未知')}")

        if collection == "attractions":
            history = p.get('history', '')[:80]
            highlights = p.get('highlights', '')[:80]
            print(f"  历史: {history}...")
            print(f"  亮点: {highlights}...")
        else:
            content = p.get('content', '')[:100]
            print(f"  内容: {content}...")

        # 显示分数组成
        if hasattr(hit, 'score'):
            print(f"  混合分数: {hit.score:.4f}")

    return results


# ============================================================
# 主函数
# ============================================================

def main():
    print("""
    ╔══════════════════════════════════════════════════════════════╗
    ║          Qdrant RAG 混合检索 & 重排序 测试脚本                 ║
    ║                                                              ║
    ║  测试内容:                                                   ║
    ║  1. Collection 状态检查                                      ║
    ║  2. 混合检索功能（不同 alpha 值）                              ║
    ║  3. 重排序功能对比                                           ║
    ║  4. 检索时间性能测试                                         ║
    ║  5. 检索准确性评估                                           ║
    ║  6. 端到端完整流程演示                                       ║
    ╚══════════════════════════════════════════════════════════════╝
    """)

    try:
        # 0. 检查 Collection 状态
        check_collections()

        # 1. 测试混合检索
        test_hybrid_search()

        # 2. 测试重排序
        test_reranking()

        # 3. 性能测试
        test_performance()

        # 4. 准确性测试
        test_accuracy()

        # 5. 端到端演示
        test_end_to_end()

        print("\n" + "=" * 60)
        print("  所有测试完成!")
        print("=" * 60)

    except Exception as e:
        print(f"\n测试过程中发生错误: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()