package com.uchat.miniapp.platform.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "[A-Za-z0-9_.-]{4,40}", message = "用户名须为4至40位字母、数字、点、下划线或连字符")
        String username,
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码须为8至72个字符")
        String password,
        @NotBlank(message = "用途不能为空")
        @Size(max = 500, message = "用途不能超过500个字符")
        String purpose,
        @NotBlank(message = "开发计划不能为空")
        @Size(max = 1000, message = "开发计划不能超过1000个字符")
        String planDescription,
        @NotBlank(message = "开发者名称不能为空")
        @Size(max = 80, message = "开发者名称不能超过80个字符")
        String developerName,
        @NotBlank(message = "联系邮箱不能为空")
        @Email(message = "联系邮箱格式不正确")
        @Size(max = 254, message = "联系邮箱不能超过254个字符")
        String contactEmail,
        @Size(max = 120, message = "组织名称不能超过120个字符")
        String organizationName
) {
}
