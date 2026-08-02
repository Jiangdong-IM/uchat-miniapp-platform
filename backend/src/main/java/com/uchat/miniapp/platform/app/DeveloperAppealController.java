package com.uchat.miniapp.platform.app;

import com.uchat.miniapp.platform.admin.AdminRequests;
import com.uchat.miniapp.platform.admin.AdminService;
import com.uchat.miniapp.platform.admin.AppealView;
import com.uchat.miniapp.platform.api.ApiResponse;
import com.uchat.miniapp.platform.auth.AuthContext;
import com.uchat.miniapp.platform.domain.Account;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/developer/appeals")
public class DeveloperAppealController {
    private final AdminService service;

    public DeveloperAppealController(AdminService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AppealView>> list(HttpServletRequest request) {
        return ApiResponse.ok(service.developerAppeals(developerIdentity(request)));
    }

    @PostMapping
    public ApiResponse<AppealView> create(
            @Valid @RequestBody AdminRequests.CreateAppeal body,
            HttpServletRequest request) {
        return ApiResponse.ok(service.createAppeal(developerIdentity(request), body));
    }

    private static Account developerIdentity(HttpServletRequest request) {
        return AuthContext.requireDeveloperIdentity(request);
    }
}
