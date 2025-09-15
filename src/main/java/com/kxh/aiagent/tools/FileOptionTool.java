package com.kxh.aiagent.tools;


import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;

/**
 * 文件操作工具
 */
@Slf4j
public class FileOptionTool {
    private String filePath = "/tmp/file-store/";

    @Tool(description = "create local file by given content" ,returnDirect = true)
    public String saveFile(@ToolParam(description = "content of save into file")String content, @ToolParam(description = "filename of created new file")String fileName, ToolContext context){
        log.info("context:"+context.getContext().toString());
        try {
            File file = FileUtil.writeUtf8String(content, System.getProperty("user.dir") + filePath + fileName);
            return "create local file suacess,filePath:"+file.getAbsolutePath();
        }catch (Exception e){
            log.error("call ai-tool saveFile failed:"+e.getMessage());
            return "create local file failed";
        }

    }

    @Tool(description = "get file content by given filepath")
    public String getFileContent(@ToolParam(description = "filePath")String fileName){
        try {
            String content = FileUtil.readUtf8String(System.getProperty("user.dir") + filePath + fileName);
            return content;
        }catch (Exception e){
            log.error("get file content failed:"+e.getMessage());
            return "get file content failed";
        }

    }
}
