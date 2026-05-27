import os
from langchain.chat_models import init_chat_model

from langchain.messages import HumanMessage, AIMessage, SystemMessage

from tools import  get_weather
from structure import Movie

## 提供不同类型的消息
conversationTypes = [
    SystemMessage("You are a helpful assistant that translates English to French."),
    HumanMessage("Translate: I love programming."),
    AIMessage("J'adore la programmation."),
    HumanMessage("Translate: I love building applications.")
]





os.environ["DASHSCOPE_API_KEY"] = "sk-9141250581a24874849e0a864e9dff80"

#model = init_chat_model("gpt-5.4")


# 2. 使用统一接口初始化通义千问 [citation:4]
# 确保环境变量 'DASHSCOPE_API_KEY' 已设置
# 通义千问兼容OpenAI的API规范，所以 model_provider 指定为 'openai'
# 同时需要手动指定 base_url 为阿里云百炼平台的兼容地址
model = init_chat_model(
    model="qwen-plus", # 或 "qwen-plus", "qwen-turbo"
    model_provider="openai",
    base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
    api_key=os.getenv("DASHSCOPE_API_KEY"),
    max_retries=10,
    temperature=0.7,
)

# 模型的高级属性 个性化配置
model.profile
# {
#   "max_input_tokens": 400000,
#   "image_inputs": True,
#   "reasoning_output": True,
#   "tool_calling": True,
#   ...
# }


conversation = [
    {"role": "system", "content": "You are a helpful assistant that translates English to French."},
    {"role": "user", "content": "Translate: I love programming."},
    {"role": "assistant", "content": "J'adore la programmation."},
    {"role": "user", "content": "Translate: I love building applications."}
]
model.bind_tools([get_weather])
model.with_structured_output(Movie)
 ## 流输出
for chunk in model.stream("Provide details about the movie Inception"):
    print(chunk.text, end="|", flush=True)


## 提供消息列表
response = model.invoke(conversation)

print(response)  # AIMessage("J'adore créer des applications.")

# response = model.invoke("Why do parrots talk?")


# response = model.invoke(conversationTypes)
print(response)  # AIMessage("J'adore créer des applications.")
