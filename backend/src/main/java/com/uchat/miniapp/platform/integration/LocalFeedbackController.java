package com.uchat.miniapp.platform.integration;

import com.uchat.miniapp.platform.api.ApiException;
import com.uchat.miniapp.platform.api.ApiResponse;
import com.uchat.miniapp.platform.auth.AuthContext;
import com.uchat.miniapp.platform.repository.MiniAppRepository;
import com.uchat.miniapp.platform.repository.ReviewRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/local/feedback")
@Profile("local")
public class LocalFeedbackController {
    private final MiniAppRepository apps;
    private final ReviewRepository reviews;

    public LocalFeedbackController(MiniAppRepository apps, ReviewRepository reviews) {
        this.apps = apps;
        this.reviews = reviews;
    }

    @PostMapping
    @Transactional
    public ApiResponse<Result> create(@Valid @RequestBody Request body,
                                      HttpServletRequest request) {
        AuthContext.requireAdmin(request);
        if (apps.findById(body.miniAppId()).isEmpty()) {
            throw ApiException.notFound("APP_NOT_FOUND", "小程序不存在");
        }
        reviews.upsertLocalRating(body.miniAppId(), body.uchatUserId(), body.score());
        reviews.upsertLocalComment(body.miniAppId(), body.uchatUserId(),
                body.userDisplayName().trim(), body.content().trim());
        return ApiResponse.ok(new Result(body.miniAppId(), body.uchatUserId()));
    }

    public record Request(
            @Min(value = 1, message = "miniAppId无效") long miniAppId,
            @Min(value = 1, message = "uchatUserId无效") long uchatUserId,
            @NotBlank(message = "用户显示名称不能为空")
            @Size(max = 120, message = "用户显示名称不能超过120个字符") String userDisplayName,
            @Min(value = 1, message = "评分最低为1")
            @Max(value = 5, message = "评分最高为5") int score,
            @NotBlank(message = "评论不能为空")
            @Size(max = 500, message = "评论不能超过500个字符") String content
    ) {
    }

    public record Result(long miniAppId, long uchatUserId) {
    }
}
