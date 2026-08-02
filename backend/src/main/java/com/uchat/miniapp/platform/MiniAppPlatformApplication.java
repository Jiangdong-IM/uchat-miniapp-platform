package com.uchat.miniapp.platform;

import com.uchat.miniapp.platform.config.PlatformProperties;
import com.uchat.miniapp.platform.config.UchatIntegrationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({PlatformProperties.class, UchatIntegrationProperties.class})
public class MiniAppPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiniAppPlatformApplication.class, args);
    }
}
