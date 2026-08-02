package com.uchat.miniapp.platform.api;

import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

@RestController
public class HealthController {
    private final Environment environment;

    public HealthController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/api/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "time", Instant.now(),
                "profiles", Arrays.asList(environment.getActiveProfiles().length == 0
                        ? environment.getDefaultProfiles() : environment.getActiveProfiles())));
    }
}
