package com.uchat.miniapp.platform.repository;

import com.uchat.miniapp.platform.domain.DeveloperAppeal;
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
public class AppealRepository {
    private static final RowMapper<DeveloperAppeal> MAPPER = AppealRepository::map;
    private final JdbcTemplate jdbc;

    public AppealRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(long developerAccountId, String content) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO mini_app_developer_appeal
                    (developer_account_id, content, status, created_at, updated_at)
                VALUES (?, ?, 'PENDING', ?, ?)
                """, developerAccountId, content, Timestamp.from(now), Timestamp.from(now));
        return jdbc.queryForObject("""
                SELECT id FROM mini_app_developer_appeal
                WHERE developer_account_id = ? ORDER BY id DESC LIMIT 1
                """, Long.class, developerAccountId);
    }

    public boolean hasPending(long developerAccountId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM mini_app_developer_appeal
                WHERE developer_account_id = ? AND status = 'PENDING'
                """, Integer.class, developerAccountId);
        return count != null && count > 0;
    }

    public Optional<DeveloperAppeal> findById(long id) {
        return jdbc.query("SELECT * FROM mini_app_developer_appeal WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    public Optional<DeveloperAppeal> findByIdForUpdate(long id) {
        return jdbc.query("SELECT * FROM mini_app_developer_appeal WHERE id = ? FOR UPDATE",
                MAPPER, id).stream().findFirst();
    }

    public List<DeveloperAppeal> findByDeveloper(long developerAccountId) {
        return jdbc.query("""
                SELECT * FROM mini_app_developer_appeal
                WHERE developer_account_id = ? ORDER BY created_at DESC, id DESC
                """, MAPPER, developerAccountId);
    }

    public List<DeveloperAppeal> findAdmin(String status, Long developerAccountId,
                                           int offset, int limit) {
        Query query = adminQuery(status, developerAccountId, false);
        query.sql().append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        query.parameters().add(limit);
        query.parameters().add(offset);
        return jdbc.query(query.sql().toString(), MAPPER, query.parameters().toArray());
    }

    public long countAdmin(String status, Long developerAccountId) {
        Query query = adminQuery(status, developerAccountId, true);
        Long count = jdbc.queryForObject(query.sql().toString(), Long.class,
                query.parameters().toArray());
        return count == null ? 0 : count;
    }

    public int decide(long id, String status, String reviewNote, long reviewerId) {
        Instant now = Instant.now();
        return jdbc.update("""
                UPDATE mini_app_developer_appeal
                SET status = ?, review_note = ?, reviewed_by = ?, reviewed_at = ?, updated_at = ?
                WHERE id = ? AND status = 'PENDING'
                """, status, reviewNote, reviewerId, Timestamp.from(now), Timestamp.from(now), id);
    }

    public int approvePendingForDeveloper(long developerAccountId, String reviewNote,
                                          long reviewerId) {
        Instant now = Instant.now();
        return jdbc.update("""
                UPDATE mini_app_developer_appeal
                SET status = 'APPROVED', review_note = ?, reviewed_by = ?, reviewed_at = ?,
                    updated_at = ?
                WHERE developer_account_id = ? AND status = 'PENDING'
                """, reviewNote, reviewerId, Timestamp.from(now), Timestamp.from(now),
                developerAccountId);
    }

    private static Query adminQuery(String status, Long developerAccountId, boolean count) {
        StringBuilder sql = new StringBuilder(count
                ? "SELECT COUNT(*) FROM mini_app_developer_appeal WHERE 1 = 1"
                : "SELECT * FROM mini_app_developer_appeal WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (status != null) {
            sql.append(" AND status = ?");
            parameters.add(status);
        }
        if (developerAccountId != null) {
            sql.append(" AND developer_account_id = ?");
            parameters.add(developerAccountId);
        }
        return new Query(sql, parameters);
    }

    private static DeveloperAppeal map(ResultSet rs, int rowNum) throws SQLException {
        return new DeveloperAppeal(rs.getLong("id"), rs.getLong("developer_account_id"),
                rs.getString("content"), rs.getString("status"), rs.getString("review_note"),
                nullableLong(rs, "reviewed_by"), instant(rs, "reviewed_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record Query(StringBuilder sql, List<Object> parameters) {
    }
}
