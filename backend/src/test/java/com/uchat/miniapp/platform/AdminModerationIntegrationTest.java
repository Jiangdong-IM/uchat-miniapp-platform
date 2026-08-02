package com.uchat.miniapp.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uchat.miniapp.platform.api.ApiException;
import com.uchat.miniapp.platform.app.AppRequests;
import com.uchat.miniapp.platform.app.MiniAppService;
import com.uchat.miniapp.platform.domain.Account;
import com.uchat.miniapp.platform.repository.AccountRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminModerationIntegrationTest {
    private static final String ADMIN_PASSWORD = "test-admin-password-123";
    private static final String USERNAME = "moderation_developer";
    private static final String APP_ID = "com.test.moderation";
    private static final String ORIGINAL_NAME = "治理测试小程序";
    private static final String ORIGINAL_DESCRIPTION = "管理员治理全流程";
    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    MiniAppService miniAppService;

    @Test
    void adminCanManageAppsCommentsDevelopersAndAppeals() throws Exception {
        String adminToken = login("admin", ADMIN_PASSWORD);
        long developerId = registerAndApprove(adminToken);
        String developerToken = login(USERNAME, "password-123");
        long appId = createApp(developerToken);

        uploadDeveloperAsset(appId, "icon", developerToken);
        uploadDeveloperAsset(appId, "cover", developerToken);

        mvc.perform(multipart("/api/admin/apps/{id}/assets/{kind}", appId, "icon")
                        .file(new MockMultipartFile("file", "forbidden.png", "image/png", PNG))
                        .header("Authorization", bearer(developerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
        mvc.perform(multipart("/api/admin/apps/{id}/assets/{kind}", appId, "icon")
                        .file(new MockMultipartFile("file", "replacement.png", "image/png", PNG))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.iconObjectKey").value(
                        org.hamcrest.Matchers.containsString("/icon/")));
        mvc.perform(multipart("/api/admin/apps/{id}/assets/{kind}", appId, "cover")
                        .file(new MockMultipartFile("file", "fake.png", "image/png",
                                "not-image".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));

        long approvedVersionId = uploadVersion(appId, "1.0.0", ORIGINAL_NAME,
                ORIGINAL_DESCRIPTION, developerToken);
        approveVersion(approvedVersionId, adminToken);

        mvc.perform(get("/api/admin/apps")
                        .param("keyword", "治理测试")
                        .param("status", "PUBLISHED")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].developerAccountId").value(developerId))
                .andExpect(jsonPath("$.data.items[0].status").value("PUBLISHED"));

        String renamed = "管理员已修改名称";
        String renamedDescription = "管理员修改后应在三个本地端点立即一致";
        mvc.perform(put("/api/developer/apps/{id}", appId)
                        .header("Authorization", bearer(developerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", renamed, "description", renamedDescription))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APP_MUST_BE_DELISTED_FOR_METADATA_UPDATE"));
        mvc.perform(put("/api/admin/apps/{id}", appId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", renamed, "description", renamedDescription))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APP_MUST_BE_DELISTED_FOR_METADATA_UPDATE"));
        mvc.perform(post("/miniApp/search")
                        .header("userId", 9101)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("keyword", APP_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value(ORIGINAL_NAME))
                .andExpect(jsonPath("$.data[0].description").value(ORIGINAL_DESCRIPTION));
        mvc.perform(post("/miniApp/detail")
                        .header("userId", 9101)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", APP_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(ORIGINAL_NAME))
                .andExpect(jsonPath("$.data.description").value(ORIGINAL_DESCRIPTION));
        mvc.perform(post("/miniApp/prepareDownload")
                        .header("userId", 9101)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", APP_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(ORIGINAL_NAME))
                .andExpect(jsonPath("$.data.description").value(ORIGINAL_DESCRIPTION))
                .andExpect(jsonPath("$.data.updatedAt").isNumber());

        long commentId = createComment(appId, adminToken);
        mvc.perform(post("/api/admin/comments")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("miniAppId", appId, "uchatUserId", 9201,
                                "userDisplayName", "管理员测试用户", "content", "重复评论",
                                "featured", false, "status", "VISIBLE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMENT_EXISTS"));
        mvc.perform(get("/api/admin/comments/{id}", commentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.miniAppId").value(appId))
                .andExpect(jsonPath("$.data.uchatUserId").value(9201));
        mvc.perform(put("/api/admin/comments/{id}", commentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userDisplayName", "修改后的用户", "content", "修改后的评论",
                                "featured", true, "status", "VISIBLE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("修改后的评论"))
                .andExpect(jsonPath("$.data.featured").value(true));
        mvc.perform(get("/api/admin/comments")
                        .param("keyword", "修改后的评论")
                        .param("miniAppId", Long.toString(appId))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(commentId));
        mvc.perform(put("/api/admin/comments/{id}", commentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userDisplayName", "修改后的用户", "content", "隐藏的评论",
                                "featured", true, "status", "HIDDEN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.featured").value(false))
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));
        mvc.perform(put("/api/admin/comments/{id}/featured", commentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("featured", true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HIDDEN_COMMENT_NOT_FEATURED"));
        mvc.perform(post("/miniApp/comment")
                        .header("userId", 9201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", APP_ID, "content", "用户再次编辑隐藏评论"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/comments/{id}", commentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("用户再次编辑隐藏评论"))
                .andExpect(jsonPath("$.data.status").value("HIDDEN"))
                .andExpect(jsonPath("$.data.featured").value(false));
        mvc.perform(post("/miniApp/comments")
                        .header("userId", 9201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", APP_ID, "page", 1, "pageSize", 20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(delete("/api/admin/comments/{id}", commentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(commentId));
        mvc.perform(get("/api/admin/comments/{id}", commentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/admin/apps/{id}/delist", appId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELISTED"));
        assertLocalAppUnavailable();
        mvc.perform(post("/api/admin/apps/{id}/delist", appId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APP_NOT_PUBLISHED"));
        updateAdminApp(appId, renamed, renamedDescription, adminToken);
        mvc.perform(post("/api/admin/apps/{id}/publish", appId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APP_CHANGED_AFTER_APPROVAL"));

        long renamedVersionId = uploadVersion(appId, "1.1.0", renamed,
                renamedDescription, developerToken);
        approveVersion(renamedVersionId, adminToken);
        assertLocalMetadata(renamed, renamedDescription);
        mvc.perform(post("/api/admin/apps/{id}/delist", appId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELISTED"));
        mvc.perform(post("/api/admin/apps/{id}/publish", appId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        assertLocalMetadata(renamed, renamedDescription);
        long pendingVersionId = uploadVersion(appId, "1.2.0", renamed,
                renamedDescription, developerToken);

        mvc.perform(get("/api/admin/developers")
                        .param("keyword", USERNAME)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].appCount").value(1))
                .andExpect(jsonPath("$.data.items[0].publishedAppCount").value(1));
        Account staleApprovedAccount = accounts.findById(developerId).orElseThrow();
        mvc.perform(post("/api/admin/developers/{id}/ban", developerId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "违反平台测试规则"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.delistedAppCount").value(1))
                .andExpect(jsonPath("$.data.developer.status").value("BANNED"))
                .andExpect(jsonPath("$.data.developer.publishedAppCount").value(0));
        assertThatThrownBy(() -> miniAppService.create(staleApprovedAccount,
                new AppRequests.Create("com.test.stale", "过期请求", "封禁前已通过鉴权的在途请求")))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo("ACCOUNT_BANNED"));
        assertLocalAppUnavailable();

        String bannedToken = login(USERNAME, "password-123");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(bannedToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BANNED"));
        mvc.perform(get("/api/developer/apps").header("Authorization", bearer(bannedToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BANNED"));
        mvc.perform(post("/api/admin/versions/{id}/decision", pendingVersionId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVED", "reviewNote", "不应通过"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVELOPER_BANNED"));
        mvc.perform(post("/api/admin/versions/{id}/decision", pendingVersionId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "REJECTED", "reviewNote", "封禁后允许驳回"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        mvc.perform(post("/api/admin/apps/{id}/publish", appId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVELOPER_BANNED"));

        long rejectedAppeal = submitAppeal(bannedToken, "第一次申诉，请管理员复核");
        mvc.perform(post("/api/developer/appeals")
                        .header("Authorization", bearer(bannedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "不允许并发重复申诉"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPEAL_ALREADY_PENDING"));
        mvc.perform(get("/api/admin/appeals")
                        .param("status", "PENDING")
                        .param("developerAccountId", Long.toString(developerId))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(rejectedAppeal));
        decideAppeal(rejectedAppeal, "REJECTED", adminToken);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(bannedToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BANNED"));

        long manuallyResolvedAppeal = submitAppeal(bannedToken, "第二次申诉，等待手工解禁");
        mvc.perform(post("/api/admin/developers/{id}/unban", developerId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("note", "管理员手工解禁"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.developer.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.delistedAppCount").value(0));
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(bannedToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewNote").value("准入通过"));
        mvc.perform(get("/api/developer/appeals").header("Authorization", bearer(bannedToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(manuallyResolvedAppeal))
                .andExpect(jsonPath("$.data[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.data[0].reviewNote").value("管理员手工解禁"));
        mvc.perform(post("/api/admin/appeals/{id}/decision", manuallyResolvedAppeal)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVED", "reviewNote", "重复处理"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPEAL_ALREADY_REVIEWED"));
        assertLocalAppUnavailable();

        mvc.perform(post("/api/admin/developers/{id}/ban", developerId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "再次封禁以测试申诉通过"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.delistedAppCount").value(0));
        String rebannedToken = login(USERNAME, "password-123");
        long approvedAppeal = submitAppeal(rebannedToken, "第三次申诉，应当自动解禁");
        mvc.perform(post("/api/admin/appeals/{id}/decision", approvedAppeal)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVED", "reviewNote", "申诉成立"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(rebannedToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        assertLocalAppUnavailable();
    }

    private long registerAndApprove(String adminToken) throws Exception {
        MvcResult registration = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", USERNAME, "password", "password-123",
                                "purpose", "验证管理员治理", "planDescription", "覆盖全部治理能力",
                                "developerName", "治理测试开发者", "contactEmail", "moderation@example.test",
                                "organizationName", "治理测试组织"))))
                .andExpect(status().isOk()).andReturn();
        long developerId = body(registration).at("/data/id").asLong();
        mvc.perform(post("/api/admin/registrations/{id}/decision", developerId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVED", "reviewNote", "准入通过"))))
                .andExpect(status().isOk());
        return developerId;
    }

    private long createApp(String developerToken) throws Exception {
        MvcResult result = mvc.perform(post("/api/developer/apps")
                        .header("Authorization", bearer(developerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", APP_ID, "name", ORIGINAL_NAME,
                                "description", ORIGINAL_DESCRIPTION))))
                .andExpect(status().isOk()).andReturn();
        return body(result).at("/data/id").asLong();
    }

    private void uploadDeveloperAsset(long appId, String kind, String developerToken) throws Exception {
        mvc.perform(multipart("/api/developer/apps/{id}/assets/{kind}", appId, kind)
                        .file(new MockMultipartFile("file", kind + ".png", "image/png", PNG))
                        .header("Authorization", bearer(developerToken)))
                .andExpect(status().isOk());
    }

    private long uploadVersion(long appId, String version, String name, String description,
                               String developerToken) throws Exception {
        MvcResult result = mvc.perform(multipart("/api/developer/apps/{id}/versions", appId)
                        .file(new MockMultipartFile("file", "app.zip", "application/zip",
                                packageZip(version, name, description)))
                        .param("releaseNotes", "治理测试版本 " + version)
                        .header("Authorization", bearer(developerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andReturn();
        return body(result).at("/data/id").asLong();
    }

    private void approveVersion(long versionId, String adminToken) throws Exception {
        mvc.perform(post("/api/admin/versions/{id}/decision", versionId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVED", "reviewNote", "版本通过"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    private void updateAdminApp(long appId, String name, String description, String adminToken)
            throws Exception {
        mvc.perform(put("/api/admin/apps/{id}", appId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "description", description))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.description").value(description));
    }

    private long createComment(long appId, String adminToken) throws Exception {
        MvcResult result = mvc.perform(post("/api/admin/comments")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("miniAppId", appId, "uchatUserId", 9201,
                                "userDisplayName", "管理员测试用户", "content", "管理员创建评论",
                                "featured", false, "status", "VISIBLE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VISIBLE"))
                .andReturn();
        return body(result).at("/data/id").asLong();
    }

    private long submitAppeal(String token, String content) throws Exception {
        MvcResult result = mvc.perform(post("/api/developer/appeals")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", content))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        return body(result).at("/data/id").asLong();
    }

    private void decideAppeal(long appealId, String decision, String adminToken) throws Exception {
        mvc.perform(post("/api/admin/appeals/{id}/decision", appealId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", decision, "reviewNote", "管理员处理申诉"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(decision));
    }

    private void assertLocalAppUnavailable() throws Exception {
        mvc.perform(post("/miniApp/search")
                        .header("userId", 9101)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("keyword", APP_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(post("/miniApp/detail")
                        .header("userId", 9101)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", APP_ID))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/miniApp/prepareDownload")
                        .header("userId", 9101)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", APP_ID))))
                .andExpect(status().isNotFound());
    }

    private void assertLocalMetadata(String name, String description) throws Exception {
        mvc.perform(post("/miniApp/search")
                        .header("userId", 9101)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("keyword", APP_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value(name))
                .andExpect(jsonPath("$.data[0].description").value(description));
        mvc.perform(post("/miniApp/detail")
                        .header("userId", 9101)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", APP_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.description").value(description));
        mvc.perform(post("/miniApp/prepareDownload")
                        .header("userId", 9101)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appId", APP_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.description").value(description));
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

    private byte[] packageZip(String version, String name, String description) throws Exception {
        String manifest = objectMapper.writeValueAsString(Map.of(
                "schemaVersion", 1,
                "appId", APP_ID,
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
            zip.write("<!doctype html><title>moderation</title>".getBytes(StandardCharsets.UTF_8));
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
