package com.uchat.miniapp.platform.integration;

import com.uchat.miniapp.platform.api.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@Profile("local")
public class LocalMiniAppObjectController {
    private final LocalMemoryReleaseGateway gateway;

    public LocalMiniAppObjectController(LocalMemoryReleaseGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/local-packages")
    public ResponseEntity<byte[]> packageDownload(@RequestParam("key") String key) {
        return object(key, "packages/", MediaType.parseMediaType("application/zip"));
    }

    @GetMapping("/oss/object")
    public ResponseEntity<byte[]> asset(@RequestParam("key") String key) {
        return object(key, "assets/", null);
    }

    private ResponseEntity<byte[]> object(String key, String requiredPrefix, MediaType fixedType) {
        if (key == null || !key.startsWith(requiredPrefix)) {
            throw ApiException.notFound("OBJECT_NOT_FOUND", "object was not found");
        }
        LocalMemoryReleaseGateway.StoredObject object = gateway.object(key);
        if (object == null) {
            throw ApiException.notFound("OBJECT_NOT_FOUND", "object was not found");
        }
        MediaType contentType = fixedType;
        if (contentType == null) {
            try {
                contentType = MediaType.parseMediaType(object.contentType());
            } catch (IllegalArgumentException exception) {
                contentType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }
        return ResponseEntity.ok().contentType(contentType)
                .contentLength(object.bytes().length)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(object.bytes());
    }
}
