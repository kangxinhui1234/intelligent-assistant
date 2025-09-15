package com.kxh.aiagent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

//@Component
public class OllamaAiInvoke implements CommandLineRunner {
    @Resource
    ChatModel  ollamaChatModel;
    @Override
    public void run(String... args) throws Exception {
        Advisor a;
       AssistantMessage message = ollamaChatModel.call(new Prompt("我是 kxh")).getResult().getOutput();
        System.out.println("ollama:"+message.getText());
    }
}
