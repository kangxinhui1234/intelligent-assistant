package com.example.kch_search_image_mcp_server;

import com.example.kch_search_image_mcp_server.tools.SearchImageTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

@SpringBootApplication
public class KchSearchImageMcpServerApplication {

    public static void main(String[] args) {
            SpringApplication.run(KchSearchImageMcpServerApplication.class, args);

    }

    @Bean
    public ToolCallbackProvider imageSearchTools(SearchImageTool imageSearchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(imageSearchTool)
                .build();
    }
}
