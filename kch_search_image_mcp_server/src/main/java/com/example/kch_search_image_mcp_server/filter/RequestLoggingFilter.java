package com.example.kch_search_image_mcp_server.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)throws IOException, ServletException
             {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        long startTime = System.currentTimeMillis();

        // 1. 前置处理（请求到达前）
        log.info("拦截请求: " + httpRequest.getRequestURI());
                 log.info("请求方法: " + httpRequest.getMethod());
                 log.info("客户端IP: " + httpRequest.getRemoteAddr());

        // 2. 继续处理请求
        chain.doFilter(request, response);

        // 3. 后置处理（响应返回前）
        long duration = System.currentTimeMillis() - startTime;
                 log.info("请求处理耗时: " + duration + "ms");
    }

}