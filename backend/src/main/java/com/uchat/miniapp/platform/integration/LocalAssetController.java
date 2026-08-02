package com.uchat.miniapp.platform.integration;

import com.uchat.miniapp.platform.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@Profile("local")
public class LocalAssetController {
    private static final String PREFIX = "/local-assets/";
    private final LocalMemoryReleaseGateway gateway;

    public LocalAssetController(LocalMemoryReleaseGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/local-assets/**")
    public ResponseEntity<byte[]> get(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith(PREFIX) || uri.length() <= PREFIX.length()) {
            throw ApiException.notFound("ASSET_NOT_FOUND", "资产不存在");
        }
        String key = uri.substring(PREFIX.length());
        if (!key.startsWith("assets/")) {
            throw ApiException.notFound("ASSET_NOT_FOUND", "资产不存在");
        }
        LocalMemoryReleaseGateway.StoredObject object = gateway.object(key);
        if (object == null) {
            throw ApiException.notFound("ASSET_NOT_FOUND", "资产不存在");
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(object.contentType());
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok().contentType(mediaType)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(object.bytes());
    }
}
