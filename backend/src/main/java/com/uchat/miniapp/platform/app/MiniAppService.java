package com.uchat.miniapp.platform.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uchat.miniapp.platform.api.ApiException;
import com.uchat.miniapp.platform.config.PlatformProperties;
import com.uchat.miniapp.platform.domain.Account;
import com.uchat.miniapp.platform.domain.MiniApp;
import com.uchat.miniapp.platform.domain.MiniAppVersion;
import com.uchat.miniapp.platform.integration.MiniAppReleaseGateway;
import com.uchat.miniapp.platform.repository.AccountRepository;
import com.uchat.miniapp.platform.repository.MiniAppRepository;
import com.uchat.miniapp.platform.repository.ReviewRepository;
import com.uchat.miniapp.platform.repository.VersionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class MiniAppService {
    private final AccountRepository accounts;
    private final MiniAppRepository apps;
    private final VersionRepository versions;
    private final ReviewRepository reviews;
    private final MiniAppReleaseGateway releaseGateway;
    private final ObjectMapper objectMapper;
    private final PlatformProperties properties;

    public MiniAppService(AccountRepository accounts, MiniAppRepository apps,
                          VersionRepository versions, ReviewRepository reviews,
                          MiniAppReleaseGateway releaseGateway, ObjectMapper objectMapper,
                          PlatformProperties properties) {
        this.accounts = accounts;
        this.apps = apps;
        this.versions = versions;
        this.reviews = reviews;
        this.releaseGateway = releaseGateway;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public MiniAppView create(Account account, AppRequests.Create request) {
        Account currentAccount = lockApprovedDeveloper(account.id());
        if (apps.countByDeveloper(account.id()) >= 10) {
            throw ApiException.conflict("APP_LIMIT_REACHED", "每个开发者账号最多可创建10个小程序");
        }
        try {
            long id = apps.insert(request.appId().trim(), request.name().trim(),
                    request.description().trim(), currentAccount.id(), currentAccount.developerName());
            return view(requireOwned(id, account.id()));
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict("APP_ID_EXISTS", "该appId已被使用");
        }
    }

    public List<MiniAppView> list(Account account) {
        return apps.findByDeveloper(account.id()).stream().map(this::view).toList();
    }

    public MiniAppView get(long id, Account account) {
        return view(requireOwned(id, account.id()));
    }

    @Transactional
    public MiniAppView update(long id, Account account, AppRequests.Update request) {
        lockApprovedDeveloper(account.id());
        MiniApp app = requireOwnedForUpdate(id, account.id());
        String name = request.name().trim();
        String description = request.description().trim();
        if ("PUBLISHED".equals(app.status())
                && (!app.name().equals(name) || !app.description().equals(description))) {
            throw ApiException.conflict("APP_MUST_BE_DELISTED_FOR_METADATA_UPDATE",
                    "已上架小程序必须先下架，才能修改名称或简介");
        }
        apps.updateMetadata(id, account.id(), name, description);
        return view(requireOwned(id, account.id()));
    }

    @Transactional
    public MiniAppView uploadAsset(long id, Account account, String kind, MultipartFile file) {
        if (!"icon".equals(kind) && !"cover".equals(kind)) {
            throw ApiException.badRequest("INVALID_ASSET_KIND", "资产类型只能是icon或cover");
        }
        lockApprovedDeveloper(account.id());
        MiniApp app = requireOwnedForUpdate(id, account.id());
        String objectKey = releaseGateway.uploadAsset(app.appId(), kind, file);
        apps.updateAsset(id, account.id(), kind, objectKey);
        return view(requireOwned(id, account.id()));
    }

    @Transactional
    public VersionView uploadVersion(long id, Account account, MultipartFile file, String releaseNotes) {
        lockApprovedDeveloper(account.id());
        MiniApp app = apps.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("APP_NOT_FOUND", "小程序不存在"));
        if (app.developerAccountId() != account.id()) {
            throw ApiException.forbidden("APP_ACCESS_DENIED", "无权访问此小程序");
        }
        if (app.iconObjectKey() == null || app.coverObjectKey() == null) {
            throw ApiException.badRequest("ASSETS_REQUIRED", "提交审核前必须上传图标和封面图");
        }
        MiniAppReleaseGateway.PackageInspection inspection = releaseGateway.inspectPackage(file);
        if (!app.appId().equals(inspection.appId())
                || !app.name().equals(inspection.name())
                || !app.description().equals(inspection.description())) {
            throw ApiException.badRequest("MANIFEST_MISMATCH", "包内appId、名称和简介必须与小程序资料完全一致");
        }
        String notes = releaseNotes == null ? "" : releaseNotes.trim();
        if (notes.length() > 1000) {
            throw ApiException.badRequest("RELEASE_NOTES_TOO_LONG", "更新说明不能超过1000个字符");
        }
        try {
            long versionId = versions.insert(app.id(), inspection.schemaVersion(),
                    inspection.appId(), inspection.name(), inspection.version(), inspection.entry(),
                    objectMapper.writeValueAsString(inspection.permissions()), inspection.description(),
                    inspection.objectKey(), inspection.archiveSha256(), inspection.archiveSize(), notes);
            return versionView(versions.findById(versionId).orElseThrow(), app);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize verified permissions", exception);
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict("VERSION_EXISTS", "该版本号已经提交过");
        }
    }

    public List<VersionView> versions(long id, Account account) {
        MiniApp app = requireOwned(id, account.id());
        return versions.findByApp(app.id()).stream().map(version -> versionView(version, app)).toList();
    }

    @Transactional
    public MiniAppView delist(long id, Account account) {
        lockApprovedDeveloper(account.id());
        MiniApp app = requireOwnedForUpdate(id, account.id());
        if ("PUBLISHED".equals(app.status())) {
            releaseGateway.deactivate(app.appId());
        }
        apps.delist(id, account.id());
        return view(requireOwned(id, account.id()));
    }

    public ReviewView reviews(long id, Account account) {
        MiniApp app = requireOwned(id, account.id());
        MiniAppRepository.ReviewSummary summary = apps.reviewSummary(app.id());
        List<ReviewView.Rating> ratingViews = reviews.ratings(app.id()).stream()
                .map(row -> new ReviewView.Rating(row.id(), row.uchatUserId(), row.score(),
                        row.createdAt(), row.updatedAt())).toList();
        List<ReviewView.Comment> comments = reviews.comments(app.id()).stream()
                .map(row -> new ReviewView.Comment(row.id(), row.uchatUserId(),
                        row.userDisplayName(), row.content(), row.featured(), row.status(),
                        row.createdAt(), row.updatedAt())).toList();
        return new ReviewView(app.id(), summary.averageRating().doubleValue(), summary.ratingCount(),
                summary.commentCount(), ratingViews,
                comments.stream().filter(ReviewView.Comment::featured).toList(), comments);
    }

    public DashboardView dashboard(Account account) {
        return new DashboardView(apps.countByDeveloper(account.id()),
                apps.countPublished(account.id()), versions.countPendingForDeveloper(account.id()),
                apps.averageRatingForDeveloper(account.id()),
                apps.findRecentByDeveloper(account.id(), 5).stream().map(this::view).toList());
    }

    public MiniApp requireOwned(long id, long developerId) {
        MiniApp app = apps.findById(id)
                .orElseThrow(() -> ApiException.notFound("APP_NOT_FOUND", "小程序不存在"));
        if (app.developerAccountId() != developerId) {
            throw ApiException.forbidden("APP_ACCESS_DENIED", "无权访问此小程序");
        }
        return app;
    }

    private MiniApp requireOwnedForUpdate(long id, long developerId) {
        MiniApp app = apps.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("APP_NOT_FOUND", "小程序不存在"));
        if (app.developerAccountId() != developerId) {
            throw ApiException.forbidden("APP_ACCESS_DENIED", "无权访问此小程序");
        }
        return app;
    }

    private Account lockApprovedDeveloper(long developerId) {
        Account account = accounts.findByIdForUpdate(developerId)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_SESSION", "登录已失效，请重新登录"));
        if (!"DEVELOPER".equals(account.role())) {
            throw ApiException.forbidden("DEVELOPER_REQUIRED", "此操作仅限开发者账号");
        }
        if ("BANNED".equals(account.status())) {
            throw ApiException.forbidden("ACCOUNT_BANNED", "开发者账号已被封禁，仅可查看或提交申诉");
        }
        if (!"APPROVED".equals(account.status())) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "开发者账号当前不可用");
        }
        return account;
    }

    public MiniAppView view(MiniApp app) {
        MiniAppRepository.ReviewSummary summary = apps.reviewSummary(app.id());
        return new MiniAppView(app.id(), app.appId(), app.name(), app.description(),
                app.developerName(), app.iconObjectKey(), app.coverObjectKey(),
                publicUrl(app.iconObjectKey()), publicUrl(app.coverObjectKey()), app.status(),
                app.currentVersionId(), summary.averageRating().doubleValue(), summary.ratingCount(),
                summary.commentCount(), app.createdAt(), app.updatedAt());
    }

    public VersionView versionView(MiniAppVersion version, MiniApp app) {
        try {
            List<String> permissions = objectMapper.readValue(version.permissionsJson(), new TypeReference<>() {
            });
            VersionView.Manifest manifest = new VersionView.Manifest(version.schemaVersion(),
                    version.manifestAppId(), version.manifestName(), version.version(),
                    version.entryPath(), permissions, version.manifestDescription(),
                    version.objectKey(), version.archiveSha256(), version.archiveSize());
            return new VersionView(version.id(), version.miniAppId(), app.name(), app.developerName(),
                    manifest, version.releaseNotes(), version.status(), version.reviewNote(),
                    version.reviewedBy(), version.reviewedAt(), version.createdAt(), version.updatedAt());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored permissions JSON is invalid", exception);
        }
    }

    private String publicUrl(String objectKey) {
        if (objectKey == null || properties.assetPublicBaseUrl() == null
                || properties.assetPublicBaseUrl().isBlank()) {
            return null;
        }
        String base = properties.assetPublicBaseUrl().endsWith("/")
                ? properties.assetPublicBaseUrl().substring(0, properties.assetPublicBaseUrl().length() - 1)
                : properties.assetPublicBaseUrl();
        return base + "/" + objectKey;
    }
}
