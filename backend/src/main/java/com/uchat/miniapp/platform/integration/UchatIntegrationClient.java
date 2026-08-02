package com.uchat.miniapp.platform.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uchat.miniapp.platform.api.ApiException;
import com.uchat.miniapp.platform.config.UchatIntegrationProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Component
@Profile("production")
public class UchatIntegrationClient implements MiniAppReleaseGateway {
    private static final String TOKEN_HEADER = "X-Mini-App-Internal-Token";
    private final RestClient restClient;
    private final UchatIntegrationProperties properties;
    private final ObjectMapper objectMapper;

    public UchatIntegrationClient(RestClient restClient, UchatIntegrationProperties properties,
                                  ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String uploadAsset(String appId, String kind, MultipartFile file) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("appId", appId);
        body.add("kind", kind);
        body.add("file", resource(file));
        JsonNode data = postMultipart("/miniApp/internal/assets", body);
        return requiredText(data, "objectKey");
    }

    @Override
    public PackageInspection inspectPackage(MultipartFile file) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource(file));
        JsonNode data = postMultipart("/miniApp/internal/packages", body);
        JsonNode permissions = data.get("permissions");
        if (permissions == null || !permissions.isArray()) {
            throw ApiException.serviceUnavailable("UCHAT_INVALID_RESPONSE", "UChat 服务返回了无效的权限清单");
        }
        return new PackageInspection(requiredInt(data, "schemaVersion"),
                requiredText(data, "appId"), requiredText(data, "name"),
                requiredText(data, "version"), requiredText(data, "entry"),
                objectMapper.convertValue(permissions,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)),
                requiredTextAllowEmpty(data, "description"), requiredText(data, "objectKey"),
                requiredText(data, "archiveSha256"), requiredLong(data, "archiveSize"));
    }

    @Override
    public void activate(ActivationRequest request) {
        postJson("/miniApp/internal/activate", request);
    }

    @Override
    public void deactivate(String appId) {
        postJson("/miniApp/internal/deactivate", new DeactivationRequest(appId));
    }

    private JsonNode postMultipart(String path, MultiValueMap<String, Object> body) {
        ensureConfigured();
        try {
            ApiEnvelope envelope = restClient.post().uri(url(path))
                    .header(TOKEN_HEADER, properties.internalToken())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body).retrieve().body(ApiEnvelope.class);
            return unwrap(envelope);
        } catch (RestClientException exception) {
            throw ApiException.serviceUnavailable("UCHAT_UNAVAILABLE", "UChat 小程序服务暂时不可用");
        }
    }

    private void postJson(String path, Object body) {
        ensureConfigured();
        try {
            ApiEnvelope envelope = restClient.post().uri(url(path))
                    .header(TOKEN_HEADER, properties.internalToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(ApiEnvelope.class);
            unwrap(envelope);
        } catch (RestClientException exception) {
            throw ApiException.serviceUnavailable("UCHAT_UNAVAILABLE", "UChat 小程序服务暂时不可用");
        }
    }

    private JsonNode unwrap(ApiEnvelope envelope) {
        if (envelope == null || !envelope.success()) {
            throw ApiException.serviceUnavailable("UCHAT_OPERATION_FAILED", "UChat 小程序服务未完成操作");
        }
        return envelope.data() == null ? objectMapper.nullNode() : envelope.data();
    }

    private ByteArrayResource resource(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "上传文件不能为空");
        }
        try {
            byte[] bytes = file.getBytes();
            String filename = file.getOriginalFilename();
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename == null || filename.isBlank() ? "upload.bin" : filename;
                }
            };
        } catch (IOException exception) {
            throw ApiException.badRequest("FILE_READ_FAILED", "无法读取上传文件");
        }
    }

    private void ensureConfigured() {
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()
                || properties.internalToken() == null || properties.internalToken().isBlank()) {
            throw ApiException.serviceUnavailable("UCHAT_NOT_CONFIGURED", "UChat 小程序服务尚未配置");
        }
    }

    private String url(String path) {
        String base = properties.baseUrl().endsWith("/")
                ? properties.baseUrl().substring(0, properties.baseUrl().length() - 1)
                : properties.baseUrl();
        return base + path;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw ApiException.serviceUnavailable("UCHAT_INVALID_RESPONSE", "UChat 服务返回数据不完整");
        }
        return value.asText();
    }

    private static String requiredTextAllowEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw ApiException.serviceUnavailable("UCHAT_INVALID_RESPONSE", "UChat 服务返回数据不完整");
        }
        return value.asText();
    }

    private static int requiredInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw ApiException.serviceUnavailable("UCHAT_INVALID_RESPONSE", "UChat 服务返回数据不完整");
        }
        return value.asInt();
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw ApiException.serviceUnavailable("UCHAT_INVALID_RESPONSE", "UChat 服务返回数据不完整");
        }
        return value.asLong();
    }

    private record ApiEnvelope(boolean success, JsonNode code, String msg, JsonNode data) {
    }

    private record DeactivationRequest(String appId) {
    }
}
