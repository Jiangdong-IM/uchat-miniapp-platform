package com.uchat.miniapp.platform.admin;

import com.uchat.miniapp.platform.domain.Account;
import com.uchat.miniapp.platform.domain.DeveloperAppeal;

import java.time.Instant;

public record AppealView(
        long id,
        long developerAccountId,
        String developerName,
        String username,
        String content,
        String status,
        String reviewNote,
        Long reviewedBy,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static AppealView from(DeveloperAppeal appeal, Account developer) {
        return new AppealView(appeal.id(), appeal.developerAccountId(), developer.developerName(),
                developer.username(), appeal.content(), appeal.status(), appeal.reviewNote(),
                appeal.reviewedBy(), appeal.reviewedAt(), appeal.createdAt(), appeal.updatedAt());
    }
}
