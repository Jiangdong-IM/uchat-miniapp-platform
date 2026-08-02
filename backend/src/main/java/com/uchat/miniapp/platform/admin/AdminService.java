package com.uchat.miniapp.platform.admin;

import com.uchat.miniapp.platform.api.ApiException;
import com.uchat.miniapp.platform.app.MiniAppService;
import com.uchat.miniapp.platform.app.VersionView;
import com.uchat.miniapp.platform.auth.AccountView;
import com.uchat.miniapp.platform.domain.Account;
import com.uchat.miniapp.platform.domain.MiniApp;
import com.uchat.miniapp.platform.domain.MiniAppVersion;
import com.uchat.miniapp.platform.integration.MiniAppReleaseGateway;
import com.uchat.miniapp.platform.repository.AccountRepository;
import com.uchat.miniapp.platform.repository.MiniAppRepository;
import com.uchat.miniapp.platform.repository.ReviewRepository;
import com.uchat.miniapp.platform.repository.VersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class AdminService {
    private static final Set<String> ACCOUNT_STATUSES = Set.of(
            "PENDING", "APPROVED", "REJECTED", "DISABLED");
    private static final Set<String> VERSION_STATUSES = Set.of(
            "PENDING_REVIEW", "APPROVED", "REJECTED");
    private final AccountRepository accounts;
    private final MiniAppRepository apps;
    private final VersionRepository versions;
    private final ReviewRepository reviews;
    private final MiniAppReleaseGateway releaseGateway;
    private final MiniAppService miniAppService;

    public AdminService(AccountRepository accounts, MiniAppRepository apps,
                        VersionRepository versions, ReviewRepository reviews,
                        MiniAppReleaseGateway releaseGateway, MiniAppService miniAppService) {
        this.accounts = accounts;
        this.apps = apps;
        this.versions = versions;
        this.reviews = reviews;
        this.releaseGateway = releaseGateway;
        this.miniAppService = miniAppService;
    }

    public List<AccountView> registrations(String status) {
        String normalized = normalizeFilter(status, ACCOUNT_STATUSES, "INVALID_ACCOUNT_STATUS");
        return accounts.findRegistrations(normalized).stream().map(AccountView::from).toList();
    }

    @Transactional
    public AccountView decideRegistration(long id, AdminRequests.Decision request, Account admin) {
        String decision = request.decision().trim().toUpperCase();
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw ApiException.badRequest("INVALID_DECISION", "注册审核决定只能是APPROVED或REJECTED");
        }
        int changed = accounts.reviewPending(id, decision, normalizeNote(request.reviewNote()), admin.id());
        if (changed == 0) {
            Account existing = accounts.findById(id)
                    .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "开发者账号不存在"));
            if (!"DEVELOPER".equals(existing.role())) {
                throw ApiException.notFound("ACCOUNT_NOT_FOUND", "开发者账号不存在");
            }
            throw ApiException.conflict("ACCOUNT_ALREADY_REVIEWED", "该注册申请已经审核");
        }
        return AccountView.from(accounts.findById(id).orElseThrow());
    }

    public List<VersionView> versionQueue(String status) {
        String normalized = normalizeFilter(status, VERSION_STATUSES, "INVALID_VERSION_STATUS");
        return versions.findByStatus(normalized).stream().map(version -> {
            MiniApp app = apps.findById(version.miniAppId())
                    .orElseThrow(() -> new IllegalStateException("Version references missing app"));
            return miniAppService.versionView(version, app);
        }).toList();
    }

    public List<AdminCommentView> comments() {
        return reviews.adminComments().stream()
                .map(comment -> new AdminCommentView(
                        comment.id(), comment.appId(), comment.appName(),
                        comment.userDisplayName(), comment.content(), comment.featured(),
                        comment.status(), comment.createdAt()))
                .toList();
    }

    @Transactional
    public VersionView decideVersion(long id, AdminRequests.Decision request, Account admin) {
        String decision = request.decision().trim().toUpperCase();
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw ApiException.badRequest("INVALID_DECISION", "版本审核决定只能是APPROVED或REJECTED");
        }
        MiniAppVersion version = versions.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("VERSION_NOT_FOUND", "版本不存在"));
        if (!"PENDING_REVIEW".equals(version.status())) {
            throw ApiException.conflict("VERSION_ALREADY_REVIEWED", "该版本已经审核");
        }
        MiniApp app = apps.findByIdForUpdate(version.miniAppId())
                .orElseThrow(() -> ApiException.notFound("APP_NOT_FOUND", "小程序不存在"));
        String note = normalizeNote(request.reviewNote());
        if ("APPROVED".equals(decision)) {
            if (app.iconObjectKey() == null || app.coverObjectKey() == null) {
                throw ApiException.conflict("ASSETS_REQUIRED", "小程序缺少图标或封面，不能通过审核");
            }
            if (!app.appId().equals(version.manifestAppId())
                    || !app.name().equals(version.manifestName())
                    || !app.description().equals(version.manifestDescription())) {
                throw ApiException.conflict("APP_CHANGED_AFTER_SUBMISSION", "小程序资料已在提交后更改，请驳回并重新提交版本");
            }
            VersionView view = miniAppService.versionView(version, app);
            VersionView.Manifest manifest = view.manifest();
            releaseGateway.activate(new MiniAppReleaseGateway.ActivationRequest(
                    manifest.appId(), manifest.name(), manifest.version(), manifest.entry(),
                    manifest.schemaVersion(), manifest.permissions(), manifest.description(),
                    manifest.objectKey(), manifest.archiveSha256(), manifest.archiveSize(), admin.id()));
            if (versions.review(id, "PENDING_REVIEW", "APPROVED", note, admin.id()) != 1) {
                throw ApiException.conflict("VERSION_ALREADY_REVIEWED", "该版本已经审核");
            }
            apps.activate(app.id(), version.id());
        } else if (versions.review(id, "PENDING_REVIEW", "REJECTED", note, admin.id()) != 1) {
            throw ApiException.conflict("VERSION_ALREADY_REVIEWED", "该版本已经审核");
        }
        return miniAppService.versionView(versions.findById(id).orElseThrow(),
                apps.findById(app.id()).orElseThrow());
    }

    @Transactional
    public FeaturedResult setFeatured(long commentId, boolean featured) {
        if (reviews.setFeatured(commentId, featured) == 0) {
            throw ApiException.notFound("COMMENT_NOT_FOUND", "评论不存在");
        }
        return new FeaturedResult(commentId, featured);
    }

    private static String normalizeFilter(String value, Set<String> accepted, String errorCode) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (!accepted.contains(normalized)) {
            throw ApiException.badRequest(errorCode, "筛选状态无效");
        }
        return normalized;
    }

    private static String normalizeNote(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record FeaturedResult(long id, boolean featured) {
    }
}
