package com.uchat.miniapp.platform.auth;

import com.uchat.miniapp.platform.domain.Account;

import java.time.Instant;

public record AccountView(
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
        Instant updatedAt
) {
    public static AccountView from(Account account) {
        return new AccountView(account.id(), account.username(), account.role(), account.status(),
                account.purpose(), account.planDescription(), account.developerName(),
                account.contactEmail(), account.organizationName(), account.reviewNote(),
                account.reviewedBy(), account.reviewedAt(), account.banReason(), account.bannedBy(),
                account.bannedAt(), account.createdAt(), account.updatedAt());
    }
}
