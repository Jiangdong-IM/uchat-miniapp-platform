package com.uchat.miniapp.platform.repository;

import com.uchat.miniapp.platform.domain.Account;
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

    private static Account map(ResultSet rs, int rowNum) throws SQLException {
        return new Account(
                rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("role"), rs.getString("status"), rs.getString("purpose"),
                rs.getString("plan_description"), rs.getString("developer_name"),
                rs.getString("contact_email"), rs.getString("organization_name"),
                rs.getString("review_note"), nullableLong(rs, "reviewed_by"),
                instant(rs, "reviewed_at"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
