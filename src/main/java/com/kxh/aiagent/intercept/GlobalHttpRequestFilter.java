package com.kxh.aiagent.intercept;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

//@Component
public class GlobalHttpRequestFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // 只处理 HTTP 请求
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            // 添加自定义响应头（入站）
            httpResponse.addHeader("X-Response-From", "my-spring-app");

            // 包装请求以添加出站头
            chain.doFilter(new HttpServletRequestWrapper(httpRequest) {
                @Override
                public String getHeader(String name) {
                    // 添加自定义请求头
                    if ("X-Request-Source".equals(name)) {
                        return "spring-boot-app";
                    }
                    return super.getHeader(name);
                }

                @Override
                public Enumeration<String> getHeaders(String name) {
                    // 添加自定义请求头
                    if ("X-Request-Source".equals(name)) {
                        return Collections.enumeration(Collections.singletonList("spring-boot-app"));
                    }
                    return super.getHeaders(name);
                }

                @Override
                public Enumeration<String> getHeaderNames() {
                    // 添加自定义请求头名称
                    List<String> names = Collections.list(super.getHeaderNames());
                    names.add("X-Request-Source");
                    return Collections.enumeration(names);
                }
            }, response);
        } else {
            chain.doFilter(request, response);
        }
    }
}