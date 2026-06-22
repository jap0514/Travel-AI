import os
import sys
import zlib
from datetime import datetime

# 确保项目根目录在 sys.path 中，使 app 模块可导入
_current = os.path.dirname(os.path.abspath(__file__))
_root = os.path.dirname(os.path.dirname(_current))  # travel_ai_python/
if _root not in sys.path:
    sys.path.insert(0, _root)

from sentence_transformers import SentenceTransformer,CrossEncoder
from typing import Optional
from mcp.server.fastmcp import FastMCP
from qdrant_client import QdrantClient
from qdrant_client.models import PointStruct, Filter, FieldCondition, MatchValue, Range, SparseVector, Prefetch, FusionQuery
from typing import Dict, Any, List, Optional
import uuid
from app.config.logger import logger

QDRANT_HOST = os.getenv("QDRANT_HOST", "192.168.71.140")
QDRANT_PORT = int(os.getenv("QDRANT_PORT", 6333))
EMBEDDING_MODEL = os.getenv("RAG_EMBEDDING_MODEL", "paraphrase-multilingual-MiniLM-L12-v2")

# ---------- 初始化 ----------
model = SentenceTransformer(EMBEDDING_MODEL)
qdrant_client = QdrantClient(host=QDRANT_HOST, port=QDRANT_PORT)
# 重排序模型
reranker = CrossEncoder('cross-encoder/ms-marco-MiniLM-L-6-v2', device='cpu')

mcp = FastMCP("Qdrant RAG Server", host="127.0.0.1", port=9996)

def _search_old(collection: str, query: str, city: Optional[str] = None, limit: int = 3) -> list:
    """通用检索，支持按城市过滤"""
    vector = model.encode(query).tolist()
    filter_cond = None
    if city:
        filter_cond = Filter(
            must=[FieldCondition(key="city", match=MatchValue(value=city))]
        )
    results = qdrant_client.query_points(
        collection_name=collection,
        query=vector,
        query_filter=filter_cond,
        limit=limit,
        with_payload=True
    )
    return results.points


def _search(
    collection: str,
    query: str,
    city: Optional[str] = None,
    limit: int = 5,
    alpha: float = 0.75,          # 向量 vs 关键词权重（0.0~1.0）
    rerank: bool = True,          # 是否开启重排序
    rerank_top_k: int = 12        # 先召回多少条再重排序
) -> list:
    """
    升级版混合检索 + 重排序
    """
    logger.info(f"🔍 执行混合检索 | Collection: {collection} | Query: {query} | City: {city}")

    try:
        query_vector = model.encode(query).tolist()

        # 构建过滤条件（按城市过滤）
        filter_cond = None
        if city:
            filter_cond = Filter(must=[FieldCondition(key="city", match=MatchValue(value=city))])

        # ==================== 混合检索：FusionQuery (RRF) ====================
        # 使用 RRF (Reciprocal Rank Fusion) 融合向量检索和关键词检索
        merged_results = []
        try:
            sparse_vec = _compute_query_sparse(query)
            if sparse_vec["indices"]:
                results = qdrant_client.query_points(
                    collection_name=collection,
                    query=FusionQuery(fusion='rrf'),
                    prefetch=[
                        Prefetch(query=query_vector, limit=rerank_top_k * 2),
                        Prefetch(
                            query=SparseVector(
                                indices=sparse_vec["indices"],
                                values=sparse_vec["values"]
                            ),
                            using="text",
                            limit=rerank_top_k * 2
                        ),
                    ],
                    query_filter=filter_cond,
                    limit=rerank_top_k * 2,
                    with_payload=True
                ).points
                merged_results = results
                logger.info(f"混合检索完成 → 召回 {len(merged_results)} 条")
            else:
                logger.warning("Query sparse vector 为空，仅使用向量搜索")
                results = qdrant_client.query_points(
                    collection_name=collection,
                    query=query_vector,
                    query_filter=filter_cond,
                    limit=rerank_top_k * 2,
                    with_payload=True
                ).points
                merged_results = results
        except Exception as e:
            logger.warning(f"关键词搜索失败: {e}，仅使用向量搜索")
            results = qdrant_client.query_points(
                collection_name=collection,
                query=query_vector,
                query_filter=filter_cond,
                limit=rerank_top_k * 2,
                with_payload=True
            ).points
            merged_results = results

        # ==================== 4. 重排序 (Reranking) ====================
        if rerank and len(merged_results) > 3 and reranker is not None:
            logger.info(f"开始重排序 Top-{min(rerank_top_k, len(merged_results))} → Top-{limit}")

            rerank_pairs = []
            top_candidates = merged_results[:rerank_top_k]

            for hit in top_candidates:
                text = (hit.payload.get("content") or
                       hit.payload.get("embedding_text") or
                       hit.payload.get("history") or
                       str(hit.payload))
                rerank_pairs.append((query, text))

            scores = reranker.predict(rerank_pairs)

            reranked = sorted(
                zip(top_candidates, scores),
                key=lambda x: x[1],
                reverse=True
            )

            final_results = [item[0] for item in reranked[:limit]]
            logger.info(f"✅ 重排序完成，最终返回 {len(final_results)} 条结果")
        else:
            final_results = merged_results[:limit]

        return final_results

    except Exception as e:
        logger.error(f"_search 执行失败: {e}")
        try:
            vector_results = qdrant_client.query_points(
                collection_name=collection,
                query=query_vector,
                query_filter=filter_cond,
                limit=limit,
                with_payload=True
            ).points
            return vector_results
        except:
            return []


def _compute_query_sparse(query: str) -> dict:
    """
    计算查询文本的 BM25 sparse vector
    与 init_qdrant_rag.py 中的 tokenize 和 BM25 算法保持一致
    """
    import math

    def tokenize(text: str) -> list:
        tokens = []
        for i in range(len(text)):
            tokens.append(text[i])
        for i in range(len(text) - 1):
            tokens.append(text[i:i+2])
        return tokens

    tokens = tokenize(query)
    # 返回与 Qdrant sparse vector 格式一致的 dict
    # 注意：这里只返回 indices/values，BM25 score 作为 values
    # 由于我们没有全局 doc_freqs，这里简化为 term frequency 作为 score
    tf = {}
    for t in tokens:
        tf[t] = tf.get(t, 0) + 1

    # 取 top 64 个 term
    sorted_terms = sorted(tf.items(), key=lambda x: x[1], reverse=True)[:64]
    indices = [zlib.crc32(t.encode()) % 100000 for t, _ in sorted_terms]
    values = [float(s) for _, s in sorted_terms]

    return {"indices": indices, "values": values}



@mcp.tool()
def search_classic_routes(destination: str, days: int, limit: int = 3) -> str:
    """检索经典行程模板（如北京3日游）"""
    query = f"{destination} {days}日游 经典行程"
    results = _search("classic_routes", query, city=destination, limit=limit)
    if not results:
        return "未找到匹配的经典行程模板。"
    output = []
    for hit in results:
        p = hit.payload
        output.append(f"【模板】{p.get('title', '未命名')}\n{p.get('content', '')}\n")
    return "\n".join(output)

@mcp.tool()
def search_attractions(city: str, keyword: str = "", limit: int = 5) -> str:
    """检索景点历史背景、文化意义、亮点特色"""
    query = f"{city} {keyword} 景点 历史文化 特色"
    results = _search("attractions", query, city=city, limit=limit)
    if not results:
        return "未找到相关景点信息。"
    output = []
    for hit in results:
        p = hit.payload
        output.append(
            f"【{p.get('name', '未知景点')}】\n"
            f"历史：{p.get('history', '')}\n"
            f"亮点：{p.get('highlights', '')}\n"
            f"贴士：{p.get('tips', '')}\n"
        )
    return "\n".join(output)

@mcp.tool()
def search_user_plans(destination: str, preferences: str = "", limit: int = 3) -> str:
    """检索历史用户的真实行程规划"""
    query = f"{destination} {preferences} 游记 行程"
    results = _search("user_plans", query, city=destination, limit=limit)
    if not results:
        return "未找到相似的用户行程记录。"
    output = []
    for hit in results:
        p = hit.payload
        output.append(f"【来源：{p.get('source', '用户分享')}】\n{p.get('content', '')}\n")
    return "\n".join(output)


@mcp.tool()
def add_knowledge(
        knowledge_type: str,  # "classic_route", "attraction", "user_plan"
        payload: Dict[str, Any],
        point_id: Optional[str] = None
) -> str:
    """
    新增一条知识到对应的 collection。
    payload 中必须包含 'embedding_text' 字段用于生成向量。
    其他字段按实际业务需要填写（如 name, city, content 等）。
    如果不提供 point_id，将自动生成 UUID。
    """
    # 类型到 collection 名称的映射
    collection_map = {
        "classic_route": "classic_routes",
        "attraction": "attractions",
        "user_plan": "user_plans",
    }
    if knowledge_type not in collection_map:
        return f"不支持的知识类型: {knowledge_type}，可选: {list(collection_map.keys())}"

    collection = collection_map[knowledge_type]

    # 检查是否有 embedding_text
    if "embedding_text" not in payload:
        return "payload 中缺少 'embedding_text' 字段，无法生成向量。"

    # 自动添加时间字段（如果调用方未提供）
    now_iso = datetime.now().isoformat()
    if "create_time" not in payload:
        payload["create_time"] = now_iso
    if "update_time" not in payload:
        payload["update_time"] = now_iso

    # 生成向量
    try:
        vector = model.encode(payload["embedding_text"]).tolist()
    except Exception as e:
        return f"生成向量失败: {str(e)}"

    # 生成 ID
    if point_id is None:
        point_id = str(uuid.uuid4())

    # 构建 PointStruct
    point = PointStruct(
        id=point_id,
        vector=vector,
        payload=payload
    )

    try:
        qdrant_client.upsert(collection_name=collection, points=[point])
        return f"✅ 成功添加 {knowledge_type}，ID: {point_id}"
    except Exception as e:
        return f"添加失败: {str(e)}"


@mcp.tool()
def update_knowledge(
        knowledge_type: str,
        point_id: str,
        updated_payload: Dict[str, Any]
) -> str:
    """
    更新已存在的知识条目。
    如果 updated_payload 中包含 'embedding_text'，会重新生成向量。
    否则只更新 payload 中指定的字段（合并方式，不会删除原有字段）。
    """
    collection_map = {
        "classic_route": "classic_routes",
        "attraction": "attractions",
        "user_plan": "user_plans",
    }
    if knowledge_type not in collection_map:
        return f"不支持的知识类型: {knowledge_type}"
    collection = collection_map[knowledge_type]

    # 先获取原有数据
    try:
        points = qdrant_client.retrieve(
            collection_name=collection,
            ids=[point_id],
            with_payload=True
        )
        if not points:
            return f"未找到 ID 为 {point_id} 的数据"
        old_payload = points[0].payload
    except Exception as e:
        return f"查询原数据失败: {str(e)}"

    # 合并 payload（新覆盖旧），创建时间还是原来的
    new_payload = {**old_payload, **updated_payload}
    new_payload["create_time"] = old_payload.get("create_time", datetime.now().isoformat())
    new_payload["update_time"] = datetime.now().isoformat()  # 强制更新

    # 如果 embedding_text 有变化，重新生成向量
    if "embedding_text" in updated_payload:
        try:
            vector = model.encode(new_payload["embedding_text"]).tolist()
        except Exception as e:
            return f"重新生成向量失败: {str(e)}"
    else:
        # 向量不变，但仍需获取原向量
        vector = points[0].vector

    point = PointStruct(id=point_id, vector=vector, payload=new_payload)
    try:
        qdrant_client.upsert(collection_name=collection, points=[point])
        return f"✅ 成功更新 {knowledge_type}，ID: {point_id}"
    except Exception as e:
        return f"更新失败: {str(e)}"


@mcp.tool()
def get_knowledge_by_id(knowledge_type: str, point_id: str) -> str:
    """根据 ID 查询单条知识，返回 JSON 格式的 payload"""
    collection_map = {
        "classic_route": "classic_routes",
        "attraction": "attractions",
        "user_plan": "user_plans",
    }
    if knowledge_type not in collection_map:
        return f"不支持的知识类型: {knowledge_type}"
    collection = collection_map[knowledge_type]

    try:
        points = qdrant_client.retrieve(
            collection_name=collection,
            ids=[point_id],
            with_payload=True
        )
        if not points:
            return f"未找到 ID 为 {point_id} 的数据"
        payload = points[0].payload
        # 将 payload 转为可读的 JSON 字符串
        import json
        return json.dumps(payload, ensure_ascii=False, indent=2)
    except Exception as e:
        return f"查询失败: {str(e)}"


@mcp.tool()
def delete_knowledge_by_id(knowledge_type: str, point_id: str) -> str:
    """根据 ID 删除单条知识"""
    collection_map = {
        "classic_route": "classic_routes",
        "attraction": "attractions",
        "user_plan": "user_plans",
    }
    if knowledge_type not in collection_map:
        return f"不支持的知识类型: {knowledge_type}"
    collection = collection_map[knowledge_type]

    try:
        qdrant_client.delete(collection_name=collection, points_selector=[point_id])
        return f"✅ 成功删除 {knowledge_type}，ID: {point_id}"
    except Exception as e:
        return f"删除失败: {str(e)}"


@mcp.tool()
def delete_by_filter(
        knowledge_type: str,
        city: Optional[str] = None,
        tag: Optional[str] = None
) -> str:
    """
    按条件批量删除知识。
    - knowledge_type: classic_route / attraction / user_plan
    - city: 城市名称（如北京、西安）
    - tag: 标签（tags 数组是否包含该值）
    """
    collection_map = {
        "classic_route": "classic_routes",
        "attraction": "attractions",
        "user_plan": "user_plans",
    }
    if knowledge_type not in collection_map:
        return f"不支持的知识类型: {knowledge_type}"
    collection = collection_map[knowledge_type]

    # 构建过滤条件
    must_conditions = []
    if city:
        must_conditions.append(FieldCondition(key="city", match=MatchValue(value=city)))
    if tag:
        # tags 字段是字符串数组，判断是否包含某个值
        must_conditions.append(FieldCondition(key="tags", match=MatchValue(value=tag)))

    if not must_conditions:
        return "请至少提供一个删除条件（city 或 tag），防止误删全量数据。"

    filter_obj = Filter(must=must_conditions)

    try:
        # 先查询符合条件的 ID 列表（用于给用户确认）
        scroll_result = qdrant_client.scroll(
            collection_name=collection,
            scroll_filter=filter_obj,
            limit=100,  # 最多删除100条，防止误删太多
            with_payload=False
        )
        points = scroll_result[0]
        if not points:
            return f"未找到符合条件的 {knowledge_type} 数据。"

        ids = [p.id for p in points]
        # 执行删除
        qdrant_client.delete(collection_name=collection, points_selector=ids)
        return f"✅ 已删除 {len(ids)} 条 {knowledge_type} 数据。\n删除的 ID：{ids}"
    except Exception as e:
        return f"批量删除失败: {str(e)}"


@mcp.tool()
def get_collection_stats(knowledge_type: str) -> str:
    """获取指定 collection 的统计信息：向量数量、存储大小等"""
    collection_map = {
        "classic_route": "classic_routes",
        "attraction": "attractions",
        "user_plan": "user_plans",
    }
    if knowledge_type not in collection_map:
        return f"不支持的知识类型: {knowledge_type}"
    collection = collection_map[knowledge_type]

    try:
        info = qdrant_client.get_collection(collection_name=collection)
        points_count = qdrant_client.count(collection_name=collection).count
        return (
            f"Collection: {collection}\n"
            f"向量数量: {points_count}\n"
            f"状态: {info.status}\n"
            f"向量维度: {info.config.params.vectors.size}\n"
            f"距离度量: {info.config.params.vectors.distance}"
        )
    except Exception as e:
        return f"获取统计信息失败: {str(e)}"


@mcp.tool()
def clear_collection(knowledge_type: str, confirm: bool = False) -> str:
    """⚠️ 清空整个 collection（删除所有数据）。需要 confirm=True 才能执行。"""
    if not confirm:
        return "⚠️ 危险操作！请设置 confirm=True 以确认清空整个集合。"

    collection_map = {
        "classic_route": "classic_routes",
        "attraction": "attractions",
        "user_plan": "user_plans",
    }
    if knowledge_type not in collection_map:
        return f"不支持的知识类型: {knowledge_type}"
    collection = collection_map[knowledge_type]

    try:
        # 删除整个 collection 然后重建（保留原配置）
        config = qdrant_client.get_collection(collection_name=collection).config
        qdrant_client.delete_collection(collection_name=collection)
        qdrant_client.create_collection(
            collection_name=collection,
            vectors_config=config.params.vectors
        )
        return f"✅ 已清空 {collection} 集合（重建空集合并保留原始配置）"
    except Exception as e:
        return f"清空失败: {str(e)}"


@mcp.tool()
def list_collections() -> str:
    """列出所有可用的 collection 名称及内部类型"""
    try:
        collections = qdrant_client.get_collections().collections
        names = [c.name for c in collections]
        type_map = {
            "classic_routes": "classic_route",
            "attractions": "attraction",
            "user_plans": "user_plan",
        }
        result = []
        for name in names:
            if name in type_map:
                result.append(f"{name} → {type_map[name]}")
            else:
                result.append(f"{name} (未知类型)")
        return "\n".join(result) if result else "没有找到任何 collection。"
    except Exception as e:
        return f"列表获取失败: {str(e)}"


@mcp.tool()
def hybrid_search(
        query: str,
        collection_type: str = "attractions",  # attractions / classic_routes / user_plans
        city: Optional[str] = None,
        limit: int = 5,
        alpha: float = 0.7
) -> str:
    """混合检索（向量 + 关键词），推荐用于 RAG 查询"""

    collection_map = {
        "attractions": "attractions",
        "classic_routes": "classic_routes",
        "user_plans": "user_plans",
    }

    if collection_type not in collection_map:
        return "不支持的 collection_type"

    collection = collection_map[collection_type]

    results = _search(
        collection=collection,
        query=query,
        city=city,
        limit=limit,
        alpha=alpha
    )

    if not results:
        return "未找到相关内容。"

    output = []
    for hit in results:
        p = hit.payload
        score = hit.score if hasattr(hit, 'score') else "N/A"

        if collection_type == "attractions":
            output.append(
                f"【{p.get('name', '未知')}】 (相似度: {score:.3f})\n"
                f"历史：{p.get('history', '')}\n"
                f"亮点：{p.get('highlights', '')}\n"
                f"贴士：{p.get('tips', '')}\n"
            )
        else:
            output.append(f"【{p.get('title', '未命名')}】 (相似度: {score:.3f})\n{p.get('content', '')}\n")

    return "\n---\n".join(output)

if __name__ == "__main__":
    mcp.run(transport="sse")