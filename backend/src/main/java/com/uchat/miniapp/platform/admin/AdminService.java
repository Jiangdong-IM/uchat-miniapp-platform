package com.uchat.miniapp.platform.admin;

import com.uchat.miniapp.platform.api.ApiException;
import com.uchat.miniapp.platform.app.MiniAppService;
import com.uchat.miniapp.platform.app.VersionView;
import com.uchat.miniapp.platform.auth.AccountView;
import com.uchat.miniapp.platform.domain.Account;
import com.uchat.miniapp.platform.domain.DeveloperAppeal;
import com.uchat.miniapp.platform.domain.MiniApp;
import com.uchat.miniapp.platform.domain.MiniAppVersion;
import com.uchat.miniapp.platform.integration.MiniAppReleaseGateway;
import com.uchat.miniapp.platform.repository.AccountRepository;
import com.uchat.miniapp.platform.repository.AppealRepository;
import com.uchat.miniapp.platform.repository.MiniAppRepository;
import com.uchat.miniapp.platform.repository.ReviewRepository;
import com.uchat.miniapp.platform.repository.VersionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AdminService {
    private static final Set<String> ACCOUNT_STATUSES = Set.of(
            "PENDING", "APPROVED", "REJECTED", "DISABLED", "BANNED");
    private static final Set<String> VERSION_STATUSES = Set.of(
            "PENDING_REVIEW", "APPROVED", "REJECTED");
    private static final Set<String> APP_STATUSES = Set.of("DRAFT", "PUBLISHED", "DELISTED");
    private static final Set<String> COMMENT_STATUSES = Set.of("VISIBLE", "HIDDEN");
    private static final Set<String> APPEAL_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");

    private final AccountRepository accounts;
    private final MiniAppRepository apps;
    private final VersionRepository versions;
    private final ReviewRepository reviews;
    private final AppealRepository appeals;
    private final MiniAppReleaseGateway releaseGateway;
    private final MiniAppService miniAppService;

    public AdminService(AccountRepository accounts, MiniAppRepository apps,
                        VersionRepository versions, ReviewRepository reviews,
                        AppealRepository appeals, MiniAppReleaseGateway releaseGateway,
                        MiniAppService miniAppService) {
        this.accounts = accounts;
        this.apps = apps;
        this.versions = versions;
        this.reviews = reviews;
        this.appeals = appeals;
        this.releaseGateway = releaseGateway;
        this.miniAppService = miniAppService;
    }

    public List<AccountView> registrations(String status) {
        String normalized = normalizeFilter(status, ACCOUNT_STATUSES, "INVALID_ACCOUNT_STATUS");
        return accounts.findRegistrations(normalized).stream().map(AccountView::from).toList();
    }

    @Transactional
    public AccountView decideRegistration(long id, AdminRequests.Decision request, Account admin) {
        String decision = normalizeDecision(request.decision(), "注册审核决定只能是APPROVED或REJECTED");
        int changed = accounts.reviewPending(id, decision, normalizeNote(request.reviewNote()), admin.id());
        if (changed == 0) {
            Account existing = requireDeveloper(id);
            if (!"PENDING".equals(existing.status())) {
                throw ApiException.conflict("ACCOUNT_ALREADY_REVIEWED", "该注册申请已经审核");
            }
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

    @Transactional
    public VersionView decideVersion(long id, AdminRequests.Decision request, Account admin) {
        String decision = normalizeDecision(request.decision(), "版本审核决定只能是APPROVED或REJECTED");
        MiniAppVersion versionSnapshot = versions.findById(id)
                .orElseThrow(() -> ApiException.notFound("VERSION_NOT_FOUND", "版本不存在"));
        if (!"PENDING_REVIEW".equals(versionSnapshot.status())) {
            throw ApiException.conflict("VERSION_ALREADY_REVIEWED", "该版本已经审核");
        }
        MiniApp appSnapshot = apps.findById(versionSnapshot.miniAppId())
                .orElseThrow(() -> ApiException.notFound("APP_NOT_FOUND", "小程序不存在"));
        String note = normalizeNote(request.reviewNote());
        if ("REJECTED".equals(decision)) {
            MiniAppVersion version = versions.findByIdForUpdate(id)
                    .orElseThrow(() -> ApiException.notFound("VERSION_NOT_FOUND", "版本不存在"));
            if (!"PENDING_REVIEW".equals(version.status())) {
                throw ApiException.conflict("VERSION_ALREADY_REVIEWED", "该版本已经审核");
            }
            if (versions.review(id, "PENDING_REVIEW", "REJECTED", note, admin.id()) != 1) {
                throw ApiException.conflict("VERSION_ALREADY_REVIEWED", "该版本已经审核");
            }
            return miniAppService.versionView(versions.findById(id).orElseThrow(), appSnapshot);
        }
        requirePublishableDeveloperForUpdate(appSnapshot.developerAccountId());
        MiniApp app = apps.findByIdForUpdate(appSnapshot.id())
                .orElseThrow(() -> ApiException.notFound("APP_NOT_FOUND", "小程序不存在"));
        MiniAppVersion version = versions.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("VERSION_NOT_FOUND", "版本不存在"));
        if (!"PENDING_REVIEW".equals(version.status())) {
            throw ApiException.conflict("VERSION_ALREADY_REVIEWED", "该版本已经审核");
        }
        if (version.miniAppId() != app.id()
                || app.developerAccountId() != appSnapshot.developerAccountId()) {
            throw ApiException.conflict("APP_VERSION_CHANGED", "小程序或版本归属已经变化");
        }
        requireAssets(app);
        if (!app.appId().equals(version.manifestAppId())
                || !app.name().equals(version.manifestName())
                || !app.description().equals(version.manifestDescription())) {
            throw ApiException.conflict("APP_CHANGED_AFTER_SUBMISSION", "小程序资料已在提交后更改，请驳回并重新提交版本");
        }
        activate(version, app, admin.id());
        if (versions.review(id, "PENDING_REVIEW", "APPROVED", note, admin.id()) != 1) {
            throw ApiException.conflict("VERSION_ALREADY_REVIEWED", "该版本已经审核");
        }
        apps.activate(app.id(), version.id());
        return miniAppService.versionView(versions.findById(id).orElseThrow(),
                apps.findById(app.id()).orElseThrow());
    }

    public AdminPage<AdminMiniAppView> apps(String keyword, String status, int page, int pageSize) {
        Page range = page(page, pageSize);
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeFilter(status, APP_STATUSES, "INVALID_APP_STATUS");
        List<AdminMiniAppView> items = apps.findAdmin(normalizedKeyword, normalizedStatus,
                        range.offset(), range.pageSize()).stream().map(this::appView).toList();
        return new AdminPage<>(items, range.page(), range.pageSize(),
                apps.countAdmin(normalizedKeyword, normalizedStatus));
    }

    public AdminMiniAppView app(long id) {
        return appView(requireApp(id));
    }

    @Transactional
    public AdminMiniAppView updateApp(long id, AdminRequests.UpdateApp request) {
        MiniApp app = apps.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("APP_NOT_FOUND", "小程序不存在"));
        String name = request.name().trim();
        String description = request.description().trim();
        if ("PUBLISHED".equals(app.status())
                && (!app.name().equals(name) || !app.description().equals(description))) {
            throw ApiException.conflict("APP_MUST_BE_DELISTED_FOR_METADATA_UPDATE",
                    "已上架小程序必须先下架，才能修改名称或简介");
        }
        apps.updateMetadataAdmin(id, name, description);
        return appView(requireApp(id));
    }

    @Transactional
    public AdminMiniAppView uploadAppAsset(long id, String kind, MultipartFile file) {
        if (!"icon".equals(kind) && !"cover".equals(kind)) {
            throw ApiException.badRequest("INVALID_ASSET_KIND", "资产类型只能是icon或cover");
        }
        MiniApp app = requireApp(id);
        String objectKey = releaseGateway.uploadAsset(app.appId(), kind, file);
        apps.updateAssetAdmin(id, kind, objectKey);
        return appView(requireApp(id));
    }

    @Transactional
    public AdminMiniAppView publishApp(long id, Account admin) {
        MiniApp snapshot = requireApp(id);
        requirePublishableDeveloperForUpdate(snapshot.developerAccountId());
        MiniApp app = apps.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("APP_NOT_FOUND", "小程序不存在"));
        if (app.developerAccountId() != snapshot.developerAccountId()) {
            throw ApiException.conflict("APP_OWNER_CHANGED", "小程序归属已经变化");
        }
        if ("PUBLISHED".equals(app.status())) {
            throw ApiException.conflict("APP_ALREADY_PUBLISHED", "小程序已经上架");
        }
        requireAssets(app);
        MiniAppVersion version = requireApprovedCurrentVersion(app);
        if (!app.appId().equals(version.manifestAppId())
                || !app.name().equals(version.manifestName())
                || !app.description().equals(version.manifestDescription())) {
            throw ApiException.conflict("APP_CHANGED_AFTER_APPROVAL", "小程序资料与已审核版本不一致，请先提交匹配的新版本");
        }
        activate(version, app, admin.id());
        apps.activate(app.id(), version.id());
        return appView(requireApp(id));
    }

    @Transactional
    public AdminMiniAppView delistApp(long id) {
        MiniApp snapshot = requireApp(id);
        accounts.findByIdForUpdate(snapshot.developerAccountId())
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "开发者账号不存在"));
        MiniApp app = apps.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("APP_NOT_FOUND", "小程序不存在"));
        if (app.developerAccountId() != snapshot.developerAccountId()) {
            throw ApiException.conflict("APP_OWNER_CHANGED", "小程序归属已经变化");
        }
        if (!"PUBLISHED".equals(app.status())) {
            throw ApiException.conflict("APP_NOT_PUBLISHED", "只有已上架的小程序可以下架");
        }
        releaseGateway.deactivate(app.appId());
        apps.delistAdmin(id);
        return appView(requireApp(id));
    }

    public AdminPage<AdminCommentView> comments(String keyword, String status, Long miniAppId,
                                                int page, int pageSize) {
        Page range = page(page, pageSize);
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeFilter(status, COMMENT_STATUSES, "INVALID_COMMENT_STATUS");
        if (miniAppId != null && miniAppId < 1) {
            throw ApiException.badRequest("INVALID_APP_ID", "miniAppId无效");
        }
        List<AdminCommentView> items = reviews.adminComments(normalizedKeyword, normalizedStatus,
                        miniAppId, range.offset(), range.pageSize()).stream()
                .map(AdminService::commentView).toList();
        return new AdminPage<>(items, range.page(), range.pageSize(),
                reviews.countAdminComments(normalizedKeyword, normalizedStatus, miniAppId));
    }

    public AdminCommentView comment(long id) {
        return commentView(reviews.findAdminComment(id)
                .orElseThrow(() -> ApiException.notFound("COMMENT_NOT_FOUND", "评论不存在")));
    }

    @Transactional
    public AdminCommentView createComment(AdminRequests.CreateComment request) {
        requireApp(request.miniAppId());
        String status = normalizeFilter(request.status(), COMMENT_STATUSES, "INVALID_COMMENT_STATUS");
        boolean featured = "VISIBLE".equals(status) && request.featured();
        try {
            long id = reviews.insertComment(request.miniAppId(), request.uchatUserId(),
                    request.userDisplayName().trim(), request.content().trim(), featured, status);
            return comment(id);
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict("COMMENT_EXISTS", "该UChat用户已经评论过此小程序");
        }
    }

    @Transactional
    public AdminCommentView updateComment(long id, AdminRequests.UpdateComment request) {
        String status = normalizeFilter(request.status(), COMMENT_STATUSES, "INVALID_COMMENT_STATUS");
        boolean featured = "VISIBLE".equals(status) && request.featured();
        if (reviews.updateComment(id, request.userDisplayName().trim(), request.content().trim(),
                featured, status) == 0) {
            throw ApiException.notFound("COMMENT_NOT_FOUND", "评论不存在");
        }
        return comment(id);
    }

    @Transactional
    public DeletedResult deleteComment(long id) {
        if (reviews.deleteComment(id) == 0) {
            throw ApiException.notFound("COMMENT_NOT_FOUND", "评论不存在");
        }
        return new DeletedResult(id);
    }

    @Transactional
    public FeaturedResult setFeatured(long commentId, boolean featured) {
        if (reviews.setFeatured(commentId, featured) == 0) {
            ReviewRepository.AdminCommentRow comment = reviews.findAdminComment(commentId)
                    .orElseThrow(() -> ApiException.notFound("COMMENT_NOT_FOUND", "评论不存在"));
            if (featured && !"VISIBLE".equals(comment.status())) {
                throw ApiException.conflict("HIDDEN_COMMENT_NOT_FEATURED", "隐藏评论不能设为精选");
            }
        }
        return new FeaturedResult(commentId, featured);
    }

    public AdminPage<AdminDeveloperView> developers(String keyword, String status,
                                                     int page, int pageSize) {
        Page range = page(page, pageSize);
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeFilter(status, ACCOUNT_STATUSES, "INVALID_ACCOUNT_STATUS");
        List<AdminDeveloperView> items = accounts.findDevelopers(normalizedKeyword, normalizedStatus,
                        range.offset(), range.pageSize()).stream().map(this::developerView).toList();
        return new AdminPage<>(items, range.page(), range.pageSize(),
                accounts.countDevelopers(normalizedKeyword, normalizedStatus));
    }

    @Transactional
    public DeveloperModerationResult banDeveloper(long id, AdminRequests.BanDeveloper request,
                                                   Account admin) {
        Account developer = accounts.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "开发者账号不存在"));
        requireDeveloperRole(developer);
        if ("BANNED".equals(developer.status())) {
            throw ApiException.conflict("DEVELOPER_ALREADY_BANNED", "开发者已经被封禁");
        }
        if (!"APPROVED".equals(developer.status())) {
            throw ApiException.conflict("DEVELOPER_NOT_ACTIVE", "只有已通过审核的开发者可以被封禁");
        }
        List<MiniApp> published = apps.findPublishedByDeveloper(id);
        if (accounts.banDeveloper(id, request.reason().trim(), admin.id()) != 1) {
            throw ApiException.conflict("DEVELOPER_STATUS_CHANGED", "开发者状态已经变化");
        }
        for (MiniApp app : published) {
            releaseGateway.deactivate(app.appId());
        }
        int delisted = apps.delistPublishedByDeveloper(id);
        if (delisted != published.size()) {
            throw new IllegalStateException("Published mini app count changed during developer ban");
        }
        return new DeveloperModerationResult(developerView(requireDeveloper(id)), delisted);
    }

    @Transactional
    public DeveloperModerationResult unbanDeveloper(long id, AdminRequests.UnbanDeveloper request,
                                                     Account admin) {
        Account developer = accounts.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "开发者账号不存在"));
        requireDeveloperRole(developer);
        if (!"BANNED".equals(developer.status())) {
            throw ApiException.conflict("DEVELOPER_NOT_BANNED", "开发者当前未被封禁");
        }
        if (accounts.unbanDeveloper(id) != 1) {
            throw ApiException.conflict("DEVELOPER_STATUS_CHANGED", "开发者状态已经变化");
        }
        appeals.approvePendingForDeveloper(id, normalizeNote(request.note()), admin.id());
        return new DeveloperModerationResult(developerView(requireDeveloper(id)), 0);
    }

    public List<AppealView> developerAppeals(Account developer) {
        return appeals.findByDeveloper(developer.id()).stream()
                .map(appeal -> AppealView.from(appeal, developer)).toList();
    }

    @Transactional
    public AppealView createAppeal(Account authenticated, AdminRequests.CreateAppeal request) {
        Account developer = accounts.findByIdForUpdate(authenticated.id())
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "开发者账号不存在"));
        requireDeveloperRole(developer);
        if (!"BANNED".equals(developer.status())) {
            throw ApiException.conflict("APPEAL_NOT_ALLOWED", "只有被封禁的开发者可以提交申诉");
        }
        if (appeals.hasPending(developer.id())) {
            throw ApiException.conflict("APPEAL_ALREADY_PENDING", "已有待处理申诉，不能重复提交");
        }
        long id = appeals.insert(developer.id(), request.content().trim());
        return AppealView.from(appeals.findById(id).orElseThrow(), developer);
    }

    public AdminPage<AppealView> appeals(String status, Long developerAccountId,
                                         int page, int pageSize) {
        Page range = page(page, pageSize);
        String normalizedStatus = normalizeFilter(status, APPEAL_STATUSES, "INVALID_APPEAL_STATUS");
        if (developerAccountId != null && developerAccountId < 1) {
            throw ApiException.badRequest("INVALID_DEVELOPER_ID", "developerAccountId无效");
        }
        List<AppealView> items = appeals.findAdmin(normalizedStatus, developerAccountId,
                        range.offset(), range.pageSize()).stream().map(this::appealView).toList();
        return new AdminPage<>(items, range.page(), range.pageSize(),
                appeals.countAdmin(normalizedStatus, developerAccountId));
    }

    @Transactional
    public AppealView decideAppeal(long id, AdminRequests.Decision request, Account admin) {
        String decision = normalizeDecision(request.decision(), "申诉决定只能是APPROVED或REJECTED");
        DeveloperAppeal snapshot = appeals.findById(id)
                .orElseThrow(() -> ApiException.notFound("APPEAL_NOT_FOUND", "申诉不存在"));
        if (!"PENDING".equals(snapshot.status())) {
            throw ApiException.conflict("APPEAL_ALREADY_REVIEWED", "申诉已经处理");
        }
        Account developer = accounts.findByIdForUpdate(snapshot.developerAccountId())
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "开发者账号不存在"));
        DeveloperAppeal appeal = appeals.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("APPEAL_NOT_FOUND", "申诉不存在"));
        if (!"PENDING".equals(appeal.status())
                || appeal.developerAccountId() != developer.id()) {
            throw ApiException.conflict("APPEAL_ALREADY_REVIEWED", "申诉已经处理");
        }
        String note = normalizeNote(request.reviewNote());
        if (appeals.decide(id, decision, note, admin.id()) != 1) {
            throw ApiException.conflict("APPEAL_ALREADY_REVIEWED", "申诉已经处理");
        }
        if ("APPROVED".equals(decision) && "BANNED".equals(developer.status())) {
            if (accounts.unbanDeveloper(developer.id()) != 1) {
                throw ApiException.conflict("DEVELOPER_STATUS_CHANGED", "开发者状态已经变化");
            }
        }
        return appealView(appeals.findById(id).orElseThrow());
    }

    private void activate(MiniAppVersion version, MiniApp app, long adminId) {
        VersionView.Manifest manifest = miniAppService.versionView(version, app).manifest();
        releaseGateway.activate(new MiniAppReleaseGateway.ActivationRequest(
                manifest.appId(), manifest.name(), manifest.version(), manifest.entry(),
                manifest.schemaVersion(), manifest.permissions(), manifest.description(),
                manifest.objectKey(), manifest.archiveSha256(), manifest.archiveSize(), adminId));
    }

    private MiniAppVersion requireApprovedCurrentVersion(MiniApp app) {
        if (app.currentVersionId() == null) {
            throw ApiException.conflict("APP_VERSION_REQUIRED", "小程序没有可上架的已审核版本");
        }
        MiniAppVersion version = versions.findById(app.currentVersionId())
                .orElseThrow(() -> ApiException.conflict("APP_VERSION_REQUIRED", "小程序当前版本不存在"));
        if (version.miniAppId() != app.id() || !"APPROVED".equals(version.status())) {
            throw ApiException.conflict("APP_VERSION_NOT_APPROVED", "小程序当前版本尚未通过审核");
        }
        return version;
    }

    private void requirePublishableDeveloperForUpdate(long developerId) {
        Account developer = accounts.findByIdForUpdate(developerId)
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "开发者账号不存在"));
        requireDeveloperRole(developer);
        if ("BANNED".equals(developer.status())) {
            throw ApiException.conflict("DEVELOPER_BANNED", "被封禁开发者的小程序不能上架或通过审核");
        }
        if (!"APPROVED".equals(developer.status())) {
            throw ApiException.conflict("DEVELOPER_NOT_ACTIVE", "开发者账号当前不可发布小程序");
        }
    }

    private static void requireAssets(MiniApp app) {
        if (app.iconObjectKey() == null || app.coverObjectKey() == null) {
            throw ApiException.conflict("ASSETS_REQUIRED", "小程序缺少图标或封面，不能上架");
        }
    }

    private MiniApp requireApp(long id) {
        return apps.findById(id)
                .orElseThrow(() -> ApiException.notFound("APP_NOT_FOUND", "小程序不存在"));
    }

    private Account requireDeveloper(long id) {
        Account account = accounts.findById(id)
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "开发者账号不存在"));
        requireDeveloperRole(account);
        return account;
    }

    private static void requireDeveloperRole(Account account) {
        if (!"DEVELOPER".equals(account.role())) {
            throw ApiException.notFound("ACCOUNT_NOT_FOUND", "开发者账号不存在");
        }
    }

    private AdminMiniAppView appView(MiniApp app) {
        return AdminMiniAppView.from(app, miniAppService.view(app));
    }

    private AdminDeveloperView developerView(Account developer) {
        return AdminDeveloperView.from(developer, apps.countByDeveloper(developer.id()),
                apps.countPublished(developer.id()));
    }

    private AppealView appealView(DeveloperAppeal appeal) {
        return AppealView.from(appeal, requireDeveloper(appeal.developerAccountId()));
    }

    private static AdminCommentView commentView(ReviewRepository.AdminCommentRow comment) {
        return new AdminCommentView(comment.id(), comment.miniAppId(), comment.appId(),
                comment.appName(), comment.uchatUserId(), comment.userDisplayName(),
                comment.content(), comment.featured(), comment.status(), comment.createdAt(),
                comment.updatedAt());
    }

    private static Page page(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw ApiException.badRequest("INVALID_PAGE", "page必须大于等于1，pageSize必须在1到100之间");
        }
        long offset = (long) (page - 1) * pageSize;
        if (offset > Integer.MAX_VALUE) {
            throw ApiException.badRequest("INVALID_PAGE", "分页偏移量过大");
        }
        return new Page(page, pageSize, (int) offset);
    }

    private static String normalizeKeyword(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 120) {
            throw ApiException.badRequest("SEARCH_KEYWORD_TOO_LONG", "搜索关键词不能超过120个字符");
        }
        return normalized;
    }

    private static String normalizeFilter(String value, Set<String> accepted, String errorCode) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!accepted.contains(normalized)) {
            throw ApiException.badRequest(errorCode, "筛选状态无效");
        }
        return normalized;
    }

    private static String normalizeDecision(String value, String message) {
        String decision = value.trim().toUpperCase(Locale.ROOT);
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw ApiException.badRequest("INVALID_DECISION", message);
        }
        return decision;
    }

    private static String normalizeNote(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record FeaturedResult(long id, boolean featured) {
    }

    public record DeletedResult(long id) {
    }

    public record DeveloperModerationResult(AdminDeveloperView developer, int delistedAppCount) {
    }

    private record Page(int page, int pageSize, int offset) {
    }
}
