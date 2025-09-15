package com.kxh.aiagent.demo.invoke;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

//@Component
public class LangChain4jAiInvoke implements CommandLineRunner {


    @Value("${spring.ai.dashscope.api-key}")
    String apiKey;
    @Override
    public void run(String... args) throws Exception {

        QwenChatModel qwenModel = QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-max")
                .build();



        String answer = qwenModel.chat("Say 'Hello World'");
        System.out.println("langchain4j:"+answer); // Hello World
    }
}
