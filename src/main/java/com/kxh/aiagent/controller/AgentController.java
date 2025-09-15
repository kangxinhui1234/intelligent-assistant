package com.kxh.aiagent.controller;

import com.kxh.aiagent.agent.InvestManus;
import com.kxh.aiagent.agent.KxhManus;
import com.kxh.aiagent.model.AgentState;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/agent")
public class AgentController {

    @Autowired
    KxhManus manus;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private Advisor loveAppRagCloudAdvisor;

    /**
     * 流式调用 Manus 超级智能体
     *
     * @param message
     * @return
     */
    @GetMapping(value = "/manus/chat", produces = "text/event-stream;charset=UTF-8")
    public ResponseEntity<SseEmitter> doChatWithManus(String message) {
        KxhManus manus = new KxhManus(allTools,dashscopeChatModel);
        SseEmitter emitter = manus.runStream(message); // 复用已注入 manus
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "event-stream", java.nio.charset.StandardCharsets.UTF_8));
        headers.setCacheControl("no-cache");
        headers.add("X-Accel-Buffering", "no"); // 部署在反向代理时避免缓冲
        return new ResponseEntity<>(emitter, headers, HttpStatus.OK);
    }


    /**
     * 流式调用 Manus 超级智能体
     * @param message
     * @return
     */
    @GetMapping(value = "/manus/invest", produces = "text/event-stream;charset=UTF-8")
    public ResponseEntity<SseEmitter> doChatWithInvestManus(String message) {
        InvestManus manus = new InvestManus(allTools,dashscopeChatModel,loveAppRagCloudAdvisor);
        SseEmitter emitter = manus.runStream(message); // 复用已注入 manus
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "event-stream", java.nio.charset.StandardCharsets.UTF_8));
        headers.setCacheControl("no-cache");
        headers.add("X-Accel-Buffering", "no"); // 部署在反向代理时避免缓冲
        return new ResponseEntity<>(emitter, headers, HttpStatus.OK);
    }

}
