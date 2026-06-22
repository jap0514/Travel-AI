# Qdrant RAG 混合检索问题排查总结

> 时间：2026/06/19
> 目标：修复 Qdrant RAG 服务的混合检索（向量 + BM25 关键词）功能

---

## 问题一：ModuleNotFoundError: No module named 'app'

### 遇到的问题

运行 `qdrant_rag_server.py` 时报错：

```
ModuleNotFoundError: No module named 'app'
```

### 分析

脚本中使用了包内绝对导入：

```python
from app.config.logger import logger
```

直接以脚本方式运行 `python xxx.py` 时，Python 将脚本所在目录作为顶层，不会去查找 `app` 包。需要以模块方式运行，或在代码中主动添加项目根目录到 `sys.path`。

### 解决

在文件顶部添加路径处理：

```python
import sys
import os

_current = os.path.dirname(os.path.abspath(__file__))
_root = os.path.dirname(os.path.dirname(_current))  # travel_ai_python/
if _root not in sys.path:
    sys.path.insert(0, _root)
```

或者使用 `-m` 方式运行：

```bash
python -m travel_ai_python.app.mcp_servers.qdrant_rag_server
```

### 知识点

- Python 模块导入机制：`sys.path` 决定 import 时的搜索路径
- 绝对导入 vs 相对导入
- 包内模块的 `__name__` 和 `__file__`

---

## 问题二：关键词搜索返回 400 Bad Request

### 遇到的问题

运行测试脚本时，关键词搜索（BM25）一直报错：

```
关键词搜索失败: Unexpected Response: 400 (Bad Request)
Raw response: Expected some form of vector, id, or a type of query
```

### 分析

原代码中直接用字符串作为 query 传给 `qdrant_client.query_points`：

```python
keyword_results = qdrant_client.query_points(
    collection_name=collection,
    query=query,  # ❌ 直接传字符串，不支持
    query_filter=filter_cond,
    limit=rerank_top_k * 2,
    with_payload=True
).points
```

Qdrant 的 `query_points` 方法**不支持直接传入字符串做全文检索**。需要使用 sparse vector 查询 API。

### 解决

两步修复：

1. **修改 `init_qdrant_rag.py`**：重建 collection 时添加 `sparse_vectors_config`，为每条数据生成 BM25 sparse vector
2. **修改 `qdrant_rag_server.py`**：用 `SparseVector` + `Prefetch` + `FusionQuery` 实现关键词检索

### 知识点

- Qdrant 支持三种向量类型：**Dense Vector**（384维）、**Sparse Vector**（BM25）、**Multi-Vector**
- Sparse Vector 适用于全文检索/关键词匹配场景
- Collection 需要配置 `sparse_vectors_config` 才能使用 sparse search

---

## 问题三：pydantic ValidationError - sparse_vector 不接受

### 遇到的问题

初始化脚本重建 collection 后导入数据时报错：

```
pydantic_core._pydantic_core.ValidationError: 1 validation error for PointStruct
sparse_vector
  Extra inputs are not permitted
```

### 分析

原代码尝试在 `PointStruct` 中单独传入 `sparse_vector` 参数：

```python
PointStruct(
    id=item["id"],
    vector=dense_vector,
    payload=payload,
    sparse_vector=sparse  # ❌ PointStruct 不接受此参数
)
```

查看 `PointStruct` 的 `vector` 参数签名，发现它接受 `Dict`，可以同时包含多 个向量：

```python
vector: Dict[str, List[float] | SparseVector | ...]
```

### 解决

将 `sparse_vector` 合并到 `vector` 字典中传入：

```python
vec = {"": dense_vector}  # 默认 dense vector（空字符串 key）
if sparse["indices"]:
    vec["text"] = SparseVector(indices=sparse["indices"], values=sparse["values"])

points.append(PointStruct(
    id=item["id"],
    vector=vec,  # ✅ vector 传入 dict
    payload=payload
))
```

`sparse_vectors_config` 中定义的名称（如 `"text"`）需要与此处 key 一致。

### 知识点

- `PointStruct.vector` 可以是 `List[float]`（单个 dense 向量）或 `Dict[str, ...]`（多个向量）
- Dict 格式：`{"": dense_vector, "text": SparseVector(...)}`，空字符串代表默认向量
- 命名需与 `sparse_vectors_config` 中的 key 对应

---

## 问题四：Prefetch 缺少 query 报错

### 遇到的问题

修改后使用 `Prefetch` 查询时报错：

```
Bad request: A query is needed to merge the prefetches. Can't have prefetches without defining a query.
```

### 分析

`qdrant_client.query_points` 的签名中没有 `sparse_query` 参数：

```python
def query_points(self, collection_name, query, using, prefetch, ...)  # 没有 sparse_query
```

而 `Prefetch` 对象需要配合主 `query` 使用，不能单独存在。

### 解决

使用 `FusionQuery` 作为主 query，配合多个 `Prefetch` 实现融合检索：

```python
from qdrant_client.models import FusionQuery, Prefetch, SparseVector

results = qdrant_client.query_points(
    collection_name=collection,
    query=FusionQuery(fusion='rrf'),  # RRF 融合
    prefetch=[
        Prefetch(query=dense_vector, limit=rerank_top_k * 2),
        Prefetch(
            query=SparseVector(indices=indices, values=values),
            using="text",  # 指定 sparse vector 的名称
            limit=rerank_top_k * 2
        ),
    ],
    query_filter=filter_cond,
    limit=rerank_top_k * 2,
    with_payload=True
).points
```

### 知识点

- Qdrant 的 `FusionQuery` 支持多种融合策略：`rrf`（Reciprocal Rank Fusion）、`dbsf`（Distribution-Based Score Fusion）
- RRF 是 rank-based fusion，不依赖原始分数，对不同评分体系的检索结果融合更鲁棒
- `Prefetch` 用于预取多个候选集，再由主 query 的 fusion 策略合并

---

## 问题五：Python hash() 每次运行结果不同

### 遇到的问题

Sparse vector 查询能执行成功，但**返回空结果**。查询和存储使用的 index 完全不同。

### 分析

排查发现 sparse vector 已正确存储，但用相同 term 计算出的 index 却对不上。

检查 `_compute_query_sparse` 中的代码：

```python
indices = [abs(hash(t)) % 100000 for t, _ in sorted_terms]
```

问题在于 **Python 的 `hash()` 是非确定性的**：
- 每次启动 Python 进程，`hash()` 的结果都不同（受随机种子影响）
- 这导致存储时用某个 hash 值计算 index，查询时用另一个 hash 值计算 index
- 两个 index 永远对不上，所以 sparse search 返回空结果

### 解决

使用确定性的哈希函数 `zlib.crc32()` 替代 `hash()`：

```python
import zlib

# 替换前
indices = [abs(hash(t)) % 100000 for t in tokens]

# 替换后
indices = [zlib.crc32(t.encode()) % 100000 for t in tokens]
```

`zlib.crc32()` 是确定性算法，同一输入永远得到相同输出。

### 知识点

- Python `hash()` 设计用于字典等场景，**故意设置为每次进程不同**（防哈希洪水攻击）
- `zlib.crc32()` 是纯算法哈希，结果确定，适用于需要跨进程/跨会话一致的哈希场景
- 类似场景还可使用 `hashlib.md5()`、`hashlib.sha1()` 等

---

## 问题六：init_qdrant_rag.py 缺少 SparseVectorParams 导入

### 遇到的问题

运行时报错：

```
NameError: name 'SparseVectorParams' is not defined. Did you mean: 'SparseIndexParams'?
```

### 分析

`from qdrant_client.models import` 中只导入了 `SparseIndexParams`，漏了 `SparseVectorParams`：

```python
# 缺少 SparseVectorParams
from qdrant_client.models import VectorParams, Distance, PointStruct, SparseVector, SparseIndexParams
```

### 解决

补充导入：

```python
from qdrant_client.models import VectorParams, Distance, PointStruct, SparseVector, SparseIndexParams, SparseVectorParams
```

### 知识点

- Qdrant 的 sparse vector 配置分为两部分：
  - `SparseVectorParams`：定义 sparse vector 的元数据（名称、配置）
  - `SparseIndexParams`：定义 sparse vector 的索引参数（是否存储在磁盘等）

---

## 总结：Qdrant 混合检索要点

### 完整流程

1. **创建 Collection 时声明 sparse vectors**：
   ```python
   client.create_collection(
       collection_name=name,
       vectors_config=VectorParams(size=384, distance=Distance.COSINE),
       sparse_vectors_config={
           "text": SparseVectorParams(index=SparseIndexParams(on_disk=False))
       }
   )
   ```

2. **导入数据时同时提供 dense 和 sparse 向量**：
   ```python
   vec = {"": dense_vector, "text": SparseVector(indices=indices, values=values)}
   PointStruct(id=id, vector=vec, payload=payload)
   ```

3. **查询时使用 FusionQuery + Prefetch 融合**：
   ```python
   client.query_points(
       collection_name=collection,
       query=FusionQuery(fusion='rrf'),
       prefetch=[
           Prefetch(query=dense_vector, limit=top_k),
           Prefetch(query=SparseVector(indices, values), using="text", limit=top_k),
       ],
       limit=limit,
       with_payload=True
   )
   ```

4. **确保哈希一致性**：存储和查询使用相同的确定性哈希算法（如 `zlib.crc32`）

### 涉及的技术点

| 知识点 | 说明 |
|--------|------|
| Python 模块导入机制 | `sys.path`、绝对导入、相对导入 |
| Qdrant Dense Vector | 用于语义/向量检索，384维 Cosine 距离 |
| Qdrant Sparse Vector | 用于 BM25 全文检索，省内存 |
| BM25 算法 | 经典文本相关性算法，TF-IDF 的改进版 |
| FusionQuery (RRF) | Reciprocal Rank Fusion，融合多个检索结果 |
| Python hash() 不确定性 | 受随机种子影响，跨进程不同 |
| zlib.crc32 | 确定性哈希算法，保证 index 一致性 |
| Pydantic 模型验证 | `PointStruct` 的 `vector` 字段类型约束 |