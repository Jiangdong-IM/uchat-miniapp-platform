package com.uchat.miniapp.platform.domain;

import java.time.Instant;

public record Account(
        long id,
        String username,
        String passwordHash,
        String role,
        String status,
        String purpose,
        String planDescription,
        String developerName,
        String contactEmail,
        String organizationName,
        String reviewNote,
        Long reviewedBy,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
