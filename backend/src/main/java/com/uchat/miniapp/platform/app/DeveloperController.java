package com.uchat.miniapp.platform.app;

import com.uchat.miniapp.platform.api.ApiResponse;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/developer")
public class DeveloperController {
    private final MiniAppService service;

    public DeveloperController(MiniAppService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardView> dashboard(HttpServletRequest request) {
        return ApiResponse.ok(service.dashboard(developer(request)));
    }

    @GetMapping("/apps")
    public ApiResponse<List<MiniAppView>> apps(HttpServletRequest request) {
        return ApiResponse.ok(service.list(developer(request)));
    }

    @PostMapping("/apps")
    public ApiResponse<MiniAppView> create(@Valid @RequestBody AppRequests.Create body,
                                           HttpServletRequest request) {
        return ApiResponse.ok(service.create(developer(request), body));
    }

    @GetMapping("/apps/{id}")
    public ApiResponse<MiniAppView> get(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.ok(service.get(id, developer(request)));
    }

    @PutMapping("/apps/{id}")
    public ApiResponse<MiniAppView> update(@PathVariable long id,
                                           @Valid @RequestBody AppRequests.Update body,
                                           HttpServletRequest request) {
        return ApiResponse.ok(service.update(id, developer(request), body));
    }

    @PostMapping("/apps/{id}/assets/{kind}")
    public ApiResponse<MiniAppView> uploadAsset(@PathVariable long id, @PathVariable String kind,
                                                @RequestPart("file") MultipartFile file,
                                                HttpServletRequest request) {
        return ApiResponse.ok(service.uploadAsset(id, developer(request), kind, file));
    }

    @PostMapping("/apps/{id}/versions")
    public ApiResponse<VersionView> uploadVersion(@PathVariable long id,
                                                  @RequestPart("file") MultipartFile file,
                                                  @RequestParam(value = "releaseNotes", required = false)
                                                  String releaseNotes,
                                                  HttpServletRequest request) {
        return ApiResponse.ok(service.uploadVersion(id, developer(request), file, releaseNotes));
    }

    @PostMapping("/apps/{id}/delist")
    public ApiResponse<MiniAppView> delist(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.ok(service.delist(id, developer(request)));
    }

    @GetMapping("/apps/{id}/versions")
    public ApiResponse<List<VersionView>> versions(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.ok(service.versions(id, developer(request)));
    }

    @GetMapping("/apps/{id}/reviews")
    public ApiResponse<ReviewView> reviews(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.ok(service.reviews(id, developer(request)));
    }

    private static Account developer(HttpServletRequest request) {
        return AuthContext.requireDeveloper(request);
    }
}
