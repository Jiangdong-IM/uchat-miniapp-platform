package com.uchat.miniapp.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uchat.miniapp.platform.integration.LocalMemoryReleaseGateway;
import com.uchat.miniapp.platform.integration.MiniAppReleaseGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformLocalFlowIntegrationTest {
    private static final String ADMIN_PASSWORD = "test-admin-password-123";
    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LocalMemoryReleaseGateway localGateway;

    @Test
    void localGatewayRejectsInvalidImagesAndMismatchedActivation() throws Exception {
        MockMultipartFile invalidImage = new MockMultipartFile(
                "file", "icon.png", "image/png", "not-an-image".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> localGateway.uploadAsset("com.test.security", "icon", invalidImage))
                .hasMessageContaining("PNG");

        byte[] archive = packageZip("com.test.security", "安全校验", "1.2.3", "校验激活快照");
        MiniAppReleaseGateway.PackageInspection inspected = localGateway.inspectPackage(
                new MockMultipartFile("file", "security.zip", "application/zip", archive));
        MiniAppReleaseGateway.ActivationRequest mismatched = new MiniAppReleaseGateway.ActivationRequest(
                inspected.appId(), inspected.name(), "9.9.9", inspected.entry(),
                inspected.schemaVersion(), inspected.permissions(), inspected.description(),
                inspected.objectKey(), inspected.archiveSha256(), inspected.archiveSize(), 1L);
        assertThatThrownBy(() -> localGateway.activate(mismatched))
                .hasMessageContaining("不一致");
    }

    @Test
    void runsCompleteLocalWorkflowWithoutExternalMiddleware() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
        mvc.perform(post("/miniApp/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("keyword", "演示"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("invalid mini app request"));

        JsonNode developer = register("developer_one", "one@example.test", "开发者一");
        long developerId = developer.get("id").asLong();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "developer_one", "password", "password-123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_PENDING"));

        String adminToken = login("admin", ADMIN_PASSWORD);
        approveRegistration(developerId, adminToken);
        String developerToken = login("developer_one", "password-123");

        JsonNode app = createApp(developerToken, "com.test.demo", "演示小程序", "本地全流程演示");
        long appId = app.get("id").asLong();

        JsonNode withIcon = uploadImage(appId, "icon", developerToken);
        String iconUrl = withIcon.get("iconUrl").asText();
        assertThat(iconUrl).startsWith("/local-assets/assets/com.test.demo/icon/");
        mvc.perform(get(iconUrl)).andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .containsExactly(PNG));
        uploadImage(appId, "cover", developerToken);

        byte[] miniAppZip = packageZip("com.test.demo", "演示小程序", "1.0.0", "本地全流程演示");
        MvcResult versionUpload = mvc.perform(multipart("/api/developer/apps/{id}/versions", appId)
                        .file(new MockMultipartFile("file", "demo.zip", "application/zip", miniAppZip))
                        .param("releaseNotes", "首个本地版本")
                        .header("Authorization", bearer(developerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.manifest.version").value("1.0.0"))
                .andReturn();
        long versionId = body(versionUpload).at("/data/id").asLong();

        mvc.perform(post("/miniApp/search")
                        .header("userId", 8001)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("keyword", "演示"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());

        mvc.perform(get("/api/admin/versions").param("status", "PENDING_REVIEW")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(versionId));

        mvc.perform(post("/api/admin/versions/{id}/decision", versionId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVED", "reviewNote", "本地审核通过"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        assertThat(localGateway.activeAppCount()).isEqualTo(1);

        mvc.perform(post("/miniApp/search")
                        .header("userId", 8001)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("keyword", "演示"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data[0].appId").value("com.test.demo"))
                .andExpect(jsonPath("$.data[0].name").value("演示小程序"))
                .andExpect(jsonPath("$.data[0].version").value("1.0.0"))
                .andExpect(jsonPath("$.data[0].description").value("本地全流程演示"));

        MvcResult downloadDescriptor = mvc.perform(post("/miniApp/prepareDownload")
                        .header("userId", 8001)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", "com.test.demo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appId").value("com.test.demo"))
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.archiveSha256").value(
                        localGateway.activeApp("com.test.demo").activation().archiveSha256()))
                .andExpect(jsonPath("$.data.archiveSize").value(miniAppZip.length))
                .andExpect(jsonPath("$.data.updatedAt").isNumber())
                .andReturn();
        String downloadUrl = body(downloadDescriptor).at("/data/downloadUrl").asText();
        assertThat(downloadUrl).startsWith("http://10.0.2.2:8091/local-packages?key=");
        String packageObjectKey = localGateway.activeApp("com.test.demo").activation().objectKey();
        mvc.perform(get("/local-packages").param("key", packageObjectKey))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .containsExactly(miniAppZip));

        String iconObjectKey = withIcon.get("iconObjectKey").asText();
        assertThat(iconObjectKey).startsWith("assets/com.test.demo/icon/");
        mvc.perform(get("/oss/object").param("key", iconObjectKey))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .containsExactly(PNG));
        mvc.perform(get("/local-packages").param("key", iconObjectKey))
                .andExpect(status().isNotFound());
        mvc.perform(get("/oss/object").param("key", packageObjectKey))
                .andExpect(status().isNotFound());
        mvc.perform(get("/local-packages").param("key", "../packages/escape.zip"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/developer/apps/{id}", appId)
                        .header("Authorization", bearer(developerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.currentVersionId").value(versionId));

        mvc.perform(post("/miniApp/detail")
                        .header("userId", 8001)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", "com.test.demo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.developerName").value("开发者一"))
                .andExpect(jsonPath("$.data.iconObjectKey").value(iconObjectKey))
                .andExpect(jsonPath("$.data.averageRating").value(0.0))
                .andExpect(jsonPath("$.data.currentUserRating").doesNotExist())
                .andExpect(jsonPath("$.data.featuredComments").isEmpty());

        mvc.perform(post("/miniApp/rating")
                        .header("userId", 8001)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", "com.test.demo", "score", 4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(post("/miniApp/comment")
                        .header("userId", 8001)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", "com.test.demo",
                                "content", "交互清楚，运行顺畅"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        MvcResult reviews = mvc.perform(get("/api/developer/apps/{id}/reviews", appId)
                        .header("Authorization", bearer(developerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(4.0))
                .andExpect(jsonPath("$.data.ratingCount").value(1))
                .andExpect(jsonPath("$.data.commentCount").value(1))
                .andExpect(jsonPath("$.data.comments[0].content").value("交互清楚，运行顺畅"))
                .andReturn();
        long commentId = body(reviews).at("/data/comments/0/id").asLong();

        mvc.perform(get("/api/admin/comments")
                        .header("Authorization", bearer(developerToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/comments")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(commentId))
                .andExpect(jsonPath("$.data.items[0].appId").value("com.test.demo"))
                .andExpect(jsonPath("$.data.items[0].appName").value("演示小程序"))
                .andExpect(jsonPath("$.data.items[0].userDisplayName").value("本地测试用户-8001"))
                .andExpect(jsonPath("$.data.items[0].content").value("交互清楚，运行顺畅"))
                .andExpect(jsonPath("$.data.items[0].featured").value(false))
                .andExpect(jsonPath("$.data.items[0].status").value("VISIBLE"))
                .andExpect(jsonPath("$.data.items[0].createdAt").isString());

        mvc.perform(put("/api/admin/comments/{id}/featured", commentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("featured", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.featured").value(true));
        mvc.perform(get("/api/admin/comments")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(commentId))
                .andExpect(jsonPath("$.data.items[0].featured").value(true));
        mvc.perform(get("/api/developer/apps/{id}/reviews", appId)
                        .header("Authorization", bearer(developerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.featuredComments[0].id").value(commentId));
        mvc.perform(post("/miniApp/detail")
                        .header("userId", 8001)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", "com.test.demo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(4.0))
                .andExpect(jsonPath("$.data.ratingCount").value(1))
                .andExpect(jsonPath("$.data.commentCount").value(1))
                .andExpect(jsonPath("$.data.currentUserRating").value(4))
                .andExpect(jsonPath("$.data.featuredComments[0].id").value(commentId))
                .andExpect(jsonPath("$.data.featuredComments[0].displayName")
                        .value("本地测试用户-8001"))
                .andExpect(jsonPath("$.data.featuredComments[0].avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.data.featuredComments[0].createdAt").isString());
        mvc.perform(post("/miniApp/comments")
                        .header("userId", 8001)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", "com.test.demo", "page", 1, "pageSize", 20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(commentId))
                .andExpect(jsonPath("$.data.items[0].featured").value(true));
        mvc.perform(put("/api/admin/comments/{id}/featured", commentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("featured", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.featured").value(false));
        mvc.perform(get("/api/admin/comments")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(commentId))
                .andExpect(jsonPath("$.data.items[0].featured").value(false));

        for (int index = 1; index <= 9; index++) {
            createApp(developerToken, "com.test.extra" + index, "应用" + index, "第" + index + "个应用");
        }
        mvc.perform(post("/api/developer/apps")
                        .header("Authorization", bearer(developerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", "com.test.eleven", "name", "第十一个",
                                "description", "超过账号上限"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APP_LIMIT_REACHED"));

        JsonNode other = register("developer_two", "two@example.test", "开发者二");
        approveRegistration(other.get("id").asLong(), adminToken);
        String otherToken = login("developer_two", "password-123");
        mvc.perform(get("/api/developer/apps/{id}", appId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APP_ACCESS_DENIED"));

        mvc.perform(post("/api/developer/apps/{id}/reviews", appId)
                        .header("Authorization", bearer(developerToken)))
                .andExpect(status().isMethodNotAllowed());

        mvc.perform(post("/api/developer/apps/{id}/delist", appId)
                        .header("Authorization", bearer(developerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELISTED"));
        assertThat(localGateway.activeAppCount()).isZero();
        mvc.perform(post("/miniApp/search")
                        .header("userId", 8001)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("keyword", "演示"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(post("/miniApp/prepareDownload")
                        .header("userId", 8001)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", "com.test.demo"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }

    private JsonNode register(String username, String email, String developerName) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "password-123",
                                "purpose", "开发并发布聊天工具", "planDescription", "先完成演示，再持续更新",
                                "developerName", developerName, "contactEmail", email,
                                "organizationName", "本地测试组织"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        return body(result).get("data");
    }

    private void approveRegistration(long accountId, String adminToken) throws Exception {
        mvc.perform(post("/api/admin/registrations/{id}/decision", accountId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVED", "reviewNote", "资料完整"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();
        return body(result).at("/data/token").asText();
    }

    private JsonNode createApp(String token, String technicalId, String name, String description)
            throws Exception {
        MvcResult result = mvc.perform(post("/api/developer/apps")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", technicalId, "name", name,
                                "description", description))))
                .andExpect(status().isOk()).andReturn();
        return body(result).get("data");
    }

    private JsonNode uploadImage(long appId, String kind, String token) throws Exception {
        MvcResult result = mvc.perform(multipart("/api/developer/apps/{id}/assets/{kind}", appId, kind)
                        .file(new MockMultipartFile("file", kind + ".png", "image/png", PNG))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return body(result).get("data");
    }

    private byte[] packageZip(String appId, String name, String version, String description)
            throws Exception {
        String manifest = objectMapper.writeValueAsString(Map.of(
                "schemaVersion", 1,
                "appId", appId,
                "name", name,
                "version", version,
                "entry", "index.html",
                "permissions", new String[]{"getUserInfo", "chooseImage"},
                "description", description));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("uchat-miniapp.json"));
            zip.write(manifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("index.html"));
            zip.write("<!doctype html><title>demo</title>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
