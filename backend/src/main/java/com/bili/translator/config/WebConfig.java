package com.bili.translator.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    public WebConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration config = new CorsConfiguration();
        // 同源部署（默认）不放开任何跨域来源；只有显式配置 ALLOWED_ORIGINS 才放行。
        // 令牌走 Authorization 头、不带 Cookie，但仍没必要为同源页面开跨域口子。
        List<String> origins = parseOrigins(appProperties.getAllowedOrigins());
        if (!origins.isEmpty()) {
            config.setAllowedOriginPatterns(origins);
        }
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    /** 逗号分隔的来源列表，空串与空白项都会被丢掉 */
    private static List<String> parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    /**
     * 前端已合并进后端：构建产物打在 jar 的 {@code static/} 下，由 Spring Boot 直接托管，
     * 不再需要独立的 nginx 容器。
     *
     * <p>本地只跑 {@code mvn spring-boot:run} 且未构建前端时，static 目录不存在，
     * 这里只会返回 404，不影响应用启动；开发期仍可用 vite dev 走 proxy。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // vite 产物文件名带 contenthash，可以放心长缓存
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());

        // 其余路径：命中文件就返回，否则回退 index.html，把路由交给前端
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // /api、/ws 不参与 SPA 回退，否则拼错的接口路径会返回一段 HTML 而不是 404 JSON
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("ws/")) {
                            return null;
                        }
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        Resource index = new ClassPathResource("static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
