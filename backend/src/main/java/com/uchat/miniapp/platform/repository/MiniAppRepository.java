package com.uchat.miniapp.platform.repository;

import com.uchat.miniapp.platform.domain.MiniApp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MiniAppRepository {
    private static final RowMapper<MiniApp> MAPPER = MiniAppRepository::map;
    private final JdbcTemplate jdbc;

    public MiniAppRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int countByDeveloper(long developerId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mini_app WHERE developer_account_id = ?",
                Integer.class, developerId);
        return count == null ? 0 : count;
    }

    public long insert(String appId, String name, String description,
                       long developerId, String developerName) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO mini_app
                    (app_id, name, description, developer_account_id, developer_name,
                     status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, ?)
                """, appId, name, description, developerId, developerName,
                Timestamp.from(now), Timestamp.from(now));
        return jdbc.queryForObject("SELECT id FROM mini_app WHERE app_id = ?", Long.class, appId);
    }

    public Optional<MiniApp> findById(long id) {
        return jdbc.query("SELECT * FROM mini_app WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<MiniApp> findByIdForUpdate(long id) {
        return jdbc.query("SELECT * FROM mini_app WHERE id = ? FOR UPDATE", MAPPER, id)
                .stream().findFirst();
    }

    public List<MiniApp> findByDeveloper(long developerId) {
        return jdbc.query("""
                SELECT * FROM mini_app WHERE developer_account_id = ?
                ORDER BY updated_at DESC
                """, MAPPER, developerId);
    }

    public List<MiniApp> findRecentByDeveloper(long developerId, int limit) {
        return jdbc.query("""
                SELECT * FROM mini_app WHERE developer_account_id = ?
                ORDER BY updated_at DESC LIMIT ?
                """, MAPPER, developerId, limit);
    }

    public int updateMetadata(long id, long developerId, String name, String description) {
        return jdbc.update("""
                UPDATE mini_app SET name = ?, description = ?, updated_at = ?
                WHERE id = ? AND developer_account_id = ?
                """, name, description, Timestamp.from(Instant.now()), id, developerId);
    }

    public int updateAsset(long id, long developerId, String kind, String objectKey) {
        String column = switch (kind) {
            case "icon" -> "icon_object_key";
            case "cover" -> "cover_object_key";
            default -> throw new IllegalArgumentException("Unsupported asset kind");
        };
        return jdbc.update("UPDATE mini_app SET " + column + " = ?, updated_at = ? " +
                        "WHERE id = ? AND developer_account_id = ?",
                objectKey, Timestamp.from(Instant.now()), id, developerId);
    }

    public void activate(long id, long versionId) {
        jdbc.update("""
                UPDATE mini_app SET status = 'PUBLISHED', current_version_id = ?, updated_at = ?
                WHERE id = ?
                """, versionId, Timestamp.from(Instant.now()), id);
    }

    public void delist(long id, long developerId) {
        jdbc.update("""
                UPDATE mini_app SET status = 'DELISTED', updated_at = ?
                WHERE id = ? AND developer_account_id = ?
                """, Timestamp.from(Instant.now()), id, developerId);
    }

    public ReviewSummary reviewSummary(long miniAppId) {
        return jdbc.queryForObject("""
                SELECT
                  COALESCE((SELECT AVG(score) FROM mini_app_rating WHERE mini_app_id = ?), 0) average_rating,
                  (SELECT COUNT(*) FROM mini_app_rating WHERE mini_app_id = ?) rating_count,
                  (SELECT COUNT(*) FROM mini_app_comment WHERE mini_app_id = ? AND status = 'VISIBLE') comment_count
                """, (rs, rowNum) -> new ReviewSummary(
                rs.getBigDecimal("average_rating"), rs.getInt("rating_count"),
                rs.getInt("comment_count")), miniAppId, miniAppId, miniAppId);
    }

    public int countPublished(long developerId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM mini_app
                WHERE developer_account_id = ? AND status = 'PUBLISHED'
                """, Integer.class, developerId);
        return count == null ? 0 : count;
    }

    public double averageRatingForDeveloper(long developerId) {
        Double value = jdbc.queryForObject("""
                SELECT COALESCE(AVG(r.score), 0)
                FROM mini_app a LEFT JOIN mini_app_rating r ON r.mini_app_id = a.id
                WHERE a.developer_account_id = ?
                """, Double.class, developerId);
        return value == null ? 0 : value;
    }

    private static MiniApp map(ResultSet rs, int rowNum) throws SQLException {
        long currentVersionId = rs.getLong("current_version_id");
        boolean currentVersionIsNull = rs.wasNull();
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new MiniApp(rs.getLong("id"), rs.getString("app_id"), rs.getString("name"),
                rs.getString("description"), rs.getLong("developer_account_id"),
                rs.getString("developer_name"), rs.getString("icon_object_key"),
                rs.getString("cover_object_key"), rs.getString("status"),
                currentVersionIsNull ? null : currentVersionId,
                created.toInstant(), updated.toInstant());
    }

    public record ReviewSummary(java.math.BigDecimal averageRating, int ratingCount, int commentCount) {
    }
}
