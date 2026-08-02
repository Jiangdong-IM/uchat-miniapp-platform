package com.uchat.miniapp.platform.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uchat.miniapp.platform.api.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
@Profile("local")
public class LocalMemoryReleaseGateway implements MiniAppReleaseGateway {
    private static final long MAX_ARCHIVE_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 300;
    private static final long MAX_ENTRY_BYTES = 10L * 1024L * 1024L;
    private static final long MAX_UNCOMPRESSED_BYTES = 30L * 1024L * 1024L;
    private static final int MAX_PATH_BYTES = 512;
    private static final int MAX_PATH_SEGMENTS = 32;
    private static final int MAX_PATH_SEGMENT_BYTES = 128;
    private static final Set<String> ALLOWED_MANIFEST_KEYS = Set.of(
            "schemaVersion", "appId", "name", "version", "entry", "permissions", "description");
    private static final Set<String> ALLOWED_PERMISSIONS = Set.of(
            "chooseImage", "chooseChatMedia", "getUserInfo", "getLocation", "sendMessage");
    private static final Pattern APP_ID_PATTERN = Pattern.compile(
            "^[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)+$");
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^(0|[1-9][0-9]{0,9})\\.(0|[1-9][0-9]{0,9})\\.(0|[1-9][0-9]{0,9})$");
    private static final Pattern WINDOWS_DRIVE_PREFIX = Pattern.compile("^[A-Za-z]:");
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5",
            "COM6", "COM7", "COM8", "COM9", "COM¹", "COM²", "COM³", "LPT1", "LPT2",
            "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9", "LPT¹", "LPT²", "LPT³");

    private final ObjectMapper objectMapper;
    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
    private final Map<String, PackageInspection> inspectedPackages = new ConcurrentHashMap<>();
    private final Map<String, ActiveMiniApp> activeCatalog = new ConcurrentHashMap<>();

    public LocalMemoryReleaseGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String uploadAsset(String appId, String kind, MultipartFile file) {
        byte[] bytes = bytes(file);
        validateAppId(appId);
        String normalizedKind = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("icon", "cover").contains(normalizedKind)) {
            throw ApiException.badRequest("INVALID_ASSET_KIND", "资产类型只能是icon或cover");
        }
        long limit = "icon".equals(normalizedKind) ? 1024L * 1024L : 3L * 1024L * 1024L;
        if (bytes.length > limit) {
            throw ApiException.badRequest("ASSET_TOO_LARGE", "上传图片超过大小限制");
        }
        ImageFormat format = detectImage(bytes);
        validateImageContentType(file.getContentType(), format.contentType());
        String objectKey = "assets/" + appId + "/" + normalizedKind + "/" + UUID.randomUUID()
                + "." + format.extension();
        objects.put(objectKey, new StoredObject(bytes, format.contentType()));
        return objectKey;
    }

    @Override
    public PackageInspection inspectPackage(MultipartFile file) {
        byte[] archive = bytes(file);
        if (archive.length > MAX_ARCHIVE_BYTES) {
            throw ApiException.badRequest("PACKAGE_TOO_LARGE", "小程序ZIP包不能超过10MiB");
        }
        ZipInspection zip = inspectZip(archive);
        JsonNode manifest;
        try {
            manifest = objectMapper.readTree(decodeUtf8Strict(zip.manifestBytes()));
        } catch (IOException exception) {
            throw ApiException.badRequest("INVALID_MANIFEST", "uchat-miniapp.json不是有效的JSON");
        }
        validateManifest(manifest, zip.entryNames());
        LinkedHashSet<String> permissionSet = new LinkedHashSet<>();
        manifest.get("permissions").forEach(value -> {
            if (!value.isTextual() || !permissionSet.add(value.asText())) {
                throw ApiException.badRequest("INVALID_MANIFEST", "permissions必须是无重复的字符串数组");
            }
        });
        if (!ALLOWED_PERMISSIONS.containsAll(permissionSet)) {
            throw ApiException.badRequest("INVALID_MANIFEST", "permissions包含不支持的能力");
        }
        List<String> permissions = permissionSet.stream().sorted().toList();
        String appId = manifest.get("appId").asText();
        String objectKey = "packages/" + appId + "/" + UUID.randomUUID() + ".zip";
        objects.put(objectKey, new StoredObject(archive, "application/zip"));
        PackageInspection inspection = new PackageInspection(manifest.get("schemaVersion").asInt(), appId,
                manifest.get("name").asText(), manifest.get("version").asText(),
                manifest.get("entry").asText(), List.copyOf(permissions),
                manifest.get("description").asText(), objectKey, sha256(archive), archive.length);
        inspectedPackages.put(objectKey, inspection);
        return inspection;
    }

    @Override
    public void activate(ActivationRequest request) {
        PackageInspection inspected = inspectedPackages.get(request.objectKey());
        if (inspected == null) {
            throw ApiException.badRequest("PACKAGE_NOT_FOUND", "待发布的小程序包不存在");
        }
        boolean invalidPermissions = request.permissions() == null
                || request.permissions().stream().anyMatch(Objects::isNull)
                || new HashSet<>(request.permissions()).size() != request.permissions().size();
        if (invalidPermissions
                || request.schemaVersion() != inspected.schemaVersion()
                || !Objects.equals(request.appId(), inspected.appId())
                || !Objects.equals(request.name(), inspected.name())
                || !Objects.equals(request.version(), inspected.version())
                || !Objects.equals(request.entry(), inspected.entry())
                || !new HashSet<>(request.permissions()).equals(new HashSet<>(inspected.permissions()))
                || !Objects.equals(request.description(), inspected.description())
                || !Objects.equals(request.archiveSha256(), inspected.archiveSha256())
                || request.archiveSize() != inspected.archiveSize()) {
            throw ApiException.badRequest("ACTIVATION_MISMATCH", "激活信息与已验证的小程序包不一致");
        }
        ActivationRequest snapshot = new ActivationRequest(request.appId(), request.name(),
                request.version(), request.entry(), request.schemaVersion(),
                List.copyOf(request.permissions()), request.description(), request.objectKey(),
                request.archiveSha256(), request.archiveSize(), request.publishedBy());
        activeCatalog.put(request.appId(), new ActiveMiniApp(snapshot, System.currentTimeMillis()));
    }

    @Override
    public void deactivate(String appId) {
        activeCatalog.remove(appId);
    }

    public StoredObject object(String key) {
        return objects.get(key);
    }

    public int storedObjectCount() {
        return objects.size();
    }

    public int activeAppCount() {
        return activeCatalog.size();
    }

    public ActiveMiniApp activeApp(String appId) {
        return activeCatalog.get(appId);
    }

    public List<ActiveMiniApp> searchActiveByName(String keyword, int limit) {
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return activeCatalog.values().stream()
                .filter(item -> item.activation().name().toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.comparing((ActiveMiniApp item) -> item.activation().name())
                        .thenComparing(item -> item.activation().appId()))
                .limit(limit)
                .toList();
    }

    private ZipInspection inspectZip(byte[] archive) {
        byte[] manifest = null;
        List<String> names = new ArrayList<>();
        long total = 0;
        int count = 0;
        Set<String> caseInsensitivePaths = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive),
                StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                count++;
                if (count > MAX_ENTRIES) {
                    throw ApiException.badRequest("INVALID_PACKAGE", "小程序包内文件数量过多");
                }
                String name = entry.getName();
                validateEntryName(name);
                String normalized = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
                validatePathLimits(normalized);
                if (!caseInsensitivePaths.add(normalized.toLowerCase(Locale.ROOT))) {
                    throw ApiException.badRequest("INVALID_PACKAGE", "小程序包包含重复或大小写冲突的路径");
                }
                if (!entry.isDirectory()) {
                    names.add(normalized);
                    ByteArrayOutputStream data = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8_192];
                    int read;
                    long entryBytes = 0;
                    while ((read = zip.read(buffer)) != -1) {
                        entryBytes += read;
                        total += read;
                        if (entryBytes > MAX_ENTRY_BYTES || total > MAX_UNCOMPRESSED_BYTES) {
                            throw ApiException.badRequest("INVALID_PACKAGE", "小程序包解压后超过大小限制");
                        }
                        if ("uchat-miniapp.json".equals(name)) {
                            data.write(buffer, 0, read);
                        }
                    }
                    if ("uchat-miniapp.json".equals(name)) {
                        if (manifest != null) {
                            throw ApiException.badRequest("INVALID_PACKAGE", "小程序包包含重复manifest");
                        }
                        manifest = data.toByteArray();
                    }
                }
                zip.closeEntry();
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw ApiException.badRequest("INVALID_PACKAGE", "无法读取小程序ZIP包");
        }
        if (manifest == null) {
            throw ApiException.badRequest("MANIFEST_MISSING", "小程序包根目录缺少uchat-miniapp.json");
        }
        return new ZipInspection(manifest, Set.copyOf(names));
    }

    private void validateManifest(JsonNode manifest, Set<String> entryNames) {
        if (manifest == null || !manifest.isObject()) {
            throw ApiException.badRequest("INVALID_MANIFEST", "manifest必须是JSON对象");
        }
        Set<String> keys = new HashSet<>();
        manifest.fieldNames().forEachRemaining(keys::add);
        if (!ALLOWED_MANIFEST_KEYS.containsAll(keys)) {
            throw ApiException.badRequest("INVALID_MANIFEST", "manifest包含未知字段");
        }
        JsonNode schemaVersion = manifest.get("schemaVersion");
        JsonNode appId = manifest.get("appId");
        JsonNode name = manifest.get("name");
        JsonNode version = manifest.get("version");
        JsonNode entryNode = manifest.get("entry");
        JsonNode description = manifest.get("description");
        if (schemaVersion == null || !schemaVersion.isInt() || schemaVersion.asInt() != 1
                || appId == null || !appId.isTextual()
                || name == null || !name.isTextual()
                || version == null || !version.isTextual()
                || entryNode == null || !entryNode.isTextual()
                || (description != null && !description.isTextual())) {
            throw ApiException.badRequest("INVALID_MANIFEST", "manifest字段类型或schemaVersion无效");
        }
        JsonNode permissions = manifest.get("permissions");
        if (permissions == null || !permissions.isArray()) {
            throw ApiException.badRequest("INVALID_MANIFEST", "permissions必须是数组");
        }
        if (!isValidAppId(appId.asText())) {
            throw ApiException.badRequest("INVALID_MANIFEST", "appId格式无效");
        }
        String appName = name.asText();
        if (appName.isEmpty() || appName.length() > 40 || hasDartTrimWhitespaceAtEdge(appName)) {
            throw ApiException.badRequest("INVALID_MANIFEST", "小程序名称无效");
        }
        String versionText = version.asText();
        if (versionText.length() > 32 || !VERSION_PATTERN.matcher(versionText).matches()) {
            throw ApiException.badRequest("INVALID_MANIFEST", "版本号必须为语义化三段数字");
        }
        if (description == null) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) manifest).put("description", "");
        } else if (description.asText().length() > 120) {
            throw ApiException.badRequest("INVALID_MANIFEST", "小程序简介不能超过120个字符");
        }
        String entry = entryNode.asText();
        validateEntryName(entry);
        if (!entry.toLowerCase(Locale.ROOT).endsWith(".html")) {
            throw ApiException.badRequest("INVALID_MANIFEST", "入口文件必须是HTML文件");
        }
        if (!entryNames.contains(entry)) {
            throw ApiException.badRequest("ENTRY_MISSING", "manifest指定的入口文件不存在");
        }
    }

    private static void validateEntryName(String name) {
        if (name == null || name.isEmpty() || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0
                || name.startsWith("/") || WINDOWS_DRIVE_PREFIX.matcher(name).find()) {
            throw ApiException.badRequest("UNSAFE_PACKAGE_PATH", "小程序包包含不安全的文件路径");
        }
        String normalized = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        String[] segments = normalized.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                    || segment.endsWith(".") || segment.endsWith(" ")) {
                throw ApiException.badRequest("UNSAFE_PACKAGE_PATH", "小程序包包含不安全的文件路径");
            }
            for (int index = 0; index < segment.length(); index++) {
                char character = segment.charAt(index);
                if (character < 32 || character == 127 || "<>:\"|?*".indexOf(character) >= 0) {
                    throw ApiException.badRequest("UNSAFE_PACKAGE_PATH", "小程序包包含不安全的文件路径");
                }
            }
            String deviceName = segment.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
            if (WINDOWS_RESERVED_NAMES.contains(deviceName)) {
                throw ApiException.badRequest("UNSAFE_PACKAGE_PATH", "小程序包包含不安全的文件路径");
            }
        }
    }

    private static void validatePathLimits(String value) {
        String[] segments = value.split("/", -1);
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_PATH_BYTES
                || segments.length > MAX_PATH_SEGMENTS) {
            throw ApiException.badRequest("INVALID_PACKAGE", "小程序包内文件路径超过限制");
        }
        for (String segment : segments) {
            if (segment.getBytes(StandardCharsets.UTF_8).length > MAX_PATH_SEGMENT_BYTES) {
                throw ApiException.badRequest("INVALID_PACKAGE", "小程序包内路径片段超过限制");
            }
        }
    }

    private static boolean isValidAppId(String value) {
        return value != null && value.length() <= 120 && APP_ID_PATTERN.matcher(value).matches();
    }

    private static void validateAppId(String value) {
        if (!isValidAppId(value)) {
            throw ApiException.badRequest("INVALID_APP_ID", "appId格式无效");
        }
    }

    private static boolean hasDartTrimWhitespaceAtEdge(String value) {
        int first = value.codePointAt(0);
        int last = value.codePointBefore(value.length());
        return isDartTrimWhitespace(first) || isDartTrimWhitespace(last);
    }

    private static boolean isDartTrimWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)
                || codePoint == 0x0085 || codePoint == 0xfeff;
    }

    private static String decodeUtf8Strict(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw ApiException.badRequest("INVALID_MANIFEST", "manifest不是有效的UTF-8文本");
        }
    }

    private static ImageFormat detectImage(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d
                && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return new ImageFormat("image/png", "png");
        }
        if (bytes.length >= 4 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff && (bytes[bytes.length - 2] & 0xff) == 0xff
                && (bytes[bytes.length - 1] & 0xff) == 0xd9) {
            return new ImageFormat("image/jpeg", "jpg");
        }
        throw ApiException.badRequest("INVALID_IMAGE", "小程序图片只能使用PNG或JPEG格式");
    }

    private static void validateImageContentType(String supplied, String detected) {
        if (supplied == null || supplied.isBlank()) {
            return;
        }
        String normalized = supplied.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals(detected)) {
            throw ApiException.badRequest("IMAGE_CONTENT_TYPE_MISMATCH", "图片类型与文件内容不一致");
        }
    }

    private static byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "上传文件不能为空");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw ApiException.badRequest("FILE_READ_FAILED", "无法读取上传文件");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ZipInspection(byte[] manifestBytes, Set<String> entryNames) {
    }

    public record StoredObject(byte[] bytes, String contentType) {
    }

    public record ActiveMiniApp(ActivationRequest activation, long updatedAt) {
    }

    private record ImageFormat(String contentType, String extension) {
    }
}
