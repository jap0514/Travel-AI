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
JSON_PATH = r"D:\毕业设计\Travel_python\travel_ai_python\scripts\data\travel_knowledge2.json"

# 初始化
model = SentenceTransformer(EMBEDDING_MODEL)
client = QdrantClient(host=QDRANT_HOST, port=QDRANT_PORT)


# ============================================================
# BM25 计算（用于生成 sparse vector）
# ============================================================

def tokenize(text: str) -> list:
    """简单中分词：单字 + 双字"""
    text = text.strip()
    tokens = []
    for i in range(len(text)):
        tokens.append(text[i])
    for i in range(len(text) - 1):
        tokens.append(text[i:i+2])
    return tokens


def compute_bm25_sparse(doc_tokens: list, query_tokens: list, avg_dl: float, N: int, doc_freqs: dict) -> tuple:
    """
    计算 BM25 sparse vector
    返回: (indices, values) - Qdrant sparse vector 格式
    """
    k1, b = 1.5, 0.75
    dl = len(doc_tokens)

    # 文档词频
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

    # indices 用词哈希映射到整数（Qdrant sparse vector 要求正整数索引）
    indices = [zlib.crc32(t.encode()) % 100000 for t, _ in sorted_scores]
    values = [s for _, s in sorted_scores]
    return indices, values


def build_sparse_vectors(items: list) -> dict:
    """为所有文档计算 BM25 sparse vectors"""
    # 分词
    doc_tokens_list = [tokenize(item.get("embedding_text", "")) for item in items]

    # 文档频率
    doc_freqs = {}
    for tokens in doc_tokens_list:
        for t in set(tokens):
            doc_freqs[t] = doc_freqs.get(t, 0) + 1

    N = len(items)
    avg_dl = sum(len(t) for t in doc_tokens_list) / N if N > 0 else 0

    # 每篇文档的 sparse vector
    sparse_vectors = {}
    for item, doc_tokens in zip(items, doc_tokens_list):
        query_tokens = tokenize(item.get("embedding_text", ""))
        indices, values = compute_bm25_sparse(doc_tokens, query_tokens, avg_dl, N, doc_freqs)
        if indices:
            sparse_vectors[item["id"]] = {"indices": indices, "values": values}

    return sparse_vectors


# ============================================================
# Collection 重建（带 sparse_vectors 配置）
# ============================================================

def recreate_collection(name: str, vectors_config: VectorParams):
    """删除旧 collection 并重建，支持 sparse vectors"""
    try:
        client.delete_collection(collection_name=name)
        print(f"  [删除] {name}")
    except Exception:
        pass

    client.create_collection(
        collection_name=name,
        vectors_config=vectors_config,
        sparse_vectors_config={
            "text": SparseVectorParams(index=SparseIndexParams(on_disk=False))
        }
    )
    print(f"  [创建] {name} (dense + sparse)")


print("重建 Qdrant Collections（支持混合检索）...")
collections = {
    "classic_routes": VectorParams(size=384, distance=Distance.COSINE),
    "attractions": VectorParams(size=384, distance=Distance.COSINE),
    "user_plans": VectorParams(size=384, distance=Distance.COSINE),
}
for name, params in collections.items():
    recreate_collection(name, params)


# ============================================================
# 数据导入（dense + sparse vector）
# ============================================================

with open(JSON_PATH, "r", encoding="utf-8") as f:
    items = json.load(f)


def insert_items(collection_name: str, items_list: list):
    sparse_vectors = build_sparse_vectors(items_list)
    points = []
    for item in items_list:
        text = item["embedding_text"]
        dense_vector = model.encode(text).tolist()
        sparse = sparse_vectors.get(item["id"], {"indices": [], "values": []})

        payload = dict(item)
        payload["content"] = text

        # vector 传入 dict，同时包含 dense 和 sparse vectors
        vec = {"": dense_vector}
        if sparse["indices"]:
            vec["text"] = SparseVector(indices=sparse["indices"], values=sparse["values"])

        points.append(PointStruct(
            id=item["id"],
            vector=vec,
            payload=payload
        ))

    if points:
        client.upsert(collection_name=collection_name, points=points)
        print(f"  [导入] {collection_name}: {len(points)} 条")


classic = [i for i in items if i["type"] == "route"]
attractions = [i for i in items if i["type"] == "attraction"]
user_plans = [i for i in items if i["type"] == "user_plan"]

insert_items("classic_routes", classic)
insert_items("attractions", attractions)
insert_items("user_plans", user_plans)

print("\n✅ Qdrant RAG 知识库初始化完成（支持混合检索 BM25）")