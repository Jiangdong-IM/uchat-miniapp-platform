package com.uchat.miniapp.platform.domain;

import java.time.Instant;

public record DeveloperAppeal(
        long id,
        long developerAccountId,
        String content,
        String status,
        String reviewNote,
        Long reviewedBy,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
