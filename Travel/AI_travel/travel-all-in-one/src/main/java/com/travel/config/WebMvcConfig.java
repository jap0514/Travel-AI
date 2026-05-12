package com.travel.config;

import com.travel.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/login")
//                .excludePathPatterns("/task/**")
                .excludePathPatterns(
                        "/doc.html",          // 主文档页
                        "/webjars/**",        // 文档页依赖的静态资源
                        "/swagger-resources/**", // 接口文档的资源配置
                        "/v3/api-docs/**"     // OpenAPI接口文档数据
                );
    }

}
