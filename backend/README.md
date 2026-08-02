# UChat 小程序管理平台后端

Java 21 + Spring Boot 3.5。后端提供开发者注册审核、小程序资料与资产管理、版本审核发布、下架，以及评分和评论只读查询。

## 本地零中间件启动

默认 profile 是 `local`，数据使用进程内 H2，图标、封面、ZIP 包和已发布目录使用进程内存。无需安装 MySQL、MongoDB、Redis、S3，也无需启动 Uchat-server。进程退出后所有本地数据都会清空。

Windows：

```powershell
cd D:\Users\PC\IdeaProjects\uchat-miniapp-platform\backend
$env:JAVA_HOME = "你的 Java 21 目录"
.\mvnw.cmd spring-boot:run
```

健康检查：

```powershell
Invoke-RestMethod http://localhost:8091/api/health
```

本地管理员账号固定为 `admin`。如果没有设置 `MINIAPP_BOOTSTRAP_ADMIN_PASSWORD`，仅 `local` profile 会使用开发密码 `local-admin-12345`，启动日志会明确警告。联调时建议显式设置：

```powershell
$env:MINIAPP_BOOTSTRAP_ADMIN_PASSWORD = "请替换为至少12位的本地密码"
.\mvnw.cmd spring-boot:run
```

本地前端允许来源默认为 `http://localhost:5176` 和 `http://127.0.0.1:5176`。内存图片通过返回的 `/local-assets/...` 地址直接访问。

## 自动化测试

```powershell
.\mvnw.cmd test
```

集成测试在 H2 和内存发布网关上完整执行以下流程：待审核账号无法登录、管理员批准、开发者登录、创建小程序、上传图标/封面、上传并校验 ZIP、版本审核发布、内存资产访问、评分评论读取与精选、10 个小程序上限、跨账号越权保护和评分写接口隔离。

## 生产环境

生产环境只在 `production` profile 下启用 MySQL 和 Uchat-server 内部发布接口。所有敏感信息均由环境变量提供，仓库不包含数据库密码或内部令牌。

UChat 的共享数据库通常已经包含业务表。`production` profile 因此启用 Flyway `baseline-on-migrate=true`，并把基线版本固定为 `0`：首次接管未建立 Flyway 历史表的非空 schema 时会先记录版本 0，再继续执行本项目的 V1 迁移。该设置不在 `local` profile 生效，本地 H2 仍从空库直接执行 V1。

必要变量：

| 变量 | 用途 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=production` | 启用生产实现 |
| `MINIAPP_DB_URL` | 与 UChat 共用的 MySQL JDBC URL |
| `MINIAPP_DB_USERNAME` | MySQL 用户名 |
| `MINIAPP_DB_PASSWORD` | MySQL 密码 |
| `MINIAPP_BOOTSTRAP_ADMIN_PASSWORD` | 首次安全创建 `admin` 的密码，12 至 72 位 |
| `UCHAT_SERVER_BASE_URL` | Uchat-server 地址 |
| `UCHAT_MINI_APP_INTERNAL_TOKEN` | 内部接口令牌 |
| `MINIAPP_CORS_ALLOWED_ORIGINS` | 逗号分隔的前端来源 |
| `MINIAPP_ASSET_PUBLIC_BASE_URL` | 资产公开或签名访问地址前缀；未配置时 URL 字段为 `null` |

生产发布链路使用固定内部接口和 `X-Mini-App-Internal-Token` 请求头：

- `POST /miniApp/internal/assets`
- `POST /miniApp/internal/packages`
- `POST /miniApp/internal/activate`
- `POST /miniApp/internal/deactivate`

只有 Uchat-server 成功激活后，平台才会把版本改为 `APPROVED` 并更新当前线上版本。

详细接口见 [docs/API.md](docs/API.md)，数据库协作约定见 [../database/README.md](../database/README.md)。
