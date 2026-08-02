package com.uchat.miniapp.platform.integration;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class LocalMiniAppDtos {
    private LocalMiniAppDtos() {
    }

    public record SearchRequest(String keyword) {
    }

    public record AppRequest(@NotBlank String appId) {
    }

    public record CommentsRequest(
            @NotBlank String appId,
            @NotNull @Min(1) Integer page,
            @NotNull @Min(1) @Max(100) Integer pageSize) {
    }

    public record RatingRequest(
            @NotBlank String appId,
            @NotNull @Min(1) @Max(5) Integer score) {
    }

    public record CommentRequest(
            @NotBlank String appId,
            @NotBlank @Size(max = 500) String content) {
    }

    public record SearchItem(String appId, String name, String version, String description) {
    }

    public record Download(
            String appId,
            String name,
            String version,
            String description,
            String archiveSha256,
            long archiveSize,
            long updatedAt,
            String downloadUrl) {
    }

    public record Detail(
            String appId,
            String name,
            String description,
            String developerName,
            String iconObjectKey,
            String coverObjectKey,
            double averageRating,
            long ratingCount,
            long commentCount,
            Integer currentUserRating,
            List<Comment> featuredComments) {
    }

    public record Comment(
            long id,
            String displayName,
            String avatarUrl,
            String content,
            boolean featured,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            LocalDateTime createdAt) {
    }

    public record CommentsPage(List<Comment> items, int page, int pageSize, long total) {
    }
}
