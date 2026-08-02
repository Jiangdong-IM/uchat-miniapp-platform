package com.uchat.miniapp.platform.config;

import com.uchat.miniapp.platform.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    private final PlatformProperties platformProperties;

    public WebConfig(AuthInterceptor authInterceptor, PlatformProperties platformProperties) {
        this.authInterceptor = authInterceptor;
        this.platformProperties = platformProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/register", "/api/auth/login", "/api/health");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String configured = platformProperties.corsAllowedOrigins();
        String[] origins = configured == null ? new String[0]
                : Arrays.stream(configured.split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).toArray(String[]::new);
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .allowCredentials(true)
                .maxAge(3600);
    }

}
