package com.uchat.miniapp.platform.app;

import java.time.Instant;
import java.util.List;

public record ReviewView(
        long miniAppId,
        double averageRating,
        int ratingCount,
        int commentCount,
        List<Rating> ratings,
        List<Comment> featuredComments,
        List<Comment> comments
) {
    public record Rating(long id, long uchatUserId, int score,
                         Instant createdAt, Instant updatedAt) {
    }

    public record Comment(long id, long uchatUserId, String userDisplayName,
                          String content, boolean featured, String status,
                          Instant createdAt, Instant updatedAt) {
    }
}
