package com.uchat.miniapp.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.uchat")
public record UchatIntegrationProperties(String baseUrl, String internalToken) {
}
