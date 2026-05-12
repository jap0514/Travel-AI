package com.travel.service;

import com.travel.vo.LoginVO;

public interface WechatLoginService {
    /**
     * 通过小程序端传过来的code，来获取openid来进行登录
     * @param code
     * @return LoginVO
     */
    LoginVO login(String code);
}
