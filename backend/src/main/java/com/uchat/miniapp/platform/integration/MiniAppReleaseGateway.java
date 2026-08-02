package com.uchat.miniapp.platform.integration;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MiniAppReleaseGateway {
    String uploadAsset(String appId, String kind, MultipartFile file);

    PackageInspection inspectPackage(MultipartFile file);

    void activate(ActivationRequest request);

    void deactivate(String appId);

    record PackageInspection(int schemaVersion, String appId, String name,
                             String version, String entry, List<String> permissions,
                             String description, String objectKey, String archiveSha256,
                             long archiveSize) {
    }

    record ActivationRequest(String appId, String name, String version, String entry,
                             int schemaVersion, List<String> permissions, String description,
                             String objectKey, String archiveSha256, long archiveSize,
                             long publishedBy) {
    }
}
