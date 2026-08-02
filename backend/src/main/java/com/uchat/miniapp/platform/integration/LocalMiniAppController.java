package com.uchat.miniapp.platform.integration;

import com.uchat.miniapp.platform.api.ApiException;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("local")
public class LocalMiniAppController {
    private static final String USER_ID_HEADER = "userId";
    private final LocalMiniAppService service;

    public LocalMiniAppController(LocalMiniAppService service) {
        this.service = service;
    }

    @PostMapping("/miniApp/search")
    public LocalUchatResponse<List<LocalMiniAppDtos.SearchItem>> search(
            @RequestBody @Valid LocalMiniAppDtos.SearchRequest body,
            @RequestHeader(USER_ID_HEADER) Integer userId) {
        requireLocalIdentity(userId);
        return LocalUchatResponse.ok(service.search(body.keyword()));
    }

    @PostMapping("/miniApp/prepareDownload")
    public LocalUchatResponse<LocalMiniAppDtos.Download> prepareDownload(
            @RequestBody @Valid LocalMiniAppDtos.AppRequest body,
            @RequestHeader(USER_ID_HEADER) Integer userId) {
        requireLocalIdentity(userId);
        return LocalUchatResponse.ok(service.prepareDownload(body.appId()));
    }

    @PostMapping("/miniApp/detail")
    public LocalUchatResponse<LocalMiniAppDtos.Detail> detail(
            @RequestBody @Valid LocalMiniAppDtos.AppRequest body,
            @RequestHeader(USER_ID_HEADER) Integer userId) {
        int identity = requireLocalIdentity(userId);
        return LocalUchatResponse.ok(service.detail(body.appId(), identity));
    }

    @PostMapping("/miniApp/comments")
    public LocalUchatResponse<LocalMiniAppDtos.CommentsPage> comments(
            @RequestBody @Valid LocalMiniAppDtos.CommentsRequest body,
            @RequestHeader(USER_ID_HEADER) Integer userId) {
        requireLocalIdentity(userId);
        return LocalUchatResponse.ok(service.comments(body.appId(), body.page(), body.pageSize()));
    }

    @PostMapping("/miniApp/rating")
    public LocalUchatResponse<Void> rating(
            @RequestBody @Valid LocalMiniAppDtos.RatingRequest body,
            @RequestHeader(USER_ID_HEADER) Integer userId) {
        service.rate(body.appId(), body.score(), requireLocalIdentity(userId));
        return LocalUchatResponse.ok(null);
    }

    @PostMapping("/miniApp/comment")
    public LocalUchatResponse<Void> comment(
            @RequestBody @Valid LocalMiniAppDtos.CommentRequest body,
            @RequestHeader(USER_ID_HEADER) Integer userId) {
        service.comment(body.appId(), body.content(), requireLocalIdentity(userId));
        return LocalUchatResponse.ok(null);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<LocalUchatResponse<Void>> apiError(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(LocalUchatResponse.error(exception.status().value(), exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    public ResponseEntity<LocalUchatResponse<Void>> requestError(Exception exception) {
        return ResponseEntity.badRequest()
                .body(LocalUchatResponse.error(400, "invalid mini app request"));
    }

    private static int requireLocalIdentity(Integer userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.unauthorized("LOCAL_USER_REQUIRED", "positive userId header is required");
        }
        return userId;
    }
}
