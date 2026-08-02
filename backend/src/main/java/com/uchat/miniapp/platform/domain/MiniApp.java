package com.uchat.miniapp.platform.domain;

import java.time.Instant;

public record MiniApp(
        long id,
        String appId,
        String name,
        String description,
        long developerAccountId,
        String developerName,
        String iconObjectKey,
        String coverObjectKey,
        String status,
        Long currentVersionId,
        Instant createdAt,
        Instant updatedAt
) {
}
