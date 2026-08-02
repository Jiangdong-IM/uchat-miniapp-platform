# 数据库协作约定

Flyway 初始化脚本位于 `backend/src/main/resources/db/migration/V1__create_mini_app_platform.sql`，不包含固定 schema 名称，由 JDBC URL 决定目标数据库。

生产环境连接的是已有 UChat 业务表的共享 schema。`application-production.yml` 配置 `baseline-on-migrate=true` 和 `baseline-version=0`，用于首次在非空 schema 中建立 Flyway 历史并确保 V1 仍会执行。不要把基线版本改成 `1`，否则平台建表迁移会被标记为已经执行。local profile 不启用 baseline，H2 会从空 schema 正常运行 V1。

表名固定为：

- `mini_app_developer_account`
- `mini_app_platform_session`
- `mini_app`
- `mini_app_version`
- `mini_app_rating`
- `mini_app_comment`

开发者账号独立于 UChat 用户。`mini_app.developer_account_id` 关联开发者账号；UChat 评分和评论通过 `mini_app_rating.uchat_user_id`、`mini_app_comment.uchat_user_id` 保存 UChat 用户 ID，不对 UChat `user` 表添加外键，以免影响既有用户生命周期。

UChat 用户侧写入约定：

- 评分范围为 1 至 5。
- `mini_app_rating` 由 `(mini_app_id, uchat_user_id)` 保证一名用户对一个小程序只有一条评分。
- 评论最长 500 字符。
- `mini_app_comment` 由 `(mini_app_id, uchat_user_id)` 保证一名用户对一个小程序只有一条当前评论。
- `user_display_name` 保存评论时的展示名快照。
- 评论展示状态使用 `status='VISIBLE'`；精选列的精确列名是 `is_featured`。
- `mini_app_id` 是 `mini_app.id` 的数值外键，不是字符串 `mini_app.app_id`。

管理平台对评分与评论只读，仅管理员可以切换评论的 `is_featured`。local profile 的模拟反馈接口只用于无外部服务联调，production 不注册该接口。
