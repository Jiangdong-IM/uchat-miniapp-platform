package com.uchat.miniapp.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform")
public record PlatformProperties(
        String corsAllowedOrigins,
        int sessionHours,
        String bootstrapAdminPassword,
        String assetPublicBaseUrl,
        String localAppPublicBaseUrl
) {
    public PlatformProperties {
        if (sessionHours <= 0) {
            sessionHours = 24;
        }
    }
}
