# 管理平台前端

Vue 3 + Vite，包含开发者注册、账号审核、应用资料、版本上传与审核、下架、评分和评论查看界面。

```powershell
npm install
npm run dev
```

默认访问 `http://localhost:5176`，并把 `/api`、`/local-assets` 代理到 `http://localhost:8091`。可通过 `VITE_DEV_API_TARGET` 修改开发代理目标，通过 `VITE_API_BASE_URL` 修改前端 API 前缀。

生产构建：

```powershell
npm run build
```

生产环境应由反向代理把 `/api` 指向平台后端；本地内存资产路径仅在 local profile 存在。
