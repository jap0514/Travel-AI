"""用户消息，传进来。先到这里进行意图识别，如果是知识的一些问答，
    比如：景点的介绍、用户历史规划的查询、经典的一些3日游、5日游什么的，就走RAG问答，检索向量数据库返回结果就行
    如果是需要旅游的规划，就需要走正常的行程规划的agent流程"""
from app.agents.base import llm
from app.agents.state import AgentState
from app.config.logger import logger
from langchain_core.messages import SystemMessage

def intent_recognition_node(state: AgentState):
    user_msg = state["messages"][-1].content
    prompt = f"""判断以下用户消息是否属于“旅行规划请求”（包括制定行程、安排天数、预算、节奏等）。
如果是规划请求，回复 "plan"；如果只是询问景点信息、历史行程、天气、美食等一般知识，回复 "qa"。

用户消息：{user_msg}
只输出一个单词，不要有其他内容。"""
    response = llm.invoke([SystemMessage(content=prompt)])
    intent = response.content.strip().lower()
    if intent not in ["plan", "qa"]:
        intent = "qa"   # 默认走问答
    return {"intent": intent}