package com.uchat.miniapp.platform.repository;

import com.uchat.miniapp.platform.domain.MiniAppVersion;
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
public class VersionRepository {
    private static final RowMapper<MiniAppVersion> MAPPER = VersionRepository::map;
    private final JdbcTemplate jdbc;

    public VersionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(long miniAppId, int schemaVersion, String manifestAppId,
                       String manifestName, String version, String entryPath,
                       String permissionsJson, String manifestDescription, String objectKey,
                       String archiveSha256, long archiveSize, String releaseNotes) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO mini_app_version
                    (mini_app_id, schema_version, manifest_app_id, manifest_name, version,
                     entry_path, permissions_json, manifest_description, object_key,
                     archive_sha256, archive_size, release_notes, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_REVIEW', ?, ?)
                """, miniAppId, schemaVersion, manifestAppId, manifestName, version, entryPath,
                permissionsJson, manifestDescription, objectKey, archiveSha256, archiveSize,
                releaseNotes, Timestamp.from(now), Timestamp.from(now));
        return jdbc.queryForObject("""
                SELECT id FROM mini_app_version WHERE mini_app_id = ? AND version = ?
                """, Long.class, miniAppId, version);
    }

    public Optional<MiniAppVersion> findById(long id) {
        return jdbc.query("SELECT * FROM mini_app_version WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    public Optional<MiniAppVersion> findByIdForUpdate(long id) {
        return jdbc.query("SELECT * FROM mini_app_version WHERE id = ? FOR UPDATE", MAPPER, id)
                .stream().findFirst();
    }

    public List<MiniAppVersion> findByApp(long appId) {
        return jdbc.query("""
                SELECT * FROM mini_app_version WHERE mini_app_id = ? ORDER BY created_at DESC
                """, MAPPER, appId);
    }

    public List<MiniAppVersion> findByStatus(String status) {
        if (status == null || status.isBlank()) {
            return jdbc.query("SELECT * FROM mini_app_version ORDER BY created_at DESC", MAPPER);
        }
        return jdbc.query("""
                SELECT * FROM mini_app_version WHERE status = ? ORDER BY created_at DESC
                """, MAPPER, status);
    }

    public int review(long id, String expectedStatus, String targetStatus, String note, long reviewerId) {
        Instant now = Instant.now();
        return jdbc.update("""
                UPDATE mini_app_version
                SET status = ?, review_note = ?, reviewed_by = ?, reviewed_at = ?, updated_at = ?
                WHERE id = ? AND status = ?
                """, targetStatus, note, reviewerId, Timestamp.from(now), Timestamp.from(now),
                id, expectedStatus);
    }

    public int countPendingForDeveloper(long developerId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM mini_app_version v
                JOIN mini_app a ON a.id = v.mini_app_id
                WHERE a.developer_account_id = ? AND v.status = 'PENDING_REVIEW'
                """, Integer.class, developerId);
        return count == null ? 0 : count;
    }

    private static MiniAppVersion map(ResultSet rs, int rowNum) throws SQLException {
        return new MiniAppVersion(rs.getLong("id"), rs.getLong("mini_app_id"),
                rs.getInt("schema_version"), rs.getString("manifest_app_id"),
                rs.getString("manifest_name"), rs.getString("version"),
                rs.getString("entry_path"), rs.getString("permissions_json"),
                rs.getString("manifest_description"), rs.getString("object_key"),
                rs.getString("archive_sha256"), rs.getLong("archive_size"),
                rs.getString("release_notes"), rs.getString("status"),
                rs.getString("review_note"), nullableLong(rs, "reviewed_by"),
                instant(rs, "reviewed_at"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
