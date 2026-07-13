# -*- coding: utf-8 -*-
"""
Final Optimizer 节点 - 最终润色，生成高质量版本

角色：旅行规划大师
输入：最优草案 + 评审意见
输出：最终版行程
"""
from langchain_core.messages import HumanMessage
from app.agents.base import llm
from app.agents.state import AgentState
from app.config.logger import logger


ROLE_DEFINITION = """
## 角色：顶级旅行规划大师

### 核心技能
- 行程润色与精炼
- 文字表达优化
- 细节完善
- 美化呈现格式

### 能力
- 提升文字流畅度
- 增强可读性
- 添加惊喜元素
- 完善实用信息
"""

OUTPUT_STANDARD = """
### 输出规范
1. 完整行程，所有天数都要输出
2. 每个景点包含：时间、地点、推荐理由
3. 交通方式要具体（哪路公交/地铁）
4. 预算分配要清晰
5. Tips 要实用（防坑、预约、穿着等）
"""

QUALITY_CHECKLIST = """
### 质量检查清单（输出前自检）
- [ ] 行程流畅，无逻辑矛盾
- [ ] 预算在指定档位内
- [ ] 节奏符合用户要求
- [ ] 景点类型多样
- [ ] 交通衔接合理
- [ ] 有实用 Tips
- [ ] 无错别字
"""


def final_optimizer_node(state: AgentState):
    """
    Final Optimizer 节点 - 最终润色

    输入：
    - draft_plan：最佳草案
    - critiques：历史评审意见（已基本解决）
    - scores：评分记录
    - task：用户需求

    输出：
    - 最终版行程
    """
    draft = state.get("draft_plan", "")
    critiques = "\n\n".join(state.get("critiques", []))
    scores = state.get("scores", [])
    task = state.get("task")

    # 获取最高分
    best_score = max([s.get("overall_score", 0) for s in scores], default=0)

    logger.info(f"[FinalOptimizer] 开始最终润色 | 最高分: {best_score}")

    prompt = f"""{ROLE_DEFINITION}

## 用户需求
- 目的地：{getattr(task, 'destination', '未知')}
- 天数：{getattr(task, 'days', 0)}天
- 预算：{getattr(task, 'budget', '中等')}
- 节奏：{getattr(task, 'pace', '适中')}
- 核心诉求：{getattr(task, 'user_query', '')}

## 当前最佳草案
{draft}

## 历史评审意见（已基本解决，可忽略）
{critiques}

## 最高评审分数
{best_score}/100

{OUTPUT_STANDARD}

{QUALITY_CHECKLIST}

## 润色要求
1. 保持原有合理设计，不做大幅度修改
2. 文字更流畅、有吸引力
3. 信息更完整、更实用
4. 格式更美观
5. 可增加适量小惊喜元素

## 输出
请输出最终版完整行程规划。"""

    response = llm.invoke([HumanMessage(content=prompt)])

    logger.info(f"[FinalOptimizer] 完成，最终字数: {len(response.content)}")

    return {
        "final_plan": response.content,
        "draft_plan": response.content,
        "next": "parse_plan",
        "should_end": True
    }
