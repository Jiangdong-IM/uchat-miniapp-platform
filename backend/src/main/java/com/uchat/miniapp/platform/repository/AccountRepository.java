package com.uchat.miniapp.platform.repository;

import com.uchat.miniapp.platform.domain.Account;
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
public class AccountRepository {
    private static final RowMapper<Account> MAPPER = AccountRepository::map;
    private final JdbcTemplate jdbc;

    public AccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String username, String passwordHash, String role, String status,
                       String purpose, String planDescription, String developerName,
                       String contactEmail, String organizationName) {
        jdbc.update("""
                INSERT INTO mini_app_developer_account
                    (username, password_hash, role, status, purpose, plan_description,
                     developer_name, contact_email, organization_name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, username, passwordHash, role, status, purpose, planDescription,
                developerName, contactEmail, organizationName, Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
        return jdbc.queryForObject("SELECT id FROM mini_app_developer_account WHERE username = ?",
                Long.class, username);
    }

    public Optional<Account> findById(long id) {
        return jdbc.query("SELECT * FROM mini_app_developer_account WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    public Optional<Account> findByUsername(String username) {
        return jdbc.query("SELECT * FROM mini_app_developer_account WHERE username = ?", MAPPER, username)
                .stream().findFirst();
    }

    public boolean usernameOrEmailExists(String username, String email) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM mini_app_developer_account
                WHERE username = ? OR contact_email = ?
                """, Integer.class, username, email);
        return count != null && count > 0;
    }

    public List<Account> findRegistrations(String status) {
        if (status == null || status.isBlank()) {
            return jdbc.query("""
                    SELECT * FROM mini_app_developer_account
                    WHERE role = 'DEVELOPER' ORDER BY created_at DESC
                    """, MAPPER);
        }
        return jdbc.query("""
                SELECT * FROM mini_app_developer_account
                WHERE role = 'DEVELOPER' AND status = ? ORDER BY created_at DESC
                """, MAPPER, status);
    }

    public List<Account> findDevelopers(String keyword, String status, int offset, int limit) {
        Query query = developerQuery(keyword, status, false);
        query.sql().append(" ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?");
        query.parameters().add(limit);
        query.parameters().add(offset);
        return jdbc.query(query.sql().toString(), MAPPER, query.parameters().toArray());
    }

    public long countDevelopers(String keyword, String status) {
        Query query = developerQuery(keyword, status, true);
        Long count = jdbc.queryForObject(query.sql().toString(), Long.class,
                query.parameters().toArray());
        return count == null ? 0 : count;
    }

    public int reviewPending(long id, String status, String note, long reviewerId) {
        Instant now = Instant.now();
        return jdbc.update("""
                UPDATE mini_app_developer_account
                SET status = ?, review_note = ?, reviewed_by = ?, reviewed_at = ?, updated_at = ?
                WHERE id = ? AND role = 'DEVELOPER' AND status = 'PENDING'
                """, status, note, reviewerId, Timestamp.from(now), Timestamp.from(now), id);
    }

    public void lockById(long id) {
        jdbc.queryForObject("SELECT id FROM mini_app_developer_account WHERE id = ? FOR UPDATE",
                Long.class, id);
    }

    public Optional<Account> findByIdForUpdate(long id) {
        return jdbc.query("SELECT * FROM mini_app_developer_account WHERE id = ? FOR UPDATE",
                MAPPER, id).stream().findFirst();
    }

    public int banDeveloper(long id, String reason, long adminId) {
        Instant now = Instant.now();
        return jdbc.update("""
                UPDATE mini_app_developer_account
                SET status = 'BANNED', ban_reason = ?, banned_by = ?, banned_at = ?, updated_at = ?
                WHERE id = ? AND role = 'DEVELOPER' AND status = 'APPROVED'
                """, reason, adminId, Timestamp.from(now), Timestamp.from(now), id);
    }

    public int unbanDeveloper(long id) {
        return jdbc.update("""
                UPDATE mini_app_developer_account
                SET status = 'APPROVED', ban_reason = NULL, banned_by = NULL,
                    banned_at = NULL, updated_at = ?
                WHERE id = ? AND role = 'DEVELOPER' AND status = 'BANNED'
                """, Timestamp.from(Instant.now()), id);
    }

    private static Query developerQuery(String keyword, String status, boolean count) {
        StringBuilder sql = new StringBuilder(count
                ? "SELECT COUNT(*) FROM mini_app_developer_account WHERE role = 'DEVELOPER'"
                : "SELECT * FROM mini_app_developer_account WHERE role = 'DEVELOPER'");
        List<Object> parameters = new ArrayList<>();
        if (keyword != null) {
            sql.append(" AND (LOWER(username) LIKE ? OR LOWER(developer_name) LIKE ? "
                    + "OR LOWER(contact_email) LIKE ? OR LOWER(COALESCE(organization_name, '')) LIKE ?)");
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

    private static Account map(ResultSet rs, int rowNum) throws SQLException {
        return new Account(
                rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("role"), rs.getString("status"), rs.getString("purpose"),
                rs.getString("plan_description"), rs.getString("developer_name"),
                rs.getString("contact_email"), rs.getString("organization_name"),
                rs.getString("review_note"), nullableLong(rs, "reviewed_by"),
                instant(rs, "reviewed_at"), rs.getString("ban_reason"),
                nullableLong(rs, "banned_by"), instant(rs, "banned_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record Query(StringBuilder sql, List<Object> parameters) {
    }
}
