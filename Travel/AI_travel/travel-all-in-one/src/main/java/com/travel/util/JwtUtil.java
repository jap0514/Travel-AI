package com.travel.util;

import com.travel.config.JwtProperties;
import com.travel.exception.TokenExpiredException;
import com.travel.exception.TokenInvalidException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    private final JwtProperties jwtProperties;
    private SecretKey secretKey;

    public JwtUtil(JwtProperties jwtProperties){
        this.jwtProperties=jwtProperties;
        // 1. 从配置里取出提前定义好的Base64加密秘钥字符串
        // 做Base64解码，还原成原始字节数组
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
        // 2. 根据字节数组，生成符合HS256算法的加密秘钥SecretKey
        this.secretKey= Keys.hmacShaKeyFor(keyBytes);
    }

    //生成JWT
    public String CreateToken(Long userId){
        Date now=new Date();
        //计算过期的具体时间
        Date expireDate = new Date(now.getTime() + jwtProperties.getExpiration());

        return Jwts.builder()
                .setSubject(userId.toString())  //存用户ID
                .setIssuedAt(now)   //签发时间
                .setExpiration(expireDate)   //过期时间
                .signWith(secretKey,SignatureAlgorithm.HS256)
                .compact();
    }

    //解析Token，获取用户ID
    public Long getUserIdByToken(String token){
        try{
            Claims claims=Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return Long.parseLong(claims.getSubject());
        }catch (ExpiredJwtException e){
            //接收到这个异常就代表Token已经过期了
            throw new TokenExpiredException();
        }catch (JwtException | IllegalArgumentException e){
            //这里接受其他的异常，签名篡改、格式错误、空Token等全部非法情况
            throw new TokenInvalidException();
        }
    }

    //验证Token是否合法且未过期
    public void verifyToken(String token){
        try{
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            //如果执行到这里都没有抛出异常，就证明Token是合法且未过期的
        }catch (ExpiredJwtException e){
            //接收到这个异常就代表Token已经过期了
            throw new TokenExpiredException();
        }catch (JwtException | IllegalArgumentException e){
            //这里接受其他的异常，签名篡改、格式错误、空Token等全部非法情况
            throw new TokenInvalidException();
        }
    }


    /**
     * 获取 Token 剩余的有效时间，作为黑名单的过期时间
     */
    public long getTokenRemainSeconds(String token){
        try{
            Jws<Claims> jws=Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            Claims claims=jws.getBody();
            long expireTime = claims.getExpiration().getTime();
            long now = System.currentTimeMillis();
            long remainMs=expireTime-now;
            return remainMs>0? remainMs/1000:0;
        }catch (Exception e){
            return 0;
        }
    }


}
