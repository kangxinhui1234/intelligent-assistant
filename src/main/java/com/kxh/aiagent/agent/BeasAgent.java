package com.kxh.aiagent.agent;

import com.kxh.aiagent.model.AgentState;
import com.kxh.aiagent.tools.SystemCmdExec;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.internal.StringUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * agent任务执行模板 定义了agent的执行过程和实例状态
 */
@Slf4j
@Data
public abstract class BeasAgent {
    private String name; // 智能体名字
    /**
     * 消息重复上限
     */
    public final int DUMPLICATE_NUMS = 3;

    private AgentState state;
    /**
     * 当前的执行步骤
     */
    private int currentStep;
    /**
     * 最大执行步骤
     */
    private int maxStep;

    private  String SYSTEM_PROMPT;

    private String nextPrompt;

    private ChatClient chatClient;

    // 自主维护上下文？ 为什么
    private List<Message> messageList = new ArrayList<>();


    /**
     * 运行代理（流式输出）
     *
     * @param userPrompt 用户提示词
     * @return SseEmitter实例
     */
    public SseEmitter runStream(String userPrompt) {
        // 创建SseEmitter，设置较长的超时时间
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        // 使用线程异步处理，避免阻塞主线程
        CompletableFuture.runAsync(() -> {
            try {
                if (this.state != AgentState.IDLE) {
                    emitter.send("错误：无法从状态运行代理: " + this.state);
                    emitter.complete();
                    return;
                }
                if (StringUtil.isBlank(userPrompt)) {
                    emitter.send("错误：不能使用空提示词运行代理");
                    emitter.complete();
                    return;
                }

                // 更改状态
                state = AgentState.RUNNING;
                // 记录消息上下文
                messageList.add(new UserMessage(userPrompt));

                try {
                    for (int i = 0; i < maxStep && state != AgentState.FINISHED; i++) {
                        int stepNumber = i + 1;
                        currentStep = stepNumber;
                        log.info("Executing step " + stepNumber + "/" + maxStep);

                        // 单步执行
                        String stepResult = step();
                        String result = "Step " + stepNumber + ": " + stepResult;
                            // 发送每一步的结果
                            emitter.send(result);
                    }
                    // 检查是否超出步骤限制
                    if (currentStep >= maxStep) {
                        state = AgentState.FINISHED;
                        emitter.send("执行结束: 达到最大步骤 (" + maxStep + ")");
                    }
                    // 正常完成
                    emitter.complete();
                } catch (Exception e) {
                    state = AgentState.ERROR;
                    log.error("执行智能体失败", e);
                    try {
                        emitter.send("执行错误: " + e.getMessage());
                        emitter.complete();
                    } catch (Exception ex) {
                        emitter.completeWithError(ex);
                    }
                } finally {
                    // 清理资源
                    this.cleanUp();
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        // 设置超时和完成回调
        emitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanUp();
            log.warn("SSE connection timed out");
        });

        emitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanUp();
            log.info("SSE connection completed");
        });

        return emitter;
    }




    /**
     * 运行代理方法
     */
    public String  run(String userPrompt){
        if(!AgentState.IDLE.equals(this.state)){
            throw new RuntimeException("can not run agent for state:"+this.state);
        }
        if(StringUtils.isEmpty(userPrompt)){
            throw new RuntimeException("can not run agent userPrompt is null:");
        }



        state = AgentState.RUNNING;

        messageList.add(new UserMessage(userPrompt));

        List<String> results = new ArrayList<>();
        try {

            for (int i = 0; i < maxStep && this.state != AgentState.FINISHED; i++) {
                currentStep++;
                log.info("run current step:" + currentStep, ",最大step:" + maxStep);

                String stepResult = this.step();
                results.add("finish current step:" +currentStep+",执行结果：" + stepResult);
            }
            if (currentStep >= maxStep) {
                this.state = AgentState.FINISHED;
                results.add("Terminated: Reached max step:" + maxStep);
            }
            log.info("执行结果："+String.join("\n", results));
            return String.join("\n", results);

        }catch(Exception e){
            state = AgentState.ERROR;
            log.error("occur error during runing: "+e.getMessage());
            return "执行错误："+ e.getMessage();
        }finally {
            this.cleanUp();
        }



    }

    /**
     * 执行单个步骤
     * @return
     */
     public abstract String step();

    /**
     * 清理资源  子类可以重写
     */
    protected void cleanUp(){

    }

    /**
     * 死循环检测
     */
    protected boolean isStrunk(){
        if(messageList.size() <=2){
            return false;
        }
        int dumplicate = 0;
        Message message = messageList.get(messageList.size()-1);
        for(int i = 0;i < messageList.size() -2;i++){
           if(message instanceof AssistantMessage
                   &&message.getText().equals(messageList.get(i).getText())){
               // 助手消息重复
               dumplicate++;

               if(dumplicate >= DUMPLICATE_NUMS){
                   return true;
               }
           }
        }

        return false;
    }

    /**
     * 处理死循环
     */
    public void handleStrunkState(){
      String strunkPrompt = "检测到消息重复，请采用合适策略，避免消息重复";
      this.nextPrompt = strunkPrompt + "\n"+this.nextPrompt;
      log.info("检测到消息循环超过"+this.DUMPLICATE_NUMS +"次，发出提示给模型");
    }

}
