package com.uchat.miniapp.platform.app;

import java.time.Instant;

public record MiniAppView(
        long id,
        String appId,
        String name,
        String description,
        String developerName,
        String iconObjectKey,
        String coverObjectKey,
        String iconUrl,
        String coverUrl,
        String status,
        Long currentVersionId,
        double averageRating,
        int ratingCount,
        int commentCount,
        Instant createdAt,
        Instant updatedAt
) {
}
