# -*- coding: utf-8 -*-
"""
导入广东景点chunks到Qdrant向量数据库
"""
import json
import math
import zlib
from sentence_transformers import SentenceTransformer
from qdrant_client import QdrantClient
from qdrant_client.models import VectorParams, Distance, PointStruct, SparseVector, SparseIndexParams, SparseVectorParams

# ---------- 配置 ----------
QDRANT_HOST = "192.168.71.140"
QDRANT_PORT = 6333
EMBEDDING_MODEL = "paraphrase-multilingual-MiniLM-L12-v2"
JSON_PATH = r"D:\毕业设计\Travel_python\travel_ai_python\scripts\data\attractions_chunks.json"
COLLECTION_NAME = "attractions"

# 初始化
print("加载Embedding模型...")
model = SentenceTransformer(EMBEDDING_MODEL)
print(f"模型: {EMBEDDING_MODEL}")

print("连接Qdrant...")
client = QdrantClient(host=QDRANT_HOST, port=QDRANT_PORT)


# ============================================================
# BM25 计算
# ============================================================

def tokenize(text: str) -> list:
    """简单中文分词：单字 + 双字"""
    text = text.strip()
    tokens = []
    for i in range(len(text)):
        tokens.append(text[i])
    for i in range(len(text) - 1):
        tokens.append(text[i:i+2])
    return tokens


def compute_bm25_sparse(doc_tokens: list, query_tokens: list, avg_dl: float, N: int, doc_freqs: dict) -> tuple:
    """计算 BM25 sparse vector"""
    k1, b = 1.5, 0.75
    dl = len(doc_tokens)

    doc_tf = {}
    for t in doc_tokens:
        doc_tf[t] = doc_tf.get(t, 0) + 1

    scores = {}
    for t in query_tokens:
        if t not in doc_tf:
            continue
        tf = doc_tf[t]
        df = doc_freqs.get(t, 0)
        if df == 0:
            continue
        idf = math.log((N - df + 0.5) / (df + 0.5) + 1)
        tf_norm = (tf * (k1 + 1)) / (tf + k1 * (1 - b + b * (dl / avg_dl)))
        scores[t] = idf * tf_norm

    sorted_scores = sorted(scores.items(), key=lambda x: x[1], reverse=True)[:64]
    if not sorted_scores:
        return [], []

    indices = [zlib.crc32(t.encode()) % 100000 for t, _ in sorted_scores]
    values = [s for _, s in sorted_scores]
    return indices, values


def build_sparse_vectors(items: list) -> dict:
    """为所有文档计算 BM25 sparse vectors"""
    doc_tokens_list = [tokenize(item.get("embedding_text", "")) for item in items]

    doc_freqs = {}
    for tokens in doc_tokens_list:
        for t in set(tokens):
            doc_freqs[t] = doc_freqs.get(t, 0) + 1

    N = len(items)
    avg_dl = sum(len(t) for t in doc_tokens_list) / N if N > 0 else 0

    sparse_vectors = {}
    for item, doc_tokens in zip(items, doc_tokens_list):
        query_tokens = tokenize(item.get("embedding_text", ""))
        indices, values = compute_bm25_sparse(doc_tokens, query_tokens, avg_dl, N, doc_freqs)
        if indices:
            sparse_vectors[item["id"]] = {"indices": indices, "values": values}

    return sparse_vectors


# ============================================================
# 读取并转换数据
# ============================================================

print(f"读取数据: {JSON_PATH}")
with open(JSON_PATH, "r", encoding="utf-8") as f:
    chunks = json.load(f)

print(f"共 {len(chunks)} 个chunks")

# 转换为Qdrant需要的格式
items = []
for i, chunk in enumerate(chunks):
    title = chunk.get("title", "")
    city = chunk.get("city", "")
    content = chunk.get("content", "")

    # 生成唯一ID
    item_id = f"{title}_{chunk.get('chunk_index', 0)}"

    # embedding_text 用于生成向量（加入城市和标题作为上下文）
    embedding_text = f"{city} {title} {content}"

    items.append({
        "id": item_id,
        "title": title,
        "city": city,
        "chunk_index": chunk.get("chunk_index", 0),
        "content": content,
        "embedding_text": embedding_text,
        "source_url": chunk.get("source_url", "")
    })

print(f"转换完成: {len(items)} 条")


# ============================================================
# 重建Collection
# ============================================================

print(f"\n重建 Collection: {COLLECTION_NAME}")
try:
    client.delete_collection(collection_name=COLLECTION_NAME)
    print(f"  [删除] {COLLECTION_NAME}")
except Exception:
    pass

vectors_config = VectorParams(size=384, distance=Distance.COSINE)
client.create_collection(
    collection_name=COLLECTION_NAME,
    vectors_config=vectors_config,
    sparse_vectors_config={
        "text": SparseVectorParams(index=SparseIndexParams(on_disk=False))
    }
)
print(f"  [创建] {COLLECTION_NAME} (dense + sparse)")


# ============================================================
# 导入数据
# ============================================================

print("\n计算 Sparse Vectors (BM25)...")
sparse_vectors = build_sparse_vectors(items)

print("生成 Dense Vectors 并导入...")
points = []
for item in items:
    text = item["embedding_text"]
    dense_vector = model.encode(text).tolist()
    sparse = sparse_vectors.get(item["id"], {"indices": [], "values": []})

    payload = {
        "title": item["title"],
        "city": item["city"],
        "chunk_index": item["chunk_index"],
        "content": item["content"],
        "source_url": item["source_url"]
    }

    vec = {"": dense_vector}
    if sparse["indices"]:
        vec["text"] = SparseVector(indices=sparse["indices"], values=sparse["values"])

    points.append(PointStruct(
        id=item["id"],
        vector=vec,
        payload=payload
    ))

client.upsert(collection_name=COLLECTION_NAME, points=points)
print(f"  [导入] {COLLECTION_NAME}: {len(points)} 条")

print("\n✅ 广东景点数据导入完成！")