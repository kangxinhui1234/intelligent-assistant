package com.kxh.aiagent.tools;


import cn.hutool.http.HttpUtil;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.FactoryBean;

import java.io.File;
import java.net.HttpURLConnection;

/**
 * 根据给定url 下载文件
 */
public class DownloadInternetFile {
    @Tool(description = "download file from given url")
    public String downloadFile(@ToolParam(description = "http url of download file") String url, @ToolParam(description = "name of download file")String name, ToolContext context){
        String savepath = System.getProperty("user.dir")+"/tmp/download/"+name;

        try {
            HttpUtil.downloadFile(url, savepath);
            return "file download suacess,filepath:"+savepath;
        }catch (Exception e){
            return "download occur some error:"+e.getMessage();
        }


    }
}
