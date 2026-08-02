package com.uchat.miniapp.platform.admin;

import com.uchat.miniapp.platform.domain.Account;

import java.time.Instant;

public record AdminDeveloperView(
        long id,
        String username,
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
        String banReason,
        Long bannedBy,
        Instant bannedAt,
        Instant createdAt,
        Instant updatedAt,
        int appCount,
        int publishedAppCount
) {
    public static AdminDeveloperView from(Account account, int appCount, int publishedAppCount) {
        return new AdminDeveloperView(account.id(), account.username(), account.role(),
                account.status(), account.purpose(), account.planDescription(),
                account.developerName(), account.contactEmail(), account.organizationName(),
                account.reviewNote(), account.reviewedBy(), account.reviewedAt(),
                account.banReason(), account.bannedBy(), account.bannedAt(), account.createdAt(),
                account.updatedAt(), appCount, publishedAppCount);
    }
}
