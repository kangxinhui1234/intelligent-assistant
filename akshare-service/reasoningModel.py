import os
import sys
sys.stdout.reconfigure(encoding='utf-8')

import openai
from langchain_openai.chat_models.base import BaseChatOpenAI
from langchain_core.messages import AIMessageChunk
from langchain_core.outputs import ChatResult, ChatGenerationChunk
from typing import Any

from langchain_core.callbacks import UsageMetadataCallbackHandler


class ChatOpenAICompat(BaseChatOpenAI):
    """通用 OpenAI 兼容模型封装，支持 reasoning_content 透传。
    比 ChatOpenAI 多了推理内容提取，比 ChatDeepSeek 少了 DeepSeek 特定的消息格式转换。
    """

    def _create_chat_result(
        self, response: dict | openai.BaseModel, generation_info: dict | None = None,
    ) -> ChatResult:
        result = super()._create_chat_result(response, generation_info)
        if isinstance(response, openai.BaseModel):
            choices = getattr(response, "choices", None)
            if choices and hasattr(choices[0].message, "reasoning_content"):
                result.generations[0].message.additional_kwargs["reasoning_content"] = (
                    choices[0].message.reasoning_content
                )
        return result

    def _convert_chunk_to_generation_chunk(
        self, chunk: dict, default_chunk_class: type, base_generation_info: dict | None,
    ) -> ChatGenerationChunk | None:
        gen_chunk = super()._convert_chunk_to_generation_chunk(
            chunk, default_chunk_class, base_generation_info,
        )
        if (choices := chunk.get("choices")) and gen_chunk:
            rc = choices[0].get("delta", {}).get("reasoning_content")
            if rc is not None and isinstance(gen_chunk.message, AIMessageChunk):
                gen_chunk.message.additional_kwargs["reasoning_content"] = rc
        return gen_chunk


# ====== 测试 ======
os.environ["DASHSCOPE_API_KEY"] = "sk-9141250581a24874849e0a864e9dff80"

model = ChatOpenAICompat(
    model="qwen-plus",
    base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
    api_key=os.getenv("DASHSCOPE_API_KEY"),
    extra_body={"enable_thinking": True},
)
callback = UsageMetadataCallbackHandler() ## token用量
# 非流式
response = model.invoke("Why do parrots have colorful feathers?",
                        config={
                            "run_name": "joke_generation",  # Custom name for this run
                            "tags": ["humor", "demo"],  # Tags for categorization
                            "metadata": {"user_id": "123"},  # Custom metadata
"callbacks": [callback]
                        }
                        )
print("token用量"+callback.usage_metadata)
configurable_model = ChatOpenAICompat(temperature=0) # 可配置模型
configurable_model.invoke(
    "what's your name",
    config={"configurable": {"model": "gpt-5-nano"}},  # Run with GPT-5-Nano
)



# 调试：看 additional_kwargs 里有什么
print("=== DEBUG additional_kwargs ===")
print(response.additional_kwargs)
print("=== DEBUG content_blocks ===")
print([b["type"] for b in response.content_blocks])

reasoning_steps = [b for b in response.content_blocks if b["type"] == "reasoning"]
text_blocks = [b for b in response.content_blocks if b["type"] == "text"]

print("\n=== 推理过程 ===")
for step in reasoning_steps:
    print(step["reasoning"][:300])

print("\n=== 最终回答 ===")
for block in text_blocks:
    print(block["text"][:300])

# 流式
print("\n" + "=" * 60)
print("=== 流式推理 ===")
for chunk in model.stream("1+1等于几?"):
    reasoning_steps = [r for r in chunk.content_blocks if r["type"] == "reasoning"]
    if reasoning_steps:
        print(reasoning_steps[0]["reasoning"], end="", flush=True)
    elif chunk.text:
        print(chunk.text, end="", flush=True)
print()
