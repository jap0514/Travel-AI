package com.travel.interceptor;

import com.travel.exception.TokenInvalidException;
import com.travel.exception.TokenNullException;
import com.travel.util.JwtUtil;
import io.netty.util.internal.StringUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JwtUtil jwtUtil;

    // Redis黑名单key前缀
    private static final String JWT_BLACK_PREFIX = "jwt:black:";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler){

        System.out.println("到拦截器这里了");

        //1、从请求头那里获得 Token
        String authHeader=request.getHeader("Authorization");

        //2、判断空值和格式
        if(!StringUtils.hasText(authHeader)||!authHeader.startsWith("Bearer ")){
            System.out.println("出错了"+authHeader);
            throw new TokenNullException();
        }

        //3、截取真实的 Token
        String token=authHeader.substring(7);


        //4、先校验Redis黑名单
        String blackKey=JWT_BLACK_PREFIX+token;
        if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(blackKey))){
            throw new TokenInvalidException("已退出登录，请重新登录");
        }

        //5、JWT内部验证 Token
        jwtUtil.verifyToken(token);

        //6、从 Token 中解析 userId，然后放到request中，后续可以在controller中直接获取userId
        Long userId=jwtUtil.getUserIdByToken(token);
        request.setAttribute("userId",userId);

        //6、验证通过，放行
        return true;
    }
}
