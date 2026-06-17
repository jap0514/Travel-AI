import os
import sys

# 将当前文件所在目录加入 Python 路径
sys.path.insert(0, os.path.dirname(__file__))

import pytest
import json
import time
import asyncio
from datetime import datetime
from qdrant_rag_server import (
    add_knowledge, update_knowledge, get_knowledge_by_id,
    delete_knowledge_by_id, delete_by_filter,
    get_collection_stats, list_collections
)
from qdrant_rag_server import qdrant_client



# 全局变量存储测试用的 point_id
test_point_id = 20260607

@pytest.mark.asyncio
async def test_add_knowledge():
    payload = {
        "name": "测试景点_黄埔军校",
        "city": "广州",
        "history": "1924年创办的军事学校",
        "highlights": "军校旧址、纪念馆",
        "tips": "免费参观，需预约",
        "embedding_text": "黄埔军校是广州重要的近代史遗址，培养了许多军事人才。"
    }
    result = add_knowledge(
        knowledge_type="attraction",
        payload=payload,
        point_id=test_point_id
    )
    assert "成功添加" in result
    # 验证时间字段存在
    point = qdrant_client.retrieve("attractions", [test_point_id], with_payload=True)[0]
    assert "create_time" in point.payload
    assert "update_time" in point.payload

@pytest.mark.asyncio
async def test_update_knowledge():
    orig = qdrant_client.retrieve("attractions", [test_point_id], with_payload=True)[0].payload
    old_update = orig.get("update_time")
    await asyncio.sleep(1)
    update_payload = {
        "tips": "新贴士：周一闭馆，建议上午参观",
        "embedding_text": "黄埔军校是广州重要的近代史遗址，培养了许多军事人才。新增说明。"
    }
    result = update_knowledge("attraction", test_point_id, update_payload)
    assert "成功更新" in result
    new = qdrant_client.retrieve("attractions", [test_point_id], with_payload=True)[0].payload
    new_update = new.get("update_time")
    assert new_update != old_update

@pytest.mark.asyncio
async def test_get_knowledge_by_id():
    result = get_knowledge_by_id("attraction", test_point_id)
    data = json.loads(result)
    assert data["name"] == "测试景点_黄埔军校"

@pytest.mark.asyncio
async def test_delete_single():
    result = delete_knowledge_by_id("attraction", test_point_id)
    assert "成功删除" in result
    # 确认已删除
    points = qdrant_client.retrieve("attractions", [test_point_id], with_payload=True)
    assert len(points) == 0

@pytest.mark.asyncio
async def test_delete_by_filter():
    # 添加两条临时数据
    for i in range(2):
        add_knowledge(
            "attraction",
            {"name": f"临时景点{i}", "city": "测试城市", "embedding_text": "测试"},
            point_id=i
        )
    result = delete_by_filter("attraction", city="测试城市")
    assert "已删除 2 条" in result
    # 验证删除干净
    scroll = qdrant_client.scroll("attractions", scroll_filter=None, limit=10)[0]
    temp_ids = [p.id for p in scroll if str(p.id).startswith("temp_")]
    assert len(temp_ids) == 0

@pytest.mark.asyncio
async def test_management_apis():
    stats = get_collection_stats("attraction")
    assert "向量数量" in stats
    cols = list_collections()
    assert "attractions" in cols