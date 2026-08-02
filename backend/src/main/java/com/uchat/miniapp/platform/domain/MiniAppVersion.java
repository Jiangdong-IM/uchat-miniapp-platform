package com.uchat.miniapp.platform.domain;

import java.time.Instant;

public record MiniAppVersion(
        long id,
        long miniAppId,
        int schemaVersion,
        String manifestAppId,
        String manifestName,
        String version,
        String entryPath,
        String permissionsJson,
        String manifestDescription,
        String objectKey,
        String archiveSha256,
        long archiveSize,
        String releaseNotes,
        String status,
        String reviewNote,
        Long reviewedBy,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
