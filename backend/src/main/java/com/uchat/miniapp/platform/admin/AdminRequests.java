package com.uchat.miniapp.platform.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public final class AdminRequests {
    private AdminRequests() {
    }

    public record Decision(
            @NotBlank(message = "审核决定不能为空") String decision,
            @Size(max = 500, message = "审核备注不能超过500个字符") String reviewNote
    ) {
    }

    public record Featured(boolean featured) {
    }

    public record UpdateApp(
            @NotBlank(message = "小程序名称不能为空")
            @Size(max = 40, message = "小程序名称不能超过40个字符") String name,
            @NotBlank(message = "小程序简介不能为空")
            @Size(max = 120, message = "小程序简介不能超过120个字符") String description
    ) {
    }

    public record CreateComment(
            @Min(value = 1, message = "miniAppId无效") long miniAppId,
            @Min(value = 1, message = "uchatUserId无效") long uchatUserId,
            @NotBlank(message = "用户显示名称不能为空")
            @Size(max = 120, message = "用户显示名称不能超过120个字符") String userDisplayName,
            @NotBlank(message = "评论不能为空")
            @Size(max = 500, message = "评论不能超过500个字符") String content,
            @NotNull(message = "精选状态不能为空") Boolean featured,
            @NotBlank(message = "评论状态不能为空") String status
    ) {
    }

    public record UpdateComment(
            @NotBlank(message = "用户显示名称不能为空")
            @Size(max = 120, message = "用户显示名称不能超过120个字符") String userDisplayName,
            @NotBlank(message = "评论不能为空")
            @Size(max = 500, message = "评论不能超过500个字符") String content,
            @NotNull(message = "精选状态不能为空") Boolean featured,
            @NotBlank(message = "评论状态不能为空") String status
    ) {
    }

    public record BanDeveloper(
            @NotBlank(message = "封禁原因不能为空")
            @Size(max = 500, message = "封禁原因不能超过500个字符") String reason
    ) {
    }

    public record UnbanDeveloper(
            @Size(max = 500, message = "解禁备注不能超过500个字符") String note
    ) {
    }

    public record CreateAppeal(
            @NotBlank(message = "申诉内容不能为空")
            @Size(max = 1000, message = "申诉内容不能超过1000个字符") String content
    ) {
    }
}
