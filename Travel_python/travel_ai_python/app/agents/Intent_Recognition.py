"""用户消息，传进来。先到这里进行意图识别，如果是知识的一些问答，
比如：景点的介绍、用户历史规划的查询、经典的一些3日游、5日游什么的，就走RAG问答，检索向量数据库返回结果就行
如果是需要旅游的规划，就需要走正常的行程规划的agent流程"""
from app.agents.base import llm
from app.agents.state import AgentState
from app.config.logger import logger
from langchain_core.messages import HumanMessage
from langchain_openai import ChatOpenAI
from app.config.settings import settings
import re


# 专用于意图识别的 LLM，temperature=0 保证稳定分类
intent_llm = ChatOpenAI(
    model=settings.AI_MODEL_NAME,
    base_url=settings.AI_API_URL,
    api_key=settings.AI_API_KEY,
    temperature=0,
)


def intent_recognition_node(state: AgentState):
    user_msg = state["messages"][-1].content
    prompt = f"""你是一个旅行助手意图分类器。严格判断用户消息属于哪种类型。

**判断规则**：
- "plan"（规划）：用户想要制定旅行计划、安排行程，天数、预算、节奏等。例如："帮我规划北京5日游"、"去成都玩3天怎么安排"、"我想做一份旅行计划"、"我想去北京玩两天"
- "qa"（问答）：用户只是单纯询问信息，不涉及制定行程。例如："长城门票多少钱"、"北京天气怎么样"、"请介绍一下故宫"

用户消息：{user_msg}

只输出一个单词：plan 或 qa，不要有其他内容。"""
    response = intent_llm.invoke([HumanMessage(content=prompt)])
    raw = response.content.strip().lower()

    # 从模型返回中提取 plan 或 qa（模型可能夹杂思考过程）
    match = re.search(r'\b(plan|qa)\b', raw)
    intent = match.group(1) if match else "qa"
    logger.info(f"路线：{intent}（原始：{raw[:80]}）")
    return {"intent": intent}
