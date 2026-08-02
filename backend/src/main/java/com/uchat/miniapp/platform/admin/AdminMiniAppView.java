package com.uchat.miniapp.platform.admin;

import com.uchat.miniapp.platform.app.MiniAppView;
import com.uchat.miniapp.platform.domain.MiniApp;

import java.time.Instant;

public record AdminMiniAppView(
        long id,
        String appId,
        String name,
        String description,
        long developerAccountId,
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
    public static AdminMiniAppView from(MiniApp app, MiniAppView view) {
        return new AdminMiniAppView(view.id(), view.appId(), view.name(), view.description(),
                app.developerAccountId(), view.developerName(), view.iconObjectKey(),
                view.coverObjectKey(), view.iconUrl(), view.coverUrl(), view.status(),
                view.currentVersionId(), view.averageRating(), view.ratingCount(),
                view.commentCount(), view.createdAt(), view.updatedAt());
    }
}
