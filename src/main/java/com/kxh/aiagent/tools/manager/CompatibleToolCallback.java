package com.kxh.aiagent.tools.manager;


import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;


/**
 * 包装工具返回数据
 */
public class CompatibleToolCallback implements ToolCallback {

    private final ToolCallback delegate;

    public CompatibleToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        // 确保始终返回有效的工具定义
        // if (delegate instanceof ToolCallback) {
        return ((ToolCallback) delegate).getToolDefinition();
//        } else if (delegate instanceof SyncMcpToolCallback) {
//            // 为 MCP 工具创建定义
//            SyncMcpToolCallback mcpTool = (SyncMcpToolCallback) delegate;
//            return ToolDefinition.builder()
//                    .name(mcpTool.getName())
//                    .description(mcpTool.getDescription())
//                    .inputSchema(mcpTool.getInputTypeSchema())
//                    .build();
//        } else {
//            // 为普通 FunctionCallback 创建定义
//            return ToolDefinition.builder()
//                    .name(delegate.getName())
//                    .description(delegate.getDescription())
//                    .inputSchema(delegate.getInputTypeSchema())
//                    .build();
//        }
        //}
    }

    @Override
    public String call(String toolInput) {
        return null;
    }

    /**
         * 1.0.0正式版本  不用FunctionCallBack了
         */
//    @Override
//    public String getName() {
//        return this.delegate.getName();
//    }
//
//    @Override
//    public String getDescription() {
//        return this.delegate.getDescription();
//    }
//
//    @Override
//    public String getInputTypeSchema() {
//        return this.delegate.getInputTypeSchema();
//    }
//
//    @Override
//    public String call(String toolInput) {
//        return delegate.call(toolInput, (ToolContext)null);
//    }
//
    public String call(String request, ToolContext context) {
        // 如果是 MCP 回调，不传 context
        if (delegate instanceof SyncMcpToolCallback) {
            return delegate.call(request);
        } else {
            return delegate.call(request, context);
        }
    }


}