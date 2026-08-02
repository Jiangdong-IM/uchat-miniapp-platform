package com.uchat.miniapp.platform.admin;

import java.time.Instant;

public record AdminCommentView(
        long id,
        String appId,
        String appName,
        String userDisplayName,
        String content,
        boolean featured,
        String status,
        Instant createdAt
) {
}
