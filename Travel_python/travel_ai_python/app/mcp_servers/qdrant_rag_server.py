import os
from sentence_transformers import SentenceTransformer
from typing import Optional
from fastmcp import FastMCP
from qdrant_client import QdrantClient
from qdrant_client.models import Filter, FieldCondition, MatchValue

QDRANT_HOST = os.getenv("QDRANT_HOST", "192.168.71.140")
QDRANT_PORT = int(os.getenv("QDRANT_PORT", 6333))
EMBEDDING_MODEL = os.getenv("RAG_EMBEDDING_MODEL", "paraphrase-multilingual-MiniLM-L12-v2")

# ---------- 初始化 ----------
model = SentenceTransformer(EMBEDDING_MODEL)
qdrant_client = QdrantClient(host=QDRANT_HOST, port=QDRANT_PORT)

mcp = FastMCP("Qdrant RAG Server")

def _search(collection: str, query: str, city: Optional[str] = None, limit: int = 3) -> list:
    """通用检索，支持按城市过滤"""
    vector = model.encode(query).tolist()
    filter_cond = None
    if city:
        filter_cond = Filter(
            must=[FieldCondition(key="city", match=MatchValue(value=city))]
        )
    results = qdrant_client.search(
        collection_name=collection,
        query_vector=vector,
        query_filter=filter_cond,
        limit=limit,
        with_payload=True
    )
    return results

@mcp.tool
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

@mcp.tool
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

@mcp.tool
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

if __name__ == "__main__":
    mcp.run(transport="sse", host="127.0.0.1", port=9996)