package com.uchat.miniapp.platform.admin;

import java.time.Instant;

public record AdminCommentView(
        long id,
        long miniAppId,
        String appId,
        String appName,
        long uchatUserId,
        String userDisplayName,
        String content,
        boolean featured,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
