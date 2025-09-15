package com.kxh.aiagent.vectorstore;

import com.kxh.aiagent.tools.*;
import com.kxh.aiagent.tools.financeTool.ValuationQuery;
import com.kxh.aiagent.tools.financeTool.financeIndexQuery;
import com.kxh.aiagent.tools.manager.CompatibleToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class ToolCallBackConfiguration {
    @Autowired
    ToolCallbackProvider toolCallbackProvider;
    @Autowired
    financeIndexQuery financeIndexQuery;
    @Autowired
    ValuationQuery valuationQuery;
    @Bean
    public ToolCallback[] toolCallbacks2(){
/**
 * 1.0.0之后不必再包装
 */
//        List<ToolCallback> listCallBacks = Arrays.stream(toolCallbackProvider.getToolCallbacks()).toList().stream()
//                .map(functionCallback -> {
//                    return new CompatibleToolCallback(functionCallback);
//                })
//                .collect(Collectors.toList());

        ToolCallback[] listCallBacks = toolCallbackProvider.getToolCallbacks();

        DownloadInternetFile downloadInternetFile = new DownloadInternetFile();
        FileOptionTool fileOptionTool = new FileOptionTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        SystemCmdExec systemCmdExec = new SystemCmdExec();
        WebSearchTool webSearchTool = new WebSearchTool();
        WebPageScape webPageScape = new WebPageScape();
        CustomerTools customerTools = new CustomerTools();
        TerminateTool terminateTool = new TerminateTool();
        MarkdownGenerationTool markdownGenerationTool = new MarkdownGenerationTool();
        ToolCallback[] toolCallbacks = ToolCallbacks.from(
                downloadInternetFile,
                fileOptionTool,
                pdfGenerationTool,
                systemCmdExec,
                webSearchTool,
                webPageScape,
                customerTools,
                terminateTool,
             //   financeIndexQuery,
              //  valuationQuery,
                markdownGenerationTool
        );
        // 创建新数组
        ToolCallback[] merged = new ToolCallback[listCallBacks.length + toolCallbacks.length];

        // 复制列表元素
        for (int i = 0; i < listCallBacks.length; i++) {
            merged[i] = listCallBacks[i];
        }

        // 复制数组元素
        System.arraycopy(toolCallbacks, 0, merged, listCallBacks.length, toolCallbacks.length);

        return merged;


    }
}
