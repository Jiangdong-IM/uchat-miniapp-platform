package com.uchat.miniapp.platform.admin;

import jakarta.validation.constraints.NotBlank;
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
}
