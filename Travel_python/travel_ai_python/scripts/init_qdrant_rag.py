import json
from sentence_transformers import SentenceTransformer
from qdrant_client import QdrantClient
from qdrant_client.models import VectorParams, Distance, PointStruct

# ---------- 配置 ----------
QDRANT_HOST = "192.168.71.140"
QDRANT_PORT = 6333
EMBEDDING_MODEL = "paraphrase-multilingual-MiniLM-L12-v2"
JSON_PATH = r"D:\毕业设计\Travel_python\travel_ai_python\scripts\data\travel_knowledge2.json"

# 初始化
model = SentenceTransformer(EMBEDDING_MODEL)
client = QdrantClient(host=QDRANT_HOST, port=QDRANT_PORT)

# 定义三个 collection 及其向量维度（该模型输出384维）
collections = {
    "classic_routes": VectorParams(size=384, distance=Distance.COSINE),
    "attractions": VectorParams(size=384, distance=Distance.COSINE),
    "user_plans": VectorParams(size=384, distance=Distance.COSINE),
}

# 创建 collection（如果不存在）
for name, params in collections.items():
    try:
        client.create_collection(collection_name=name, vectors_config=params)
        print(f"创建 collection: {name}")
    except Exception as e:
        print(f"{name} 已存在，跳过创建")

# 读取数据
with open(JSON_PATH, "r", encoding="utf-8") as f:
    items = json.load(f)

# 按类型分组插入
def insert_items(collection_name, items_list):
    points = []
    for item in items_list:
        text = item["embedding_text"]
        vector = model.encode(text).tolist()
        point_id = item["id"]
        payload = dict(item)  # 保留所有原始字段
        payload["content"] = text  # 增加标准 content 字段（供评估和检索使用）
        points.append(PointStruct(id=point_id, vector=vector, payload=payload))
    if points:
        client.upsert(collection_name=collection_name, points=points)
        print(f"插入 {len(points)} 条到 {collection_name}")

# 分组
classic = [i for i in items if i["type"] == "route"]
attractions = [i for i in items if i["type"] == "attraction"]
user_plans = [i for i in items if i["type"] == "user_plan"]

insert_items("classic_routes", classic)
insert_items("attractions", attractions)
insert_items("user_plans", user_plans)

print("✅ Qdrant RAG 知识库初始化完成")