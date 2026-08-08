package com.travel.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * 请求体包装过滤器
 * 将 HttpServletRequest 包装为 ContentCachingRequestWrapper，
 * 以支持在拦截器中多次读取请求体（读取后缓存）
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // 在 TraceIdFilter 之后执行
public class SignatureFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            String path = httpRequest.getRequestURI();
            // 只对 Python 回调接口进行包装
            if (path.startsWith("/sendMessageByPython/")) {
                // TraceIdFilter 已经包装过了，直接放行
                //ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
                chain.doFilter(request, response);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
