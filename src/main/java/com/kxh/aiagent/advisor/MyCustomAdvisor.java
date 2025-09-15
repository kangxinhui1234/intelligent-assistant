package com.kxh.aiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.model.MessageAggregator;
import reactor.core.publisher.Flux;

/**
 * 自定义的chat拦截器
 * 、
 * 1.0.0-M3中CallAroundAdvisor 、StreamAroundAdvisor
 * 1.0.0正式版本已经废弃 改用

 CallAroundAdvisor → CallAdvisor, StreamAroundAdvisor → StreamAdvisor,
 CallAroundAdvisorChain → CallAdvisorChain and StreamAroundAdvisorChain → StreamAdvisorChain.

 AdvisedRequest → ChatClientRequest are AdivsedResponse → ChatClientResponse.


 */
@Slf4j
public class MyCustomAdvisor implements CallAdvisor, StreamAdvisor {
    /**
     * 目标方法前置拦截器
     * @param
     */
    public ChatClientRequest before(ChatClientRequest chatClientRequest){
        log.info("AI Request: {}{}", chatClientRequest.prompt().getContents(),chatClientRequest.context());
        return chatClientRequest;
    }
//    @Override
//    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
//        // 1. 处理请求（前置处理）
//        AdvisedRequest modifiedRequest = before(advisedRequest);
//
//        // 2. 调用链中的下一个Advisor
//        AdvisedResponse response = chain.nextAroundCall(modifiedRequest);
//
//        // 3. 处理响应（后置处理）
//        return processResponse(response);
//
//    }

    /**
     * 后置处理器
     * @param response
     * @return
     */
    public ChatClientResponse processResponse(ChatClientResponse response){
        log.info("AI Response: {}", response.chatResponse().getResult().getOutput().getText());
        return response;
    }

    /**
     * 响应式流式编程
     * @param
     * @param
     * @return
     */
   // @Override
//    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
//        // 1. 处理请求
//        AdvisedRequest modifiedRequest = before(advisedRequest);
//
//        // 2. 调用链中的下一个Advisor并处理流式响应
//        Flux<AdvisedResponse> advisedResponses = chain.nextAroundStream(advisedRequest);
//        return (new MessageAggregator()).aggregateAdvisedResponse(advisedResponses, this::processResponse);
//    }

   // SpringAi 1.0.0 版本变成这个了
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
                                                 StreamAdvisorChain streamAdvisorChain) {

        Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);

        return new ChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponses,(a)->{});
    }
    @Override
    public String getName() {
        return "kxh的chat拦截器";
    }

    @Override
    public int getOrder() {
        return 1000; // 值越大 优先级越小
    }


    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        // 1. 处理请求（前置处理）
        ChatClientRequest request = before(chatClientRequest);

        // 2. 调用链中的下一个Advisor
        ChatClientResponse response = callAdvisorChain.nextCall(request);

        // 3. 处理响应（后置处理）
        return processResponse(response);
    }
}
