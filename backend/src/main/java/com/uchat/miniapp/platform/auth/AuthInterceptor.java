package com.uchat.miniapp.platform.auth;

import com.uchat.miniapp.platform.api.ApiException;
import com.uchat.miniapp.platform.domain.Account;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw ApiException.unauthorized("AUTH_REQUIRED", "请先登录");
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            throw ApiException.unauthorized("AUTH_REQUIRED", "请先登录");
        }
        Account account = authService.authenticate(token);
        if (!"APPROVED".equals(account.status())) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "账号当前不可用");
        }
        request.setAttribute(AuthContext.ACCOUNT_ATTRIBUTE, account);
        request.setAttribute(AuthContext.TOKEN_ATTRIBUTE, token);
        return true;
    }
}
