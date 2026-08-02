package com.uchat.miniapp.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class ReviewRepository {
    private final JdbcTemplate jdbc;

    public ReviewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RatingRow> ratings(long miniAppId) {
        return jdbc.query("""
                SELECT id, uchat_user_id, score, created_at, updated_at
                FROM mini_app_rating WHERE mini_app_id = ? ORDER BY updated_at DESC
                """, (rs, rowNum) -> new RatingRow(rs.getLong("id"),
                rs.getLong("uchat_user_id"), rs.getInt("score"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), miniAppId);
    }

    public List<CommentRow> comments(long miniAppId) {
        return jdbc.query("""
                SELECT id, uchat_user_id, user_display_name, content, is_featured, status,
                       created_at, updated_at
                FROM mini_app_comment WHERE mini_app_id = ? AND status = 'VISIBLE'
                ORDER BY is_featured DESC, created_at DESC
                """, (rs, rowNum) -> new CommentRow(rs.getLong("id"),
                rs.getLong("uchat_user_id"), rs.getString("user_display_name"),
                rs.getString("content"), rs.getBoolean("is_featured"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), miniAppId);
    }

    public List<AdminCommentRow> adminComments() {
        return jdbc.query("""
                SELECT c.id, a.app_id, a.name AS app_name, c.user_display_name,
                       c.content, c.is_featured, c.status, c.created_at
                FROM mini_app_comment c
                INNER JOIN mini_app a ON a.id = c.mini_app_id
                ORDER BY c.is_featured DESC, c.created_at DESC, c.id DESC
                """, (rs, rowNum) -> new AdminCommentRow(
                rs.getLong("id"), rs.getString("app_id"), rs.getString("app_name"),
                rs.getString("user_display_name"), rs.getString("content"),
                rs.getBoolean("is_featured"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant()));
    }

    public int setFeatured(long commentId, boolean featured) {
        return jdbc.update("""
                UPDATE mini_app_comment SET is_featured = ?, updated_at = ? WHERE id = ?
                """, featured, Timestamp.from(Instant.now()), commentId);
    }

    public void upsertLocalRating(long miniAppId, long uchatUserId, int score) {
        Instant now = Instant.now();
        int updated = jdbc.update("""
                UPDATE mini_app_rating SET score = ?, updated_at = ?
                WHERE mini_app_id = ? AND uchat_user_id = ?
                """, score, Timestamp.from(now), miniAppId, uchatUserId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO mini_app_rating
                        (mini_app_id, uchat_user_id, score, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, miniAppId, uchatUserId, score, Timestamp.from(now), Timestamp.from(now));
        }
    }

    public void upsertLocalComment(long miniAppId, long uchatUserId, String userDisplayName,
                                   String content) {
        Instant now = Instant.now();
        int updated = jdbc.update("""
                UPDATE mini_app_comment
                SET user_display_name = ?, content = ?, status = 'VISIBLE', updated_at = ?
                WHERE mini_app_id = ? AND uchat_user_id = ?
                """, userDisplayName, content, Timestamp.from(now), miniAppId, uchatUserId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO mini_app_comment
                        (mini_app_id, uchat_user_id, user_display_name, content,
                         is_featured, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, FALSE, 'VISIBLE', ?, ?)
                    """, miniAppId, uchatUserId, userDisplayName, content,
                    Timestamp.from(now), Timestamp.from(now));
        }
    }

    public record RatingRow(long id, long uchatUserId, int score,
                            Instant createdAt, Instant updatedAt) {
    }

    public record CommentRow(long id, long uchatUserId, String userDisplayName,
                             String content, boolean featured, String status,
                             Instant createdAt, Instant updatedAt) {
    }

    public record AdminCommentRow(long id, String appId, String appName,
                                  String userDisplayName, String content,
                                  boolean featured, String status, Instant createdAt) {
    }
}
