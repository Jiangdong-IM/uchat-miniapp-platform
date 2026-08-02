package com.uchat.miniapp.platform.integration;

import com.uchat.miniapp.platform.api.ApiException;
import com.uchat.miniapp.platform.config.PlatformProperties;
import com.uchat.miniapp.platform.domain.MiniApp;
import com.uchat.miniapp.platform.repository.MiniAppRepository;
import com.uchat.miniapp.platform.repository.ReviewRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@Profile("local")
public class LocalMiniAppService {
    private static final Pattern APP_ID_PATTERN = Pattern.compile(
            "^[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)+$");
    private final LocalMemoryReleaseGateway gateway;
    private final MiniAppRepository apps;
    private final ReviewRepository reviews;
    private final String publicBaseUrl;

    public LocalMiniAppService(LocalMemoryReleaseGateway gateway, MiniAppRepository apps,
                               ReviewRepository reviews, PlatformProperties properties) {
        this.gateway = gateway;
        this.apps = apps;
        this.reviews = reviews;
        this.publicBaseUrl = validatePublicBaseUrl(properties.localAppPublicBaseUrl());
    }

    public List<LocalMiniAppDtos.SearchItem> search(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.length() > 120) {
            throw ApiException.badRequest("SEARCH_KEYWORD_TOO_LONG", "mini app search keyword is too long");
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        return apps.searchPublished(normalized, 50).stream()
                .map(app -> new SearchEntry(app, gateway.activeApp(app.appId())))
                .filter(entry -> entry.active() != null)
                .map(entry -> new LocalMiniAppDtos.SearchItem(entry.app().appId(),
                        entry.app().name(), entry.active().activation().version(),
                        entry.app().description()))
                .toList();
    }

    public LocalMiniAppDtos.Download prepareDownload(String appId) {
        MiniApp app = requirePublishedApp(appId);
        LocalMemoryReleaseGateway.ActiveMiniApp active = requireActive(appId);
        MiniAppReleaseGateway.ActivationRequest activation = active.activation();
        LocalMemoryReleaseGateway.StoredObject archive = gateway.object(activation.objectKey());
        if (archive == null || !"application/zip".equals(archive.contentType())) {
            throw ApiException.notFound("PACKAGE_NOT_FOUND", "mini app package was not found");
        }
        String downloadUrl = UriComponentsBuilder.fromUriString(publicBaseUrl)
                .path("/local-packages")
                .queryParam("key", activation.objectKey())
                .build().encode().toUriString();
        long metadataUpdatedAt = Math.max(active.updatedAt(), app.updatedAt().toEpochMilli());
        return new LocalMiniAppDtos.Download(app.appId(), app.name(),
                activation.version(), app.description(), activation.archiveSha256(),
                activation.archiveSize(), metadataUpdatedAt, downloadUrl);
    }

    public LocalMiniAppDtos.Detail detail(String appId, int userId) {
        MiniApp app = requirePublishedApp(appId);
        MiniAppRepository.ReviewSummary summary = apps.reviewSummary(app.id());
        List<LocalMiniAppDtos.Comment> featured = reviews.featuredVisibleComments(app.id(), 3)
                .stream().map(this::commentView).toList();
        return new LocalMiniAppDtos.Detail(app.appId(), app.name(), app.description(),
                app.developerName(), app.iconObjectKey(), app.coverObjectKey(),
                summary.averageRating().doubleValue(), summary.ratingCount(), summary.commentCount(),
                reviews.currentUserRating(app.id(), userId), featured);
    }

    public LocalMiniAppDtos.CommentsPage comments(String appId, int page, int pageSize) {
        MiniApp app = requirePublishedApp(appId);
        long offsetValue = (long) (page - 1) * pageSize;
        if (offsetValue > Integer.MAX_VALUE) {
            throw ApiException.badRequest("INVALID_COMMENT_PAGE", "invalid mini app comment page");
        }
        List<LocalMiniAppDtos.Comment> items = reviews.visibleComments(
                        app.id(), (int) offsetValue, pageSize)
                .stream().map(this::commentView).toList();
        return new LocalMiniAppDtos.CommentsPage(items, page, pageSize,
                apps.reviewSummary(app.id()).commentCount());
    }

    @Transactional
    public void rate(String appId, int score, int userId) {
        MiniApp app = requirePublishedApp(appId);
        reviews.upsertLocalRating(app.id(), userId, score);
    }

    @Transactional
    public void comment(String appId, String content, int userId) {
        MiniApp app = requirePublishedApp(appId);
        reviews.upsertLocalComment(app.id(), userId, localDisplayName(userId), content);
    }

    private MiniApp requirePublishedApp(String appId) {
        requireActive(appId);
        MiniApp app = apps.findByAppId(appId)
                .orElseThrow(() -> ApiException.notFound("APP_NOT_FOUND", "mini app is not published"));
        if (!"PUBLISHED".equals(app.status())) {
            throw ApiException.notFound("APP_NOT_FOUND", "mini app is not published");
        }
        return app;
    }

    private LocalMemoryReleaseGateway.ActiveMiniApp requireActive(String appId) {
        validateAppId(appId);
        LocalMemoryReleaseGateway.ActiveMiniApp active = gateway.activeApp(appId);
        if (active == null) {
            throw ApiException.notFound("APP_NOT_FOUND", "mini app is not published");
        }
        return active;
    }

    private LocalMiniAppDtos.Comment commentView(ReviewRepository.CommentRow row) {
        return new LocalMiniAppDtos.Comment(row.id(), row.userDisplayName(), null,
                row.content(), row.featured(), LocalDateTime.ofInstant(
                row.createdAt(), ZoneId.systemDefault()));
    }

    private static String localDisplayName(int userId) {
        return "本地测试用户-" + userId;
    }

    private static void validateAppId(String appId) {
        if (appId == null || appId.length() > 120 || !APP_ID_PATTERN.matcher(appId).matches()) {
            throw ApiException.badRequest("INVALID_APP_ID", "mini app appId is invalid");
        }
    }

    private static String validatePublicBaseUrl(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("MINIAPP_LOCAL_APP_PUBLIC_BASE_URL is required in local profile");
        }
        String normalized = configured.trim();
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MINIAPP_LOCAL_APP_PUBLIC_BASE_URL must be an absolute HTTP URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null
                || uri.getHost().isBlank() || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalStateException("MINIAPP_LOCAL_APP_PUBLIC_BASE_URL must be an absolute HTTP URL");
        }
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private record SearchEntry(MiniApp app, LocalMemoryReleaseGateway.ActiveMiniApp active) {
    }
}
