package com.kxh.aiagent.agent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public  abstract class ReActAgent extends BeasAgent{
    @Override
    public String step() {
        try {
            boolean needAct = think();
            if (!needAct) {
                log.info("无需使用工具");
                return "";
            } else {
                return act();
            }
        }catch (Exception e){
            log.error("步骤执行失败："+e.getMessage());
            return "步骤执行失败:"+e.getMessage();
        }
    }


    public abstract boolean  think();


    public abstract String act();
}
