package com.uchat.miniapp.platform.repository;

import com.uchat.miniapp.platform.domain.MiniApp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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

    public Optional<MiniApp> findByAppId(String appId) {
        return jdbc.query("SELECT * FROM mini_app WHERE app_id = ?", MAPPER, appId)
                .stream().findFirst();
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

    public List<MiniApp> findAdmin(String keyword, String status, int offset, int limit) {
        Query query = adminQuery(keyword, status, false);
        query.sql().append(" ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?");
        query.parameters().add(limit);
        query.parameters().add(offset);
        return jdbc.query(query.sql().toString(), MAPPER, query.parameters().toArray());
    }

    public long countAdmin(String keyword, String status) {
        Query query = adminQuery(keyword, status, true);
        Long count = jdbc.queryForObject(query.sql().toString(), Long.class,
                query.parameters().toArray());
        return count == null ? 0 : count;
    }

    public List<MiniApp> searchPublished(String keyword, int limit) {
        String pattern = "%" + keyword.toLowerCase(java.util.Locale.ROOT) + "%";
        return jdbc.query("""
                SELECT * FROM mini_app
                WHERE status = 'PUBLISHED'
                  AND (LOWER(name) LIKE ? OR LOWER(app_id) LIKE ? OR LOWER(description) LIKE ?)
                ORDER BY name, app_id LIMIT ?
                """, MAPPER, pattern, pattern, pattern, limit);
    }

    public List<MiniApp> findPublishedByDeveloper(long developerId) {
        return jdbc.query("""
                SELECT * FROM mini_app
                WHERE developer_account_id = ? AND status = 'PUBLISHED'
                ORDER BY id
                """, MAPPER, developerId);
    }

    public int updateMetadata(long id, long developerId, String name, String description) {
        return jdbc.update("""
                UPDATE mini_app SET name = ?, description = ?, updated_at = ?
                WHERE id = ? AND developer_account_id = ?
                """, name, description, Timestamp.from(Instant.now()), id, developerId);
    }

    public int updateMetadataAdmin(long id, String name, String description) {
        return jdbc.update("""
                UPDATE mini_app SET name = ?, description = ?, updated_at = ? WHERE id = ?
                """, name, description, Timestamp.from(Instant.now()), id);
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

    public int updateAssetAdmin(long id, String kind, String objectKey) {
        String column = switch (kind) {
            case "icon" -> "icon_object_key";
            case "cover" -> "cover_object_key";
            default -> throw new IllegalArgumentException("Unsupported asset kind");
        };
        return jdbc.update("UPDATE mini_app SET " + column + " = ?, updated_at = ? WHERE id = ?",
                objectKey, Timestamp.from(Instant.now()), id);
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

    public void delistAdmin(long id) {
        jdbc.update("""
                UPDATE mini_app SET status = 'DELISTED', updated_at = ? WHERE id = ?
                """, Timestamp.from(Instant.now()), id);
    }

    public int delistPublishedByDeveloper(long developerId) {
        return jdbc.update("""
                UPDATE mini_app SET status = 'DELISTED', updated_at = ?
                WHERE developer_account_id = ? AND status = 'PUBLISHED'
                """, Timestamp.from(Instant.now()), developerId);
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

    private static Query adminQuery(String keyword, String status, boolean count) {
        StringBuilder sql = new StringBuilder(count
                ? "SELECT COUNT(*) FROM mini_app WHERE 1 = 1"
                : "SELECT * FROM mini_app WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (keyword != null) {
            sql.append(" AND (LOWER(app_id) LIKE ? OR LOWER(name) LIKE ? "
                    + "OR LOWER(description) LIKE ? OR LOWER(developer_name) LIKE ?)");
            String pattern = "%" + keyword.toLowerCase(java.util.Locale.ROOT) + "%";
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            parameters.add(status);
        }
        return new Query(sql, parameters);
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

    private record Query(StringBuilder sql, List<Object> parameters) {
    }
}
