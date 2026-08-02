package com.uchat.miniapp.platform.auth;

import com.uchat.miniapp.platform.api.ApiException;
import com.uchat.miniapp.platform.domain.Account;
import jakarta.servlet.http.HttpServletRequest;

public final class AuthContext {
    static final String ACCOUNT_ATTRIBUTE = AuthContext.class.getName() + ".account";
    static final String TOKEN_ATTRIBUTE = AuthContext.class.getName() + ".token";

    private AuthContext() {
    }

    public static Account account(HttpServletRequest request) {
        Object value = request.getAttribute(ACCOUNT_ATTRIBUTE);
        if (value instanceof Account account) {
            return account;
        }
        throw ApiException.unauthorized("AUTH_REQUIRED", "请先登录");
    }

    public static String token(HttpServletRequest request) {
        Object value = request.getAttribute(TOKEN_ATTRIBUTE);
        if (value instanceof String token) {
            return token;
        }
        throw ApiException.unauthorized("AUTH_REQUIRED", "请先登录");
    }

    public static Account requireAdmin(HttpServletRequest request) {
        Account account = account(request);
        if (!"ADMIN".equals(account.role())) {
            throw ApiException.forbidden("ADMIN_REQUIRED", "此操作仅限管理员");
        }
        return account;
    }

    public static Account requireDeveloper(HttpServletRequest request) {
        Account account = requireDeveloperIdentity(request);
        if ("BANNED".equals(account.status())) {
            throw ApiException.forbidden("ACCOUNT_BANNED", "开发者账号已被封禁，仅可查看或提交申诉");
        }
        if (!"APPROVED".equals(account.status())) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "开发者账号当前不可用");
        }
        return account;
    }

    public static Account requireDeveloperIdentity(HttpServletRequest request) {
        Account account = account(request);
        if (!"DEVELOPER".equals(account.role())) {
            throw ApiException.forbidden("DEVELOPER_REQUIRED", "此操作仅限开发者账号");
        }
        return account;
    }
}
