package com.kxh.aiagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

@Slf4j
public class TerminateTool {

    @Tool(description = """  
            Terminate the interaction when the request is met OR if the assistant cannot proceed further with the task.  
            "When you have finished all the tasks, call this tool to end the work.  
            """)
    public String doTerminate() {
        log.info("任务结束");
        return "以上就是我的回答，欢迎您继续使用!";
    }
}
