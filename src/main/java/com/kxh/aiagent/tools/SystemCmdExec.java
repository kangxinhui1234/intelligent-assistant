package com.kxh.aiagent.tools;


import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class SystemCmdExec {
    @Tool(description = "execute local system command by given command")
    public String systemCmsExec(@ToolParam(description = "command of exe on local syatem") String command){
        ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
        try {
            // 启动进程
            Process process = processBuilder.start();

            // 读取命令输出
            InputStream inputStream = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            // 等待命令执行完成
            int exitCode = process.waitFor();
            System.out.println("\nExit Code: " + exitCode);
            return builder.toString();

        } catch ( InterruptedException | IOException e) {
            e.printStackTrace();
            return "exe system command failed:"+e.getMessage();
        }



    }
}
