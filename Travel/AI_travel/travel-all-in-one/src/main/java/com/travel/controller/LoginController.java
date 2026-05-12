package com.travel.controller;

import com.travel.common.Result;
import com.travel.dto.WechatLoginDTO;
import com.travel.service.LogoutService;
import com.travel.service.UserService;
import com.travel.service.WechatLoginService;
import com.travel.vo.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 1、微信小程序端发来用户的临时code
 * 2、后端拿着code去请求微信官方服务器--->换取openid(用户唯一标识)+session_key
 * 3、再用openid生成自己的登录Token返回给小程序
 * 4、小程序保存Token，后续的请求需要带上Token来鉴权
 */

@RestController
@RequestMapping
public class LoginController {
    @Autowired
    private UserService userService;

    @Autowired
    private LogoutService logoutService;

    @Autowired
    private WechatLoginService wechatLoginService;

    /**
     * 这里先在数据库固定openid，后续再去申请
     */
    @PostMapping("/login")
    public Result<LoginVO> WechatLogin(@RequestBody WechatLoginDTO wechatLoginDTO){
        LoginVO loginVO=wechatLoginService.login(wechatLoginDTO.getCode());
        return Result.success(loginVO);
    }

    /**
     * 1、退出登录时，先检查Token的过期时间
     * 2、把Token写进Redis的黑名单中，并且设置过期时间和JWT的过期时间一致
     * 3、当用户后续再次使用Token的时候，就会被拦截，需重新登录
     */
    @PostMapping("/logout")
    public Result<Void> Logout(@RequestHeader("Authorization") String authorization){
        //1、校验请求头格式
        if(!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")){
            return Result.error("登录凭证无效");
        }

        //2、截取出原始的 Token
        String token =authorization.substring(7);
        logoutService.logout(token);
        return Result.success();
    }
}
