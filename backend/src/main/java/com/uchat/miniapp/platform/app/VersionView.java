package com.uchat.miniapp.platform.app;

import java.time.Instant;
import java.util.List;

public record VersionView(
        long id,
        long miniAppId,
        String appName,
        String developerName,
        Manifest manifest,
        String releaseNotes,
        String status,
        String reviewNote,
        Long reviewedBy,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public record Manifest(
            int schemaVersion,
            String appId,
            String name,
            String version,
            String entry,
            List<String> permissions,
            String description,
            String objectKey,
            String archiveSha256,
            long archiveSize
    ) {
    }
}
