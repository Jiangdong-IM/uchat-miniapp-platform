package com.uchat.miniapp.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    public List<AdminCommentRow> adminComments(String keyword, String status, Long miniAppId,
                                                int offset, int limit) {
        AdminQuery query = adminQuery(keyword, status, miniAppId, false);
        query.sql().append(" ORDER BY c.is_featured DESC, c.updated_at DESC, c.id DESC LIMIT ? OFFSET ?");
        query.parameters().add(limit);
        query.parameters().add(offset);
        return jdbc.query(query.sql().toString(), (rs, rowNum) -> mapAdminComment(rs),
                query.parameters().toArray());
    }

    public long countAdminComments(String keyword, String status, Long miniAppId) {
        AdminQuery query = adminQuery(keyword, status, miniAppId, true);
        Long count = jdbc.queryForObject(query.sql().toString(), Long.class,
                query.parameters().toArray());
        return count == null ? 0 : count;
    }

    public Optional<AdminCommentRow> findAdminComment(long id) {
        return jdbc.query("""
                SELECT c.id, c.mini_app_id, a.app_id, a.name AS app_name, c.uchat_user_id,
                       c.user_display_name, c.content, c.is_featured, c.status,
                       c.created_at, c.updated_at
                FROM mini_app_comment c INNER JOIN mini_app a ON a.id = c.mini_app_id
                WHERE c.id = ?
                """, (rs, rowNum) -> mapAdminComment(rs), id).stream().findFirst();
    }

    public long insertComment(long miniAppId, long uchatUserId, String userDisplayName,
                              String content, boolean featured, String status) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO mini_app_comment
                    (mini_app_id, uchat_user_id, user_display_name, content, is_featured,
                     status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, miniAppId, uchatUserId, userDisplayName, content, featured, status,
                Timestamp.from(now), Timestamp.from(now));
        return jdbc.queryForObject("""
                SELECT id FROM mini_app_comment WHERE mini_app_id = ? AND uchat_user_id = ?
                """, Long.class, miniAppId, uchatUserId);
    }

    public int updateComment(long id, String userDisplayName, String content,
                             boolean featured, String status) {
        return jdbc.update("""
                UPDATE mini_app_comment
                SET user_display_name = ?, content = ?, is_featured = ?, status = ?, updated_at = ?
                WHERE id = ?
                """, userDisplayName, content, featured, status,
                Timestamp.from(Instant.now()), id);
    }

    public int deleteComment(long id) {
        return jdbc.update("DELETE FROM mini_app_comment WHERE id = ?", id);
    }

    public int setFeatured(long commentId, boolean featured) {
        if (featured) {
            return jdbc.update("""
                    UPDATE mini_app_comment SET is_featured = TRUE, updated_at = ?
                    WHERE id = ? AND status = 'VISIBLE'
                    """, Timestamp.from(Instant.now()), commentId);
        }
        return jdbc.update("""
                UPDATE mini_app_comment SET is_featured = FALSE, updated_at = ? WHERE id = ?
                """, Timestamp.from(Instant.now()), commentId);
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
                SET user_display_name = ?, content = ?, updated_at = ?
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

    public Integer currentUserRating(long miniAppId, long uchatUserId) {
        return jdbc.query("""
                SELECT score FROM mini_app_rating
                WHERE mini_app_id = ? AND uchat_user_id = ?
                """, (rs, rowNum) -> rs.getInt("score"), miniAppId, uchatUserId)
                .stream().findFirst().orElse(null);
    }

    public List<CommentRow> visibleComments(long miniAppId, int offset, int limit) {
        return jdbc.query("""
                SELECT id, uchat_user_id, user_display_name, content, is_featured, status,
                       created_at, updated_at
                FROM mini_app_comment
                WHERE mini_app_id = ? AND status = 'VISIBLE'
                ORDER BY updated_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> mapComment(rs), miniAppId, limit, offset);
    }

    public List<CommentRow> featuredVisibleComments(long miniAppId, int limit) {
        return jdbc.query("""
                SELECT id, uchat_user_id, user_display_name, content, is_featured, status,
                       created_at, updated_at
                FROM mini_app_comment
                WHERE mini_app_id = ? AND status = 'VISIBLE' AND is_featured = TRUE
                ORDER BY updated_at DESC, id DESC
                LIMIT ?
                """, (rs, rowNum) -> mapComment(rs), miniAppId, limit);
    }

    private static CommentRow mapComment(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CommentRow(rs.getLong("id"), rs.getLong("uchat_user_id"),
                rs.getString("user_display_name"), rs.getString("content"),
                rs.getBoolean("is_featured"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static AdminCommentRow mapAdminComment(java.sql.ResultSet rs)
            throws java.sql.SQLException {
        return new AdminCommentRow(rs.getLong("id"), rs.getLong("mini_app_id"),
                rs.getString("app_id"), rs.getString("app_name"),
                rs.getLong("uchat_user_id"), rs.getString("user_display_name"),
                rs.getString("content"), rs.getBoolean("is_featured"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static AdminQuery adminQuery(String keyword, String status, Long miniAppId,
                                         boolean count) {
        StringBuilder sql = new StringBuilder(count
                ? "SELECT COUNT(*) FROM mini_app_comment c INNER JOIN mini_app a ON a.id = c.mini_app_id WHERE 1 = 1"
                : "SELECT c.id, c.mini_app_id, a.app_id, a.name AS app_name, c.uchat_user_id, "
                + "c.user_display_name, c.content, c.is_featured, c.status, c.created_at, c.updated_at "
                + "FROM mini_app_comment c INNER JOIN mini_app a ON a.id = c.mini_app_id WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (keyword != null) {
            sql.append(" AND (LOWER(a.app_id) LIKE ? OR LOWER(a.name) LIKE ? "
                    + "OR LOWER(c.user_display_name) LIKE ? OR LOWER(c.content) LIKE ?)");
            String pattern = "%" + keyword.toLowerCase(java.util.Locale.ROOT) + "%";
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        if (status != null) {
            sql.append(" AND c.status = ?");
            parameters.add(status);
        }
        if (miniAppId != null) {
            sql.append(" AND c.mini_app_id = ?");
            parameters.add(miniAppId);
        }
        return new AdminQuery(sql, parameters);
    }

    public record RatingRow(long id, long uchatUserId, int score,
                            Instant createdAt, Instant updatedAt) {
    }

    public record CommentRow(long id, long uchatUserId, String userDisplayName,
                             String content, boolean featured, String status,
                             Instant createdAt, Instant updatedAt) {
    }

    public record AdminCommentRow(long id, long miniAppId, String appId, String appName,
                                  long uchatUserId, String userDisplayName, String content,
                                  boolean featured, String status, Instant createdAt,
                                  Instant updatedAt) {
    }

    private record AdminQuery(StringBuilder sql, List<Object> parameters) {
    }
}
