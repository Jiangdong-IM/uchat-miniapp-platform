package com.uchat.miniapp.platform.admin;

import com.uchat.miniapp.platform.api.ApiResponse;
import com.uchat.miniapp.platform.app.VersionView;
import com.uchat.miniapp.platform.auth.AccountView;
import com.uchat.miniapp.platform.auth.AuthContext;
import com.uchat.miniapp.platform.domain.Account;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/apps")
    public ApiResponse<AdminPage<AdminMiniAppView>> apps(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.apps(keyword, status, page, pageSize));
    }

    @GetMapping("/apps/{id}")
    public ApiResponse<AdminMiniAppView> app(@PathVariable long id, HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.app(id));
    }

    @PutMapping("/apps/{id}")
    public ApiResponse<AdminMiniAppView> updateApp(
            @PathVariable long id, @Valid @RequestBody AdminRequests.UpdateApp body,
            HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.updateApp(id, body));
    }

    @PostMapping("/apps/{id}/assets/{kind}")
    public ApiResponse<AdminMiniAppView> uploadAppAsset(
            @PathVariable long id, @PathVariable String kind,
            @RequestPart("file") MultipartFile file, HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.uploadAppAsset(id, kind, file));
    }

    @PostMapping("/apps/{id}/publish")
    public ApiResponse<AdminMiniAppView> publishApp(
            @PathVariable long id, HttpServletRequest request) {
        return ApiResponse.ok(service.publishApp(id, admin(request)));
    }

    @PostMapping("/apps/{id}/delist")
    public ApiResponse<AdminMiniAppView> delistApp(
            @PathVariable long id, HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.delistApp(id));
    }

    @GetMapping("/comments")
    public ApiResponse<AdminPage<AdminCommentView>> comments(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long miniAppId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.comments(keyword, status, miniAppId, page, pageSize));
    }

    @GetMapping("/comments/{id}")
    public ApiResponse<AdminCommentView> comment(@PathVariable long id,
                                                 HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.comment(id));
    }

    @PostMapping("/comments")
    public ApiResponse<AdminCommentView> createComment(
            @Valid @RequestBody AdminRequests.CreateComment body,
            HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.createComment(body));
    }

    @PutMapping("/comments/{id}")
    public ApiResponse<AdminCommentView> updateComment(
            @PathVariable long id, @Valid @RequestBody AdminRequests.UpdateComment body,
            HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.updateComment(id, body));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<AdminService.DeletedResult> deleteComment(
            @PathVariable long id, HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.deleteComment(id));
    }

    @PutMapping("/comments/{id}/featured")
    public ApiResponse<AdminService.FeaturedResult> setFeatured(
            @PathVariable long id, @RequestBody AdminRequests.Featured body,
            HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.setFeatured(id, body.featured()));
    }

    @GetMapping("/developers")
    public ApiResponse<AdminPage<AdminDeveloperView>> developers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.developers(keyword, status, page, pageSize));
    }

    @PostMapping("/developers/{id}/ban")
    public ApiResponse<AdminService.DeveloperModerationResult> banDeveloper(
            @PathVariable long id, @Valid @RequestBody AdminRequests.BanDeveloper body,
            HttpServletRequest request) {
        return ApiResponse.ok(service.banDeveloper(id, body, admin(request)));
    }

    @PostMapping("/developers/{id}/unban")
    public ApiResponse<AdminService.DeveloperModerationResult> unbanDeveloper(
            @PathVariable long id, @Valid @RequestBody AdminRequests.UnbanDeveloper body,
            HttpServletRequest request) {
        return ApiResponse.ok(service.unbanDeveloper(id, body, admin(request)));
    }

    @GetMapping("/appeals")
    public ApiResponse<AdminPage<AppealView>> appeals(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long developerAccountId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        admin(request);
        return ApiResponse.ok(service.appeals(status, developerAccountId, page, pageSize));
    }

    @PostMapping("/appeals/{id}/decision")
    public ApiResponse<AppealView> decideAppeal(
            @PathVariable long id, @Valid @RequestBody AdminRequests.Decision body,
            HttpServletRequest request) {
        return ApiResponse.ok(service.decideAppeal(id, body, admin(request)));
    }

    private static Account admin(HttpServletRequest request) {
        return AuthContext.requireAdmin(request);
    }
}
