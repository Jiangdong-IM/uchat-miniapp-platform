package com.uchat.miniapp.platform.app;

import java.util.List;

public record DashboardView(
        int appCount,
        int publishedCount,
        int pendingVersionCount,
        double averageRating,
        List<MiniAppView> recentApps
) {
}
