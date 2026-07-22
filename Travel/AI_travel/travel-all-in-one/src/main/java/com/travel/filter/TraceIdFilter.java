package com.travel.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 为每一次进入后端的 HTTP 请求生成一个唯一的“身份证号”（TraceId），
 * 并让这个号码贯穿整个请求处理流程（包括日志记录和响应返回）
 */

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)  //代表这个过滤器是所有过滤器中第一个执行的
public class TraceIdFilter implements Filter {

  private static final String TRACE_ID = "traceId";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
          throws IOException, ServletException {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      HttpServletResponse httpResponse = (HttpServletResponse) response;

      String traceId = httpRequest.getHeader(TRACE_ID);
      if (traceId == null || traceId.isEmpty()) {
          traceId = UUID.randomUUID().toString().replace("-", "");
      }

      //MDC（Mapped Diagnostic Context） 是 SLF4J 提供的日志上下文容器。
      //一旦放入，你在业务代码里写的所有 log.info("xxx")，只要在日志配置文件中配置了 %X{traceId}，
      //这条日志就会自动带上这个 ID。
      MDC.put(TRACE_ID, traceId);
      httpResponse.setHeader(TRACE_ID, traceId);

      try {
          chain.doFilter(request, response);
      } finally {
          MDC.remove(TRACE_ID);
      }
  }
}