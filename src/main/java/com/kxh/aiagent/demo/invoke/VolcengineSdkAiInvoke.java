package com.kxh.aiagent.demo.invoke;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;

import java.util.ArrayList;
import java.util.List;

public class VolcengineSdkAiInvoke {
    public static void main(String[] args) {
        // 从环境变量中获取API密钥
        String apiKey = "dc9f9a16-95ca-4764-8aa8-4895bb691a58";

        // 创建ArkService实例
        ArkService arkService = ArkService.builder().apiKey(apiKey).build();

        // 初始化消息列表
        List<ChatMessage> chatMessages = new ArrayList<>();

        // 创建用户消息
        ChatMessage userMessage = ChatMessage.builder()
                .role(ChatMessageRole.USER) // 设置消息角色为用户
                .content("hello:") // 设置消息内容
                .build();

        // 将用户消息添加到消息列表
        chatMessages.add(userMessage);

        // 创建聊天完成请求
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model("doubao-seed-1-6-vision-250815")// 按需替换Model ID
                .messages(chatMessages) // 设置消息列表
                .build();

        // 发送聊天完成请求并打印响应
        try {
            // 获取响应并打印每个选择的消息内容
            arkService.createChatCompletion(chatCompletionRequest)
                    .getChoices()
                    .forEach(choice -> System.out.println("volc：" + choice.getMessage().getContent()));
        } catch (Exception e) {
            System.out.println("请求失败: " + e.getMessage());
        } finally {
            // 关闭服务执行器
            arkService.shutdownExecutor();
        }
    }
}
