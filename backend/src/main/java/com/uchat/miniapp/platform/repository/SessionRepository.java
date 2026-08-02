package com.uchat.miniapp.platform.repository;

import com.uchat.miniapp.platform.domain.Account;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class SessionRepository {
    private final JdbcTemplate jdbc;
    private final AccountRepository accounts;

    public SessionRepository(JdbcTemplate jdbc, AccountRepository accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    public void insert(long accountId, String tokenHash, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO mini_app_platform_session
                    (account_id, token_hash, expires_at, created_at)
                VALUES (?, ?, ?, ?)
                """, accountId, tokenHash, Timestamp.from(expiresAt), Timestamp.from(Instant.now()));
    }

    public Optional<Account> findActiveAccount(String tokenHash, Instant now) {
        return jdbc.query("""
                SELECT a.id FROM mini_app_platform_session s
                JOIN mini_app_developer_account a ON a.id = s.account_id
                WHERE s.token_hash = ? AND s.expires_at > ?
                """, (rs, rowNum) -> rs.getLong(1), tokenHash, Timestamp.from(now))
                .stream().findFirst().flatMap(accounts::findById);
    }

    public void delete(String tokenHash) {
        jdbc.update("DELETE FROM mini_app_platform_session WHERE token_hash = ?", tokenHash);
    }

    public void deleteExpired(Instant now) {
        jdbc.update("DELETE FROM mini_app_platform_session WHERE expires_at <= ?", Timestamp.from(now));
    }
}
