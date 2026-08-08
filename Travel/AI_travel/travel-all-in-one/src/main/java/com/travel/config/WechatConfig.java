package com.travel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatConfig {

    /**
     * 小程序 AppID
     */
    private String appid;

    /**
     * 小程序 AppSecret
     */
    private String secret;

    /**
     * 微信登录凭证校验接口
     */
    private static final String JSCODE2SESSION_URL =
        "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

    /**
     * 获取 openid 的 URL
     */
    public String getJscode2sessionUrl(String jsCode) {
        return String.format(JSCODE2SESSION_URL, appid, secret, jsCode);
    }
}
