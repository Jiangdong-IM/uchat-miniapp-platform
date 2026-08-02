package com.uchat.miniapp.platform.auth;

import com.uchat.miniapp.platform.api.ApiException;
import com.uchat.miniapp.platform.config.PlatformProperties;
import com.uchat.miniapp.platform.domain.Account;
import com.uchat.miniapp.platform.repository.AccountRepository;
import com.uchat.miniapp.platform.repository.SessionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.nio.charset.StandardCharsets;

@Service
public class AuthService {
    private final AccountRepository accounts;
    private final SessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final PlatformProperties properties;

    public AuthService(AccountRepository accounts, SessionRepository sessions,
                       PasswordEncoder passwordEncoder, PlatformProperties properties) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional
    public AccountView register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.contactEmail().trim().toLowerCase(Locale.ROOT);
        if ("admin".equals(username)) {
            throw ApiException.conflict("USERNAME_RESERVED", "该用户名为平台保留账号");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("PASSWORD_TOO_LONG", "密码的UTF-8编码不能超过72字节");
        }
        if (accounts.usernameOrEmailExists(username, email)) {
            throw ApiException.conflict("ACCOUNT_EXISTS", "用户名或联系邮箱已被使用");
        }
        try {
            long id = accounts.insert(username, passwordEncoder.encode(request.password()),
                    "DEVELOPER", "PENDING", request.purpose().trim(),
                    request.planDescription().trim(), request.developerName().trim(), email,
                    normalizeNullable(request.organizationName()));
            return AccountView.from(accounts.findById(id).orElseThrow());
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict("ACCOUNT_EXISTS", "用户名或联系邮箱已被使用");
        }
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        Account account = accounts.findByUsername(username)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "用户名或密码错误");
        }
        if (!"APPROVED".equals(account.status()) && !"BANNED".equals(account.status())) {
            String code = switch (account.status()) {
                case "PENDING" -> "ACCOUNT_PENDING";
                case "REJECTED" -> "ACCOUNT_REJECTED";
                default -> "ACCOUNT_DISABLED";
            };
            throw new ApiException(HttpStatus.FORBIDDEN, code, "账号尚未获准登录");
        }
        sessions.deleteExpired(Instant.now());
        String token = TokenHasher.randomToken();
        Instant expiresAt = Instant.now().plus(properties.sessionHours(), ChronoUnit.HOURS);
        sessions.insert(account.id(), TokenHasher.sha256(token), expiresAt);
        return new LoginResult(token, expiresAt, AccountView.from(account));
    }

    public Account authenticate(String token) {
        return sessions.findActiveAccount(TokenHasher.sha256(token), Instant.now())
                .orElseThrow(() -> ApiException.unauthorized("INVALID_SESSION", "登录已失效，请重新登录"));
    }

    public void logout(String token) {
        sessions.delete(TokenHasher.sha256(token));
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record LoginResult(String token, Instant expiresAt, AccountView account) {
    }
}
