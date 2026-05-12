package com.travel.service.impl;

import com.travel.service.LogoutService;
import com.travel.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class LogoutServiceImpl implements LogoutService {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String JWT_BLACK_PREFIX="jwt:black:";

    @Override
    public void logout(String token) {
        //1、获取用户该Token的剩余过期时间
        long tokenRemainSeconds = jwtUtil.getTokenRemainSeconds(token);
        //2、将该Token放到Redis的黑名单中，设置过期时间
        if(tokenRemainSeconds>0){
            String key=JWT_BLACK_PREFIX+token;
            //记录好key值就行，value值没有影响
            stringRedisTemplate.opsForValue().set(key,"1",tokenRemainSeconds, TimeUnit.SECONDS);
        }
    }
}
