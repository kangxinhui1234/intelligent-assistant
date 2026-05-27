import os
from langchain.chat_models import init_chat_model

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
    temperature=0.7,
)


response = model.invoke("Why do parrots talk?")