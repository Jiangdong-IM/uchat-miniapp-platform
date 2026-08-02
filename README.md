# UChat 小程序管理平台

项目分为 Vue 前端与 Java 后端：

```text
uchat-miniapp-platform/
├─ frontend/   Vue 3 + Vite 管理界面
├─ backend/    Java 21 + Spring Boot API
└─ database/   与 UChat 共库的数据约定
```

## 本地启动：不需要任何中间件

默认使用 `local` profile：账号、应用、审核、评分和评论进入进程内 H2；图标、封面、ZIP 与发布目录进入 Java 进程内存。无需安装或启动 MySQL、MongoDB、Redis、S3、Docker、Uchat-server。后端停止后，本地数据会清空。

### 1. 启动后端

```powershell
cd D:\Users\PC\IdeaProjects\uchat-miniapp-platform\backend
$env:JAVA_HOME = 'D:\Users\PC\.jdks\corretto-21.0.9'
.\mvnw.cmd spring-boot:run
```

后端地址为 `http://localhost:8091`，健康检查：

```powershell
Invoke-RestMethod http://localhost:8091/api/health
```

本地管理员：

- 账号：`admin`
- 开发密码：`local-admin-12345`

该密码只在 `local` profile 且未设置环境变量时启用。也可以在启动前设置 `MINIAPP_BOOTSTRAP_ADMIN_PASSWORD` 覆盖它。

### 2. 启动前端

```powershell
cd D:\Users\PC\IdeaProjects\uchat-miniapp-platform\frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5176`。Vite 已代理 `/api` 和 `/local-assets` 到 8091，无需额外配置跨域。

### 3. 本地验证流程

1. 在“申请账号”填写用途、开发计划、开发者名称和联系邮箱。
2. 用 `admin` 登录，在“注册审核”中批准申请。
3. 使用开发者账号登录，创建小程序并上传 PNG/JPEG 图标、封面。
4. 上传 ZIP；包根目录必须直接包含 `uchat-miniapp.json` 和入口 HTML。
5. 再次使用 `admin` 登录，在“版本审核”中批准版本。
6. 如需演示评价，可按 [后端 API 文档](backend/docs/API.md#仅-local-profile-的反馈模拟) 调用仅 local 存在的反馈模拟接口。

UChat 仓库自带的演示包也可用于上传测试，但创建的 appId、名称和简介必须与其 manifest 完全一致：

```text
D:\Users\PC\IdeaProjects\uchat\assets\mini_apps\mini_app_demo.zip
```

## 自动化验证

```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
npm run build
npm audit --audit-level=high
```

后端集成测试会在单进程内执行注册、审核、建应用、上传素材和 ZIP、发布、模拟反馈、精选评价、10 个应用上限及跨账号越权检查。

## 生产接入

生产使用 `SPRING_PROFILES_ACTIVE=production`：管理数据写入与 UChat 相同的 MySQL schema；程序包和素材通过带内部令牌的 Uchat-server 接口校验与发布，平台前端不能直接写 MongoDB 或对象存储。数据库名不写死，由 `MINIAPP_DB_URL` 决定。

必需配置、内部接口和部署注意事项见：

- [后端运行说明](backend/README.md)
- [完整 API 契约](backend/docs/API.md)
- [共享数据库约定](database/README.md)

UChat 用户侧的详情、评分、评论接口位于相邻的 `Uchat-server` 项目；小程序运行页的 more 弹窗位于相邻的 Flutter `uchat` 项目。
