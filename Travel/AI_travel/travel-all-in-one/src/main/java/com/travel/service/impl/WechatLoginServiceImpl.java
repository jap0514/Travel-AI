package com.travel.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.config.WechatConfig;
import com.travel.entity.User;
import com.travel.service.UserService;
import com.travel.service.WechatLoginService;
import com.travel.util.JwtUtil;
import com.travel.vo.LoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class WechatLoginServiceImpl implements WechatLoginService {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private WechatConfig wechatConfig;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public LoginVO login(String code) {
        // 1. 用 code 换取 openid
        String openid = getOpenidFromWechat(code);

        if (openid == null || openid.isEmpty()) {
            throw new RuntimeException("获取openid失败");
        }

        log.info("获取到 openid: {}", openid);

        // 2. 用 openid 查用户，没有则创建
        User user = userService.findByOpenid(openid);
        if (user == null) {
            log.info("新用户注册，openid: {}", openid);
            user = new User();
            user.setOpen_id(openid);
            user.setNickname("用户" + openid.substring(0, 8));
            user.setAvatar("https://mmbiz.qpic.cn/mmbiz/icTdbqWNOwNRna42FI242Lcia07jQodd2FJGIYQfG0LAJGFxM4FbnQP6yfMxBgJ0F3YRqJCJ1aPAK2dQagdusBZg/0");
            user.setStatus(1);
            userService.save(user);
            log.info("新用户创建成功，userId: {}", user.getUserId());
        } else {
            log.info("老用户登录，userId: {}", user.getUserId());
        }

        // 3. 生成 JWT Token
        String token = jwtUtil.CreateToken(user.getUserId());

        // 4. 封装返回
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUserId(user.getUserId());
        loginVO.setNickname(user.getNickname());
        loginVO.setAvatar(user.getAvatar());

        return loginVO;
    }

    /**
     * 调用微信接口，用 code 换取 openid
     */
    private String getOpenidFromWechat(String code) {
        String url = wechatConfig.getJscode2sessionUrl(code);

        try {
            String response = restTemplate.getForObject(url, String.class);
            log.info("微信接口返回: {}", response);

            JsonNode jsonNode = objectMapper.readTree(response);
            String openid = jsonNode.get("openid").asText();

            return openid;
        } catch (Exception e) {
            log.error("调用微信接口失败", e);
            return null;
        }
    }
}
