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
被管理员封禁后的状态是 `BANNED`。该状态仍可登录、注销、调用 `GET /api/auth/me`，
并可查看和提交申诉；普通开发者管理接口统一返回 `ACCOUNT_BANNED`。

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

管理员分页接口统一返回：

```json
{"items":[],"page":1,"pageSize":20,"total":0}
```

`page` 从 1 开始，`pageSize` 为 1 至 100，默认分别为 1 和 20；搜索关键词最多 120 字符。

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

### 小程序管理

- `GET /api/admin/apps?keyword=&status=&page=1&pageSize=20`
- `GET /api/admin/apps/{id}`
- `PUT /api/admin/apps/{id}`，请求为 `{"name":"...","description":"..."}`
- `POST /api/admin/apps/{id}/assets/{kind}`，multipart 字段为 `file`
- `POST /api/admin/apps/{id}/publish`
- `POST /api/admin/apps/{id}/delist`

`status` 可为 `DRAFT`、`PUBLISHED` 或 `DELISTED`。管理员小程序响应字段是：

```text
id, appId, name, description, developerAccountId, developerName,
iconObjectKey, coverObjectKey, iconUrl, coverUrl, status, currentVersionId,
averageRating, ratingCount, commentCount, createdAt, updatedAt
```

`appId` 和 `developerName` 不可从管理员小程序编辑接口修改。名称最长 40 字符，简介最长
120 字符。管理员资产替换与开发者上传使用相同校验：`kind` 只能是 `icon` 或 `cover`，
图标最大 1 MiB、封面最大 3 MiB，仅接受真实内容与声明类型一致的 PNG/JPEG。

名称和简介属于 ZIP manifest 的完整性字段，`PUBLISHED` 状态下管理员和开发者都不能修改；
变更请求返回稳定错误 `APP_MUST_BE_DELISTED_FOR_METADATA_UPDATE`。正确流程是先下架、修改资料、
由开发者上传名称和简介完全匹配的新 manifest 版本，再由管理员审核。图标和封面不属于 manifest，
允许在已上架状态直接替换。

重新上架要求当前版本存在、属于该小程序、状态为 `APPROVED`，并且 appId、名称和简介仍与
已审核 manifest 一致。被封禁开发者的小程序不能上架，其待审核版本不能被管理员通过，但管理员
仍可将待审核版本决定为 `REJECTED`，避免队列永久残留。

### 评论管理

- `GET /api/admin/comments?keyword=&status=&miniAppId=&page=1&pageSize=20`
- `GET /api/admin/comments/{id}`
- `POST /api/admin/comments`
- `PUT /api/admin/comments/{id}`
- `DELETE /api/admin/comments/{id}`
- `PUT /api/admin/comments/{id}/featured`，保留的快捷精选接口

评论响应字段是：

```text
id, miniAppId, appId, appName, uchatUserId, userDisplayName, content,
featured, status, createdAt, updatedAt
```

创建请求：

```json
{
  "miniAppId": 1,
  "uchatUserId": 8001,
  "userDisplayName": "体验用户",
  "content": "交互清楚",
  "featured": false,
  "status": "VISIBLE"
}
```

更新请求不包含 `miniAppId` 和 `uchatUserId`，其余四个字段相同。`userDisplayName` 最长
120 字符，`content` 最长 500 字符，状态只能是 `VISIBLE` 或 `HIDDEN`。隐藏评论会被强制
取消精选，快捷接口也不能把隐藏评论设为精选。同一小程序和 UChat 用户重复创建返回
稳定错误 `COMMENT_EXISTS`。

```json
{"featured":true}
```

### 开发者封禁

- `GET /api/admin/developers?keyword=&status=&page=1&pageSize=20`
- `POST /api/admin/developers/{id}/ban`，请求 `{"reason":"..."}`
- `POST /api/admin/developers/{id}/unban`，请求 `{"note":"..."}`，`note` 可空

状态筛选支持 `PENDING`、`APPROVED`、`REJECTED`、`DISABLED`、`BANNED`。列表项包含完整
账号字段、`banReason`、`bannedBy`、`bannedAt`、`appCount` 和 `publishedAppCount`，因此前端可在
确认封禁前展示会下架的数量。封禁和解禁响应是：

```json
{"developer":{},"delistedAppCount":1}
```

封禁原因最长 500 字符。封禁会在同一数据库事务内锁定开发者，把账号改为 `BANNED`，并把
其全部 `PUBLISHED` 小程序改为 `DELISTED`；不会删除账号、版本、评分或评论。解禁不会自动
重新上架历史小程序。

发布网关属于外部系统，无法与 MySQL 建立分布式事务。`deactivate(appId)` 必须是幂等操作；
任一网关调用失败时平台数据库事务会回滚，管理员应使用相同请求重试。此前已经成功的
deactivate 不会因数据库回滚而自动恢复，因此生产运维需要监控失败并重试至数据库完成下架。
local 内存网关的 deactivate 使用幂等删除，搜索、详情和下载还会同时校验 DB `PUBLISHED`
状态与激活目录，失败或封禁后不会继续暴露小程序。

### 封禁申诉

- 开发者 `GET /api/developer/appeals`
- 开发者 `POST /api/developer/appeals`，请求 `{"content":"..."}`
- 管理员 `GET /api/admin/appeals?status=&developerAccountId=&page=1&pageSize=20`
- 管理员 `POST /api/admin/appeals/{id}/decision`

申诉内容最长 1000 字符，同一开发者同时只能有一个 `PENDING` 申诉。申诉响应字段是：

```text
id, developerAccountId, developerName, username, content, status,
reviewNote, reviewedBy, reviewedAt, createdAt, updatedAt
```

管理员决定请求仍为 `{"decision":"APPROVED|REJECTED","reviewNote":"..."}`。通过申诉会
解禁账号但不会重新上架小程序；拒绝后保持封禁。管理员直接解禁会在同一事务中把该开发者
现有待处理申诉标为 `APPROVED`，避免留下无法处理的待审记录。

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
已上架小程序的名称和简介同样不能从开发者接口直接修改，必须遵循“下架、改资料、提交匹配新包、
重新审核”的流程。

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

## 仅 local profile 的 UChat App 侧接口

这些接口直接位于服务根路径，不带 `/api` 前缀，接收纯 JSON，并返回与 Uchat-server 一致的成功包装：

```json
{"success":true,"code":200,"msg":"success","data":{}}
```

所有 `/miniApp/*` 请求必须携带正整数请求头 `userId`。local 不校验 token；评分和评论以该数值作为测试用户 ID，评论展示名确定为 `本地测试用户-{userId}`，`avatarUrl` 为 `null`。production 不注册这些控制器，不能把该规则当作生产认证。

### `POST /miniApp/search`

请求：

```json
{"keyword":"演示"}
```

只搜索数据库状态为 `PUBLISHED` 且仍在内存激活目录中的小程序，按名称和 appId 排序，最多返回
50 条。每条数据精确包含 `appId`、`name`、`version`、`description`。名称和简介取数据库当前
资料；由于发布态禁止修改这些 manifest 字段，数据库资料与激活 ZIP 保持一致。空关键词返回空数组，
下架后立即不再出现；匹配新资料的版本审核激活后，搜索、详情和下载会同时显示新资料。

### `POST /miniApp/prepareDownload`

请求：

```json
{"appId":"com.example.demo"}
```

数据精确包含 `appId`、`name`、`version`、`description`、`archiveSha256`、`archiveSize`、`updatedAt`、`downloadUrl`。
名称和简介取数据库当前资料，`updatedAt` 是激活时间与资料更新时间的较大毫秒值。下载同时要求
数据库为 `PUBLISHED` 且激活包存在。`downloadUrl` 是由
`MINIAPP_LOCAL_APP_PUBLIC_BASE_URL` 生成的绝对 HTTP(S) URL。Android 模拟器默认得到
`http://10.0.2.2:8091/local-packages?...`。

### 社区接口

- `POST /miniApp/detail`：请求 `{"appId":"..."}`，返回基础资料、图标/封面 objectKey、平均分、评分数、评论数、当前测试用户评分和最多 3 条精选评论。
- `POST /miniApp/comments`：请求 `{"appId":"...","page":1,"pageSize":20}`；page 从 1 开始，pageSize 为 1 至 100。
- `POST /miniApp/rating`：请求 `{"appId":"...","score":5}`；同一 `userId` 再次提交会更新评分。
- `POST /miniApp/comment`：请求 `{"appId":"...","content":"很好用"}`；同一 `userId` 再次提交会更新评论，最多 500 字符。

评论数据字段与 Flutter 模型一致：`id`、`displayName`、`avatarUrl`、`content`、`featured`、字符串时间 `createdAt`。评论分页数据为 `items`、`page`、`pageSize`、`total`。

详情返回的 `iconObjectKey` 和 `coverObjectKey` 保持 `assets/{appId}/{icon|cover}/...` 规则。Flutter 使用同一 local 基地址访问 `/oss/object?key=...`。ZIP 使用 `packages/{appId}/...`，只允许通过 prepareDownload 返回的 `/local-packages?key=...` 地址读取；两个对象端点不会接受对方的 key 类型或路径逃逸值。
