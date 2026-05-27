# pip install -qU langchain "langchain[openai]"
from langchain.agents import create_agent
from langchain_openai import ChatOpenAI
from langchain_core.tools import tool

## 不同模型初始化的依赖
from langchain.chat_models import init_chat_model



#  只需修改这一块的配置
llm = ChatOpenAI(
    base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",      # 1️⃣ 改：指向 DeepSeek 端点
    api_key="sk-9141250581a24874849e0a864e9dff80",          # 2️⃣ 改：换成 DeepSeek API Key
    model="qwen-plus",                       # 3️⃣ 改：换成 DeepSeek 模型名
    temperature=0.7,
)

def get_weather(city: str) -> str:
    """Get weather for a given city."""
    return f"It's always sunny in {city}!"

agent = create_agent(
    model=llm,
    tools=[get_weather],
    system_prompt="You are a helpful assistant",
)

result = agent.invoke(
    {"messages": [{"role": "user", "content": "What's the weather in San Francisco?"}]}
)
print(result["messages"][-1].content_blocks)