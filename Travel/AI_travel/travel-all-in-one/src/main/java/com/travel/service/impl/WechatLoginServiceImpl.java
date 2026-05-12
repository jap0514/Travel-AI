package com.travel.service.impl;

import com.travel.entity.User;
import com.travel.service.UserService;
import com.travel.service.WechatLoginService;
import com.travel.util.JwtUtil;
import com.travel.vo.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WechatLoginServiceImpl implements WechatLoginService {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Override
    public LoginVO login(String code) {
        //1、用 code 换取 openid
        //2、通过openid去数据库里查询用户
        Long userId=1L;
        User user = userService.getById(userId);
        //3、如果没有该用户就注册
        //4、如果有，那就根据 userId 通过JWT生成Token
        String token = jwtUtil.CreateToken(userId);
        //5、封装成VO返回给小程序
        LoginVO loginVO=new LoginVO();
        loginVO.setToken(token);
        loginVO.setUserId(userId);
        loginVO.setNickname(user.getNickname());
        loginVO.setAvatar(user.getAvatar());
        //6、但是JWT本身不能够主动作废，一旦签发，没到期就一直能用
        //7、当用户退出登录时，可以把当前Token存到Redis黑名单。每次拿到Token先判断是否在黑名单里，如果在，直接拦截
        //8、如果不在，再进行后续的验证操作
        return loginVO;
    }
}
