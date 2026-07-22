package com.travel.interceptor;

import com.travel.common.ResultCode;
import com.travel.common.constant.SignatureConstant;
import com.travel.exception.BusinessException;
import com.travel.util.SignatureUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 签名验证拦截器
 * 专门用于验证 Python 回调接口的签名
 */
@Slf4j
@Component
public class SignatureInterceptor implements HandlerInterceptor {

    @Value("${travel.signature.secret-key-python:travel-secret-key-python}")
    private String secretKeyForPython;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 只拦截 Python 回调接口
        String path = request.getRequestURI();
        if (!path.startsWith("/sendMessageByPython/")) {
            return true;
        }

        // 获取签名相关请求头
        String appId = request.getHeader(SignatureConstant.HEADER_APP_ID);
        String timestamp = request.getHeader(SignatureConstant.HEADER_TIMESTAMP);
        String signature = request.getHeader(SignatureConstant.HEADER_SIGN);

        // 1. 校验请求头完整性
        if (appId == null || appId.isEmpty()) {
            log.warn("签名验证失败：缺少 AppId | path={}", path);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少签名 AppId");
        }
        if (timestamp == null || timestamp.isEmpty()) {
            log.warn("签名验证失败：缺少 Timestamp | path={}", path);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少签名时间戳");
        }
        if (signature == null || signature.isEmpty()) {
            log.warn("签名验证失败：缺少 Sign | path={}", path);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少签名");
        }

        // 2. 验证 AppId（必须是 Python）
        if (!SignatureConstant.APP_ID_PYTHON.equals(appId)) {
            log.warn("签名验证失败：AppId 不匹配 | path={} | appId={}", path, appId);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "签名 AppId 不合法");
        }

        // 3. 验证时间戳（防止重放攻击）
        if (!SignatureUtil.isTimestampValid(timestamp)) {
            log.warn("签名验证失败：时间戳已过期 | path={} | timestamp={}", path, timestamp);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "签名已过期，请重新请求");
        }

        // 4. 获取请求体并验证签名
        String body = getRequestBody(request);
        if (!SignatureUtil.verifySign(secretKeyForPython, timestamp, body, signature)) {
            log.warn("签名验证失败：签名不匹配 | path={}", path);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "签名验证失败");
        }

        log.debug("签名验证通过 | path={}", path);
        return true;
    }

    /**
     * 获取请求体（支持多次读取）
     */
    private String getRequestBody(HttpServletRequest request) {
        try {
            if (request instanceof ContentCachingRequestWrapper wrapper) {
                byte[] content = wrapper.getContentAsByteArray();
                return new String(content, StandardCharsets.UTF_8);
            }
            // 如果不是包装过的请求，读取一次
            byte[] bytes = request.getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取请求体失败", e);
            return "";
        }
    }
}
