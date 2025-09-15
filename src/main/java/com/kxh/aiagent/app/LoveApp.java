package com.kxh.aiagent.app;


import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.OverAllStateFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.node.QuestionClassifierNode;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.kxh.aiagent.advisor.MyCustomAdvisor;
import com.kxh.aiagent.advisor.Re2ReadAdvisor;
import com.kxh.aiagent.chatmemory.FileBasedChatMemory;
import com.kxh.aiagent.chatmemory.RedisBasedChatMemory;
import com.kxh.aiagent.enums.SYSTEM_PROMPT_TYPE;
import com.kxh.aiagent.graph.RecordingNode;
import com.kxh.aiagent.tools.CustomerTools;
import com.kxh.aiagent.tools.manager.CompatibleToolCallback;
import com.kxh.aiagent.vectorstore.MySimpleVectorStore;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
@Component
@Slf4j
public class LoveApp {

    @Value("${chat-system-type}:0")
    private String systemType;

    @Value("classpath:template-prompt/system-template-doctor.txt")
    private Resource dockerResource;

    @Value("classpath:template-prompt/system-template-love.txt")
    private Resource loveResource;

    private String systemTemplate;
    private  ChatClient chatClient;

    private ChatModel chatModel;

    @Autowired
    VectorStore simpleVectorStore;
    @Autowired
    ToolCallback[] toolCallbacks2; // 调用工具

   @Autowired
    RedisBasedChatMemory redisBasedChatMemory;

   @Autowired
    Advisor loveAppRagCloudAdvisor;
    @Autowired
    Advisor pgSqlRetriverAdvisor; // pgSql检索器


    @Autowired
    ToolCallbackProvider toolCallbackProvider;


    /**
     * 初始化AI客户端
     * @param
     */
    public LoveApp(ChatModel dashscopeChatModel){
        chatModel = dashscopeChatModel;
    }

    @PostConstruct
    public void initBean(){
        SystemPromptTemplate template = new SystemPromptTemplate(loveResource);
        Map map = new HashMap();
        map.put("name","李福");
        map.put("years","3");
        template.render(map);
        systemTemplate = template.getTemplate();
        // 基于内存的对话记忆
       // ChatMemory chatMemory = new InMemoryChatMemory();
        String filePath = System.getProperty("user.dir") + "/tmp";
        FileBasedChatMemory fileBasedChatMemory = new FileBasedChatMemory(filePath,10);
        //RedisBasedChatMemory redisBasedChatMemory = new RedisBasedChatMemory();
        chatClient = ChatClient.builder(chatModel)
                //.defaultSystem(systemTemplate)
                .defaultAdvisors( MessageChatMemoryAdvisor.builder(redisBasedChatMemory).build(), // 记忆拦截器
                        new MyCustomAdvisor()
                        ,new Re2ReadAdvisor())
                .build();
    }

    /**
     * 多轮对话方法
     */
    public String doChat(String charMsg,String chatId){
        ChatResponse responce = chatClient.prompt()
                .user(charMsg)
//                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId) // 会话id
//                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10)) //关联历史会话的条数
                .call()
                .chatResponse();
        String msg = responce.getResult().getOutput().getText();
        return msg;
    }



    /**
     * jdk21新版本定义实体类方法
     * @param name
     * @param suggestions
     */
    record LoveReport(String name, List<String> suggestions){

    }

    /**
     * 生成恋爱报告，返回实体类，实体类包括 姓名，恋爱报告指南列表
     */
    public LoveReport generateLoveReport(String charMsg,String chatId){
        PromptTemplate e;
         LoveReport report = chatClient.prompt()
                 .user(charMsg)
                 .system(systemTemplate + "每次对话后都要生成恋爱结果报告，标题为{姓名}的报告,内容为建议列表")
                 .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                 /**
                  * 1.0.0废弃
                  */
                // .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId) // 会话id
                //         .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10)) //关联历史会话的条数
                 .call()
                 .entity(LoveReport.class);

         return report;
    }



    /**
     * 通过向量数据库来实现检索增强对话
     */
    public String generateInvestAdviceByQuestionAnswer(String charMsg,String chatId){

        ChatResponse responce = chatClient.prompt()
                .user(charMsg)
                .system("你是理财投资专家，你会根据知识库中的知识回答用户投资问题")
//                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId) // 会话id
//                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10)) //关联历史会话的条数
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))

                .advisors(new QuestionAnswerAdvisor(simpleVectorStore))
                .advisors(spec -> spec.param(QuestionAnswerAdvisor.FILTER_EXPRESSION,"type =='web'"))
                .call()
                .chatResponse();
        String msg = responce.getResult().getOutput().getText();
        return msg;
    }


    /**
     * 通过向量数据库来实现RAG对话
     */
    public String generateInvestAdviceByRAG(String charMsg,String chatId){

        ChatResponse responce = chatClient.prompt()
                .user(charMsg)
                .system("你是理财投资专家，你会根据知识库中的知识回答用户投资问题")
//                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId) // 会话id
//                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10)) //关联历史会话的条数
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))

                .advisors(pgSqlRetriverAdvisor)
                .call()
                .chatResponse();
        String msg = responce.getResult().getOutput().getText();
        return msg;
    }


    /**
     * 结合工具调用功能
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId) {

//        ChatOptions chatOptions = ToolCallingChatOptions.builder()
//                .toolCallbacks(ToolCallbacks.from(toolCallbacks))
//                .internalToolExecutionEnabled(false)  // 禁用内部工具执行
//                .build();
        /**
         * 1.0.0版本新增withInternalToolExecutionEnabled
         */
        DashScopeChatOptions chatOptions = DashScopeChatOptions.builder().withInternalToolExecutionEnabled(false).build();
      // 创建工具调用管理器
        ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();
       Prompt prompt = new Prompt(message,chatOptions);
        ChatResponse responce = chatClient.prompt()
                .user(message)
                .options(chatOptions)
//                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId) // 会话id
//                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10)) //关联历史会话的条数
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))

                .advisors(loveAppRagCloudAdvisor)
                .tools(toolCallbacks2)
                .toolContext(Map.of("girl_name","小美"))
                .call()
                .chatResponse();

        // 手动处理工具调用循环
        while (responce.hasToolCalls()) {
            // 执行工具调用
            AssistantMessage assistantMessage = responce.getResult().getOutput();

            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, responce);
            // 创建包含工具结果的新提示
            prompt = new Prompt(toolExecutionResult.conversationHistory(), chatOptions);
            // 再次发送请求给模型
            responce = chatModel.call(prompt);
        }
        String msg = responce.getResult().getOutput().getText();
        return msg;
    }


    /**
     * 调用mcp服务
     */

    public String doChatWithMcp(String message,String chatId){
            DashScopeChatOptions chatOptions = DashScopeChatOptions.builder().withInternalToolExecutionEnabled(false).build();
            // 创建工具调用管理器
            ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();
        /**
         * spring AI 1.0.0 版本全部替换为ToolCallBack类型工具 mcp 不需要兼容了
         */
//            List<ToolCallback> listCallBacks = Arrays.stream(toolCallbackProvider.getToolCallbacks()).toList().stream()
//                    .map(functionCallback -> {
//                        return new CompatibleToolCallback(functionCallback);
//                    })
//                    .collect(Collectors.toList());

            Prompt prompt = new Prompt(message,chatOptions);
            ChatResponse responce = chatClient.prompt()
                    .user(message)
                    .options(chatOptions)
//                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId) // 会话id
//                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10)) //关联历史会话的条数
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                    //  .advisors(loveAppRagCloudAdvisor)
                   // .tools(toolCallbackProvider.getToolCallbacks())
                    .toolCallbacks(toolCallbacks2)
                  .toolContext(Map.of("girl_name","小美"))
                    .call()
                    .chatResponse();

            // 手动处理工具调用循环
            while (responce.hasToolCalls()) {
                ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, responce);
                // 创建包含工具结果的新提示
                prompt = new Prompt(toolExecutionResult.conversationHistory(), chatOptions);
                // 再次发送请求给模型
                responce =  chatClient.prompt().toolCallbacks(toolCallbacks2).call().chatResponse();
            }
            String msg = responce.getResult().getOutput().getText();
            log.info(msg);
            return msg;
        }

    /**
     * agent graph
     */

    public String doChatWithGraph(String msg) throws GraphStateException {
        OverAllStateFactory stateFactory = () -> {
            OverAllState state = new OverAllState();
            state.registerKeyAndStrategy("input", new ReplaceStrategy());
            state.registerKeyAndStrategy("classifier_output", new ReplaceStrategy());
            state.registerKeyAndStrategy("solution", new ReplaceStrategy());
            return state;
        };


        // Feedback positive/negative classification node
        QuestionClassifierNode feedbackClassifier = QuestionClassifierNode.builder()
                .chatClient(chatClient)
                .inputTextKey("input")
                .categories(List.of("positive feedback", "negative feedback"))
                .classificationInstructions(
                        List.of("Try to understand the user's feeling when he/she is giving the feedback."))
                .build();
// Negative feedback specific question classification node
        QuestionClassifierNode specificQuestionClassifier = QuestionClassifierNode.builder()
                .chatClient(chatClient)
                .inputTextKey("input")
                .categories(List.of("after-sale service", "transportation", "product quality", "others"))
                .classificationInstructions(List.of(
                        "What kind of service or help the customer is trying to get from us? " +
                                "Classify the question based on your understanding."))
                .build();


        // Node for recording results
        RecordingNode recorderNode = new RecordingNode();

//        StateGraph graph = new StateGraph("Consumer Service Workflow Demo", stateFactory)
//                .addNode("feedback_classifier", node_async(feedbackClassifier))
//                .addNode("specific_question_classifier", node_async(specificQuestionClassifier))
//                .addNode("recorder", node_async(recorderNode))
//                // Define edges (workflow sequence)
//                .addEdge(START, "feedback_classifier")  // Start node
//                .addConditionalEdges("feedback_classifier",
//                        edge_async(new CustomerServiceController.FeedbackQuestionDispatcher()),
//                        Map.of("positive", "recorder", "negative", "specific_question_classifier"))
//                .addConditionalEdges("specific_question_classifier",
//                        edge_async(new CustomerServiceController.SpecificQuestionDispatcher()),
//                        Map.of("after-sale", "recorder", "transportation", "recorder",
//                                "quality", "recorder", "others", "recorder"))
//                .addEdge("recorder", END);  // End node
        return "";
    }

}
