package cn.gyb.llm.cr.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域资源共享（CORS）配置。
 * <p>
 * 允许所有来源、所有标准 HTTP 方法和所有请求头的跨域请求，
 * 预检请求缓存时间为 3600 秒。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 配置全局 CORS 映射规则。
     *
     * @param registry CORS 注册器
     */
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
