# 后端 API 契约

基础地址默认是 `http://localhost:8091`。除注册、登录、健康检查和本地资产外，接口都需要：

```http
Authorization: Bearer <opaque-token>
```

令牌随机生成，有效期默认 24 小时；数据库只保存 SHA-256 摘要。密码使用 BCrypt。

统一响应：

```json
{
  "success": true,
  "code": "OK",
  "message": "成功",
  "data": {}
}
```

错误响应保持相同结构，`success=false`，`code` 是稳定错误码，`data=null`，不会返回堆栈或底层异常。

## 认证与注册

### `POST /api/auth/register`

```json
{
  "username": "demo_developer",
  "password": "password-123",
  "purpose": "开发团队协作工具",
  "planDescription": "先发布演示版，再按反馈更新",
  "developerName": "示例工作室",
  "contactEmail": "developer@example.com",
  "organizationName": "示例组织"
}
```

注册后状态是 `PENDING`，批准前登录返回 `ACCOUNT_PENDING`。用户名会统一保存为小写。`organizationName` 可空。

### `POST /api/auth/login`

```json
{"username":"demo_developer","password":"password-123"}
```

成功数据：

```json
{"token":"...","expiresAt":"...","account":{}}
```

### 其他认证接口

- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/health`，无需登录

## 管理员

- `GET /api/admin/registrations?status=PENDING`
- `POST /api/admin/registrations/{id}/decision`

注册审核请求：

```json
{"decision":"APPROVED","reviewNote":"资料完整"}
```

`decision` 只能是 `APPROVED` 或 `REJECTED`，同一申请只能审核一次。

- `GET /api/admin/versions?status=PENDING_REVIEW`
- `POST /api/admin/versions/{id}/decision`

版本审核请求同样使用 `decision` 和 `reviewNote`，决定只能是 `APPROVED` 或 `REJECTED`。

- `GET /api/admin/comments`
- `PUT /api/admin/comments/{id}/featured`

评论列表按精选优先、提交时间倒序返回；每项包含 `id`、`appId`、`appName`、
`userDisplayName`、`content`、`featured`、`status` 和 `createdAt`。

```json
{"featured":true}
```

## 开发者小程序

- `GET /api/developer/dashboard`
- `GET /api/developer/apps`
- `POST /api/developer/apps`
- `GET /api/developer/apps/{id}`
- `PUT /api/developer/apps/{id}`
- `POST /api/developer/apps/{id}/assets/{kind}`
- `POST /api/developer/apps/{id}/versions`
- `POST /api/developer/apps/{id}/delist`
- `GET /api/developer/apps/{id}/versions`
- `GET /api/developer/apps/{id}/reviews`

创建请求：

```json
{
  "appId": "com.example.demo",
  "name": "演示小程序",
  "description": "展示 UChat 小程序开放能力"
}
```

`appId` 不可修改，必须使用小写反向域名格式，最长 120 字符。名称最长 40 字符，简介最长 120 字符。每个开发者账号的上限在事务内锁定为 10 个。

小程序响应字段：

```text
id, appId, name, description, developerName,
iconObjectKey, coverObjectKey, iconUrl, coverUrl,
status, currentVersionId, averageRating, ratingCount,
commentCount, createdAt, updatedAt
```

资产接口使用 `multipart/form-data`，字段名为 `file`，`kind` 只能是 `icon` 或 `cover`：图标最大 1 MiB，封面最大 3 MiB，仅接受内容与声明类型一致的 PNG/JPEG。

版本接口使用 `multipart/form-data`：

- `file`：不超过 10 MiB 的 ZIP
- `releaseNotes`：可选，最长 1000 字符

ZIP 根目录必须包含 `uchat-miniapp.json`。平台会校验包后保存七个 manifest 字段快照：`schemaVersion`、`appId`、`name`、`version`、`entry`、`permissions`、`description`。其中 `schemaVersion` 必须为 `1`，版本号必须是三段数字，入口必须是包内 HTML 文件，权限只允许 `chooseImage`、`chooseChatMedia`、`getUserInfo`、`getLocation`、`sendMessage`。包内 `appId`、名称和简介必须与平台资料完全一致。

版本响应包含 `id`、`miniAppId`、`appName`、`developerName`、嵌套的 `manifest`、`releaseNotes`、`status`、审核字段和时间字段。

dashboard 数据：

```text
appCount, publishedCount, pendingVersionCount, averageRating, recentApps
```

评价接口返回：

```text
miniAppId, averageRating, ratingCount, commentCount,
ratings, featuredComments, comments
```

开发者端只有评价读取接口。评分和评论由 UChat 用户侧写入，管理平台不能代替用户提交。

## 仅 local profile 的反馈模拟

为了在没有 UChat 的情况下演示评价，local profile 额外提供管理员接口：

`POST /api/local/feedback`

```json
{
  "miniAppId": 1,
  "uchatUserId": 8001,
  "userDisplayName": "本地体验用户",
  "score": 5,
  "content": "交互清楚，运行顺畅"
}
```

此接口在 `production` profile 中不存在。
