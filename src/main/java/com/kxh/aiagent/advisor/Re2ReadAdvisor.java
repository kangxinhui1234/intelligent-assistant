package com.kxh.aiagent.advisor;

import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Re2ReadAdvisor   implements CallAdvisor, StreamAdvisor {

    private final String RED2_PROMPT_TEMPLATE = """
			{re2_input_query}
			Read the question again: {re2_input_query}
			""";
    public ChatClientRequest before(ChatClientRequest advisedRequest){
       // advisedRequest.adviseContext() 上下文 可以存储一些共享的东西
        Map<String, Object> advisedUserParams = new HashMap<>(advisedRequest.context());
        UserMessage userMessage = advisedRequest.prompt().getUserMessage();
        UserMessage re2UserMessage = UserMessage.builder().text(RED2_PROMPT_TEMPLATE
                .replaceAll("\\{re2_input_query\\}",advisedRequest.prompt().getUserMessage().getText())).build();
        List<Message> list = advisedRequest.prompt().getInstructions();
        list.remove(userMessage);
        list.add(re2UserMessage);
        return advisedRequest;
    }
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest advisedRequest, CallAdvisorChain chain) {
        ChatClientRequest request = before(advisedRequest);
        return  chain.nextCall(request);
    }

    @Override
    public String getName() {
        return "我的read2拦截器";
    }

    @Override
    public int getOrder() {
        return 98;
    }

    /**
     * 1.0.0-M3废弃
     * @param chatClientRequest
     * @param streamAdvisorChain
     * @return
     */
  /*  @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        return chain.nextAroundStream(this.before(advisedRequest));    }

*/

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);

        return new ChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponses,(a)->{});

    }
}
