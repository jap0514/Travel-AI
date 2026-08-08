package com.travel.util;

import cn.hutool.crypto.digest.DigestUtil;
import com.travel.common.constant.SignatureConstant;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * 签名工具类
 */
@Slf4j
public class SignatureUtil {

    private SignatureUtil() {}

    /**
     * 生成签名
     *
     * @param secretKey 密钥
     * @param timestamp 时间戳
     * @param body      请求体
     * @return 签名（32位十六进制字符串）
     */
    public static String generateSign(String secretKey, String timestamp, String body) {
        String data = secretKey + timestamp + (body == null ? "" : body);
        return DigestUtil.sha256Hex(data);
    }

    /**
     * 验证签名
     *
     * @param secretKey     密钥
     * @param timestamp     时间戳
     * @param body          请求体
     * @param signature     待验证的签名
     * @return true=签名正确
     */
    public static boolean verifySign(String secretKey, String timestamp, String body, String signature) {
        String expectedSign = generateSign(secretKey, timestamp, body);
        log.info("期望签名: {}", expectedSign);
        log.info("收到签名: {}", signature);
        log.info("比较结果: {}", expectedSign.equalsIgnoreCase(signature));
        return expectedSign.equalsIgnoreCase(signature);
    }

    /**
     * 检查时间戳是否在有效期内（防止重放攻击）
     *
     * @param timestampStr 时间戳字符串（毫秒）
     * @return true=在有效期内
     */
    public static boolean isTimestampValid(String timestampStr) {
        if (timestampStr == null || timestampStr.isEmpty()) {
            return false;
        }
        try {
            long timestamp = Long.parseLong(timestampStr);
            long now = System.currentTimeMillis();
            long diff = Math.abs(now - timestamp);
            return diff <= SignatureConstant.TIMESTAMP_EXPIRE_MILLIS;
        } catch (NumberFormatException e) {
            log.warn("时间戳格式错误: {}", timestampStr);
            return false;
        }
    }
}
