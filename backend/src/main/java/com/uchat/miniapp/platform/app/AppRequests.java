package com.uchat.miniapp.platform.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AppRequests {
    private AppRequests() {
    }

    public record Create(
            @NotBlank(message = "appId不能为空")
            @Size(max = 120, message = "appId不能超过120个字符")
            @Pattern(regexp = "[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)+",
                    message = "appId须为反向域名格式的小写标识，例如com.example.demo")
            String appId,
            @NotBlank(message = "小程序名称不能为空")
            @Size(max = 40, message = "小程序名称不能超过40个字符")
            String name,
            @NotBlank(message = "小程序简介不能为空")
            @Size(max = 120, message = "小程序简介不能超过120个字符")
            String description
    ) {
    }

    public record Update(
            @NotBlank(message = "小程序名称不能为空")
            @Size(max = 40, message = "小程序名称不能超过40个字符")
            String name,
            @NotBlank(message = "小程序简介不能为空")
            @Size(max = 120, message = "小程序简介不能超过120个字符")
            String description
    ) {
    }
}
