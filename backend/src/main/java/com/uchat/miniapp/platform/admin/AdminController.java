package com.uchat.miniapp.platform.admin;

import com.uchat.miniapp.platform.api.ApiResponse;
import com.uchat.miniapp.platform.app.VersionView;
import com.uchat.miniapp.platform.auth.AccountView;
import com.uchat.miniapp.platform.auth.AuthContext;
import com.uchat.miniapp.platform.domain.Account;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/registrations")
    public ApiResponse<List<AccountView>> registrations(
            @RequestParam(required = false) String status, HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.registrations(status));
    }

    @PostMapping("/registrations/{id}/decision")
    public ApiResponse<AccountView> decideRegistration(@PathVariable long id,
                                                        @Valid @RequestBody AdminRequests.Decision body,
                                                        HttpServletRequest request) {
        return ApiResponse.ok(service.decideRegistration(id, body, admin(request)));
    }

    @GetMapping("/versions")
    public ApiResponse<List<VersionView>> versions(@RequestParam(required = false) String status,
                                                   HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.versionQueue(status));
    }

    @PostMapping("/versions/{id}/decision")
    public ApiResponse<VersionView> decideVersion(@PathVariable long id,
                                                  @Valid @RequestBody AdminRequests.Decision body,
                                                  HttpServletRequest request) {
        return ApiResponse.ok(service.decideVersion(id, body, admin(request)));
    }

    @GetMapping("/comments")
    public ApiResponse<List<AdminCommentView>> comments(HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.comments());
    }

    @PutMapping("/comments/{id}/featured")
    public ApiResponse<AdminService.FeaturedResult> setFeatured(
            @PathVariable long id, @RequestBody AdminRequests.Featured body,
            HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.setFeatured(id, body.featured()));
    }

    private static Account admin(HttpServletRequest request) {
        return AuthContext.requireAdmin(request);
    }
}
