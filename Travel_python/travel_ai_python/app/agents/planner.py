# -*- coding: utf-8 -*-
"""
Planner 节点 - 生成旅行行程草案

角色：旅行行程规划专家
输入：用户任务 + 研究报告
输出：draft_plan（完整行程草案）
"""
from langchain_core.messages import SystemMessage
from app.agents.base import llm, get_tools, get_mcp_semaphore
from langchain.agents import create_agent
from app.utils.redis_client import get_user_profile
from app.utils.mem0_client import get_user_memories
from app.config.logger import logger
import json


ROLE_DEFINITION = """
## 角色：顶级旅行规划大师

### 核心技能
- 设计合理的每日行程
- 平衡活动密度和休息
- 优化交通路线
- 控制预算分配

### 能力
- 合理安排时间
- 选择最佳景点顺序
- 预留休息时间
- 处理突发情况
"""

PLANNING_PRINCIPLES = """
### 行程规划原则

#### 节奏控制
- 每天景点不超过4个（大型景点算2个）
- 穿插吃饭和休息时间
- 避免连续高强度活动
- 最后一晚留出收整行李时间

#### 交通安排
- 充分利用公共交通
- 早晚高峰错峰出行
- 相邻景点安排在一起
- 预留机场/车站往返时间

#### 预算分配参考
- 经济：景点门票+餐费为主，住宿选经济型
- 中等：可体验1-2个高端项目
- 宽裕：可选择特色体验，全程舒适优先

#### 体验优化
- 早晚安排不同类型活动
- 美食穿插在景点附近
- 留白时间应对意外
- 增加当地特色体验
"""

OUTPUT_FORMAT = """
### 输出规范

#### 必须包含
1. 每日详细行程（时间/地点/活动）
2. 交通方式（具体班次/线路）
3. 门票价格和预约信息
4. 餐饮推荐（位置+人均）
5. 实用 Tips（防坑/预约/穿着）

#### 格式
- 按天组织，每天一个段落
- 使用 Markdown 标题（## 第X天）
- 重点项目加粗
- 清晰的列表结构

#### 禁止
- 不写模糊的地址（如"市中心"、"淄博市区内的餐厅"）
- 不写不确定的价格
- 不安排时间冲突的活动
- 餐饮必须写具体店名+地址，不能写"当地特色餐厅"

#### 餐饮格式（必须严格遵守）
每餐格式：「餐饮类型」- 具体店名（XX区XX路XX号，人均XX元）
例如：「午餐」- 大漠烤肉（张店区世纪路123号，人均80元）
例如：「晚餐」- 聚斋斋鲁菜馆（张店区美食街8号，人均120元）
"""


async def planner_node(state):
    """
    Planner 节点 - 生成行程草案

    输入：
    - task（任务信息）
    - research_results（研究报告）
    - memories（用户记忆）

    输出：draft_plan（完整行程）
    """
    task = state["task"]
    user_id = state.get("user_id", 0)
    session_id = state.get("session_id", 0)
    tools = await get_tools()
    research = state.get("research_results", "")

    # 获取用户偏好和记忆
    profile = get_user_profile(user_id)
    profile_text = json.dumps(profile, ensure_ascii=False) if profile else "暂无"

    memories = get_user_memories(user_id)
    memories_text = "\n".join([m.get("memory", "") for m in memories]) if memories else "暂无"

    prompt = f"""{ROLE_DEFINITION}

## 用户任务
- 目的地：{getattr(task, 'destination', '')}
- 天数：{getattr(task, 'days', 3)}天
- 预算：{getattr(task, 'budget', '中等')}
- 节奏：{getattr(task, 'pace', '适中')}
- 核心诉求：{getattr(task, 'user_query', '')}

## 用户偏好
{profile_text}

## 用户历史记忆
{memories_text}

## 研究报告（来自 Researcher 节点）
{research}

{PLANNING_PRINCIPLES}

{OUTPUT_FORMAT}

## 输出
请生成完整的 {getattr(task, 'days', 3)} 天旅行行程草案。"""

    logger.info(f"[Planner] 开始规划 {getattr(task, 'destination', '')}{getattr(task, 'days', 0)}天行程")

    agent = create_agent(
        model=llm,
        tools=tools,
        system_prompt=prompt
    )

    # 使用信号量限制 MCP 工具并发调用，避免多请求时 MCP SSE 连接冲突
    mcp_semaphore = get_mcp_semaphore()
    async with mcp_semaphore:
        result = await agent.ainvoke({"messages": []})

    draft = result["messages"][-1].content

    logger.info(f"[Planner] 草案完成，字数: {len(draft)}")

    return {
        "draft_plan": draft,
        "messages": state["messages"] + result.get("messages", []),
        "user_id": user_id,
        "session_id": session_id
    }
