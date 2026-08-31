package com.travel.config;

import com.travel.interceptor.AuthInterceptor;
import com.travel.interceptor.SignatureInterceptor;
import com.travel.interceptor.TraceIdInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private SignatureInterceptor signatureInterceptor;

    @Autowired
    private TraceIdInterceptor traceIdInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry){
        // TraceId 拦截器 - 最早执行，为所有请求生成/透传 TraceId
        registry.addInterceptor(traceIdInterceptor)
                .addPathPatterns("/**");

        // 签名验证拦截器 - 只拦截Python回调接口
        registry.addInterceptor(signatureInterceptor)
                .addPathPatterns("/sendMessageByPython/**");

        // 认证拦截器
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/login")
//                .excludePathPatterns("/task/**")
                .excludePathPatterns(
                        "/doc.html",          // 主文档页
                        "/webjars/**",        // 文档页依赖的静态资源
                        "/swagger-resources/**", // 接口文档的资源配置
                        "/v3/api-docs/**",    // OpenAPI接口文档数据
                        "/sendMessageByPython/**", // Python回调接口，不需要认证（但需要签名）
                        "/test/travel/**",             // 测试页面，不需要认证
                        "/hotel/**",
                        // ========== ES 同步管理接口（演示阶段无需登录）==========
                        "/es/**",              // ES 健康检查（/es/health、/es/ping）
                        "/admin/hotel/**"      // 酒店同步管理（/admin/hotel/reindex、/syncStatus 等）
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

}
