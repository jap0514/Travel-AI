# -*- coding: utf-8 -*-
"""
Refiner 节点 - 根据专家评审意见优化行程

角色：行程优化专家
输入：草案 + 评审意见
输出：优化后的完整草案
"""
from langchain_core.messages import HumanMessage
from app.agents.base import llm
from app.agents.state import AgentState
from app.config.logger import logger


ROLE_DEFINITION = """
## 角色：顶级旅行行程优化专家

### 核心技能
- 深度理解专家评审意见
- 大幅改进行程质量
- 保持原有合理设计
- 创新性解决冲突问题

### 优化能力
- 调整景点顺序减少交通
- 平衡每天活动密度
- 优化预算分配
- 增加特色体验
- 保持逻辑连贯性

### 输出规范
- 必须输出完整的新版行程
- 不能只输出修改的部分
- 保持 Markdown 格式
- 字数不少于原草案80%
"""

CONSTRAINTS = """
### 硬性约束（必须遵守）
1. 预算不能超过用户指定档位（{budget}）
2. 天数必须等于 {days} 天
3. 节奏必须符合用户要求（{pace}）
4. 所有景点必须在目的地城市{destination}内
5. 每天必须有吃饭安排

### 软约束（尽量满足）
- 景点类型要多样（文化/自然/美食/休闲）
- 避免重复同质景点
- 减少交通时间和成本
- 增加当地特色体验
"""

FEEDBACK_EXAMPLES = """
### 优化示例

## 示例1：时间太赶
原始："9:00-12:00 博物馆，14:00-18:00 寺庙，19:00-21:00 夜市"
优化："9:00-12:00 博物馆，12:00-13:30 午餐+休息，14:30-17:00 寺庙，19:00-20:30 夜市（推迟1小时开放）"

## 示例2：景点单一
原始："第1天：3个人文景点"
优化："第1天：上午人文景点+下午自然风光+晚上美食体验"

## 示例3：预算超支
原始："住宿：五星酒店1500元/晚"
优化："住宿：商务连锁酒店400元/晚，升级晚餐体验"
"""


def refiner_node(state: AgentState):
    """
    Refiner 节点 - 优化行程草案

    输入：
    - draft_plan：当前草案
    - critiques：历次评审意见
    - task：用户需求

    输出：
    - 优化后的完整草案
    """
    draft = state["draft_plan"]
    critiques = "\n\n".join(state.get("critiques", []))
    task = state["task"]
    iteration = state.get("iteration", 0)

    logger.info(f"[Refiner] 第{iteration}次优化开始")

    prompt = f"""{ROLE_DEFINITION}

{ CONSTRAINTS.format(
    budget=getattr(task, 'budget', '中等'),
    days=getattr(task, 'days', 3),
    pace=getattr(task, 'pace', '适中'),
    destination=getattr(task, 'destination', '')
)}

## 当前草案
{draft}

## 历史评审意见（必须全部解决）
{critiques}

{FEEDBACK_EXAMPLES}

## 优化要求
1. 仔细阅读每条评审意见，必须逐一解决
2. 保持原草案中合理的部分
3. 改进幅度要显著（不是小修小补）
4. 输出完整的新版行程
5. 格式与原草案一致

## 输出
请输出完整的新版行程规划，包含所有天数和细节。"""

    response = llm.invoke([HumanMessage(content=prompt)])

    logger.info(f"[Refiner] 第{iteration}次优化完成，新草案长度: {len(response.content)}")

    return {
        "draft_plan": response.content,
    }
