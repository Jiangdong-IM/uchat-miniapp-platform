<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ArrowUpCircle, Edit3, Image, RefreshCw, Search, ShieldAlert } from '@lucide/vue'
import { api } from '../../api/client.js'
import EmptyState from '../../components/common/EmptyState.vue'
import ModalDialog from '../../components/common/ModalDialog.vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import StatusBadge from '../../components/common/StatusBadge.vue'
import { asList, formatDateTime, formatScore } from '../../utils/format.js'

const emit = defineEmits(['notice'])
const result = ref({ items: [], page: 1, pageSize: 20, total: 0 })
const query = reactive({ keyword: '', status: '', page: 1, pageSize: 20 })
const loading = ref(true)
const pageError = ref('')
const dialog = ref(null)
const dialogBusy = ref(false)
const dialogLoading = ref(false)
const dialogError = ref('')
const form = reactive({ name: '', description: '' })
const assetFiles = reactive({ icon: null, cover: null })
const assetPreviewUrls = reactive({ icon: '', cover: '' })
const assetPreviewErrors = reactive({ icon: false, cover: false })

const actionTitle = computed(() => dialog.value?.type === 'publish' ? '确认上架小程序' : '确认下架小程序')
const actionButtonLabel = computed(() => dialog.value?.type === 'publish' ? '确认上架' : '确认下架')

async function load() {
  loading.value = true
  pageError.value = ''
  try {
    const data = await api.admin.apps(query)
    const pageSize = Number(data?.pageSize || query.pageSize)
    const total = Number(data?.total || 0)
    const lastPage = Math.max(1, Math.ceil(total / pageSize))
    if (query.page > lastPage) {
      query.page = lastPage
      await load()
      return
    }
    result.value = {
      items: asList(data?.items),
      page: Number(data?.page || query.page),
      pageSize,
      total,
    }
  } catch (error) {
    pageError.value = error.message
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function changePage(page) {
  query.page = page
  load()
}

async function openEdit(item) {
  clearAssets()
  Object.assign(form, { name: item.name || '', description: item.description || '' })
  dialog.value = { type: 'edit', app: item }
  dialogLoading.value = true
  dialogError.value = ''
  try {
    const app = await api.admin.app(item.id)
    dialog.value = { type: 'edit', app }
    Object.assign(form, { name: app.name || '', description: app.description || '' })
  } catch (error) {
    dialogError.value = error.message
  } finally {
    dialogLoading.value = false
  }
}

function openAction(type, app) {
  clearAssets()
  dialogError.value = ''
  dialog.value = { type, app }
}

function closeDialog() {
  if (!dialogBusy.value) {
    clearAssets()
    dialog.value = null
  }
}

function releaseAssetPreview(kind) {
  if (assetPreviewUrls[kind]) URL.revokeObjectURL(assetPreviewUrls[kind])
  assetPreviewUrls[kind] = ''
}

function clearAssets() {
  for (const kind of ['icon', 'cover']) {
    releaseAssetPreview(kind)
    assetFiles[kind] = null
    assetPreviewErrors[kind] = false
  }
}

function assetPreviewUrl(kind) {
  if (assetPreviewUrls[kind]) return assetPreviewUrls[kind]
  return kind === 'icon' ? dialog.value?.app?.iconUrl : dialog.value?.app?.coverUrl
}

function chooseAsset(kind, event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  const maximumSize = kind === 'icon' ? 1024 * 1024 : 3 * 1024 * 1024
  if (!['image/png', 'image/jpeg'].includes(file.type)) {
    dialogError.value = '只能选择 PNG 或 JPEG 图片。'
    return
  }
  if (file.size > maximumSize) {
    dialogError.value = kind === 'icon' ? '图标不能超过 1 MiB。' : '封面不能超过 3 MiB。'
    return
  }
  releaseAssetPreview(kind)
  assetFiles[kind] = file
  assetPreviewUrls[kind] = URL.createObjectURL(file)
  assetPreviewErrors[kind] = false
  dialogError.value = ''
}

async function saveApp() {
  dialogBusy.value = true
  dialogError.value = ''
  try {
    if (dialog.value.app.status !== 'PUBLISHED') {
      await api.admin.updateApp(dialog.value.app.id, form)
    }
    if (assetFiles.icon) await api.admin.uploadAppAsset(dialog.value.app.id, 'icon', assetFiles.icon)
    if (assetFiles.cover) await api.admin.uploadAppAsset(dialog.value.app.id, 'cover', assetFiles.cover)
    clearAssets()
    dialog.value = null
    emit('notice', '小程序资料已更新。')
    await load()
  } catch (error) {
    dialogError.value = error.message
  } finally {
    dialogBusy.value = false
  }
}

async function submitAction() {
  const { type, app } = dialog.value
  dialogBusy.value = true
  dialogError.value = ''
  try {
    if (type === 'publish') await api.admin.publishApp(app.id)
    else await api.admin.delistApp(app.id)
    dialog.value = null
    emit('notice', type === 'publish' ? '小程序已上架。' : '小程序已下架。')
    await load()
  } catch (error) {
    dialogError.value = error.message
  } finally {
    dialogBusy.value = false
  }
}

onMounted(load)
onBeforeUnmount(clearAssets)
</script>

<template>
  <div class="page-frame">
    <header class="page-heading">
      <div><p class="eyebrow">ADMIN / CATALOG</p><h1>小程序目录</h1><p>检索全平台小程序，维护公开资料并控制上下架状态。</p></div>
      <button class="button button--ghost" type="button" :disabled="loading" @click="load"><RefreshCw :size="16" /> 刷新</button>
    </header>

    <form class="admin-filter-bar" role="search" @submit.prevent="search">
      <label class="admin-search"><Search :size="17" aria-hidden="true" /><span class="sr-only">搜索小程序</span><input v-model.trim="query.keyword" type="search" maxlength="120" placeholder="搜索名称、App ID 或开发者" /></label>
      <label class="filter-select"><span>状态</span><select v-model="query.status" @change="search"><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="PUBLISHED">已上架</option><option value="DELISTED">已下架</option></select></label>
      <button class="button button--secondary" type="submit" :disabled="loading">搜索</button>
    </form>

    <p v-if="pageError" class="form-error page-error" role="alert">{{ pageError }}</p>
    <div v-if="loading" class="admin-record-list"><div v-for="item in 5" :key="item" class="skeleton-row"></div></div>
    <section v-else-if="result.items.length" class="admin-record-list" aria-label="小程序列表">
      <article v-for="app in result.items" :key="app.id" class="admin-app-record">
        <div class="admin-app-record__visual">
          <img v-if="app.iconUrl" :src="app.iconUrl" :alt="`${app.name} 图标`" />
          <span v-else aria-hidden="true">{{ (app.name || 'A').slice(0, 1) }}</span>
        </div>
        <div class="admin-app-record__identity">
          <div><StatusBadge :status="app.status" /><code>{{ app.appId }}</code></div>
          <h2>{{ app.name }}</h2>
          <p>{{ app.description }}</p>
          <small>{{ app.developerName }} · 更新于 {{ formatDateTime(app.updatedAt) }}</small>
        </div>
        <dl class="admin-app-record__metrics">
          <div><dt>评分</dt><dd>{{ formatScore(app.averageRating) }}</dd></div>
          <div><dt>评分数</dt><dd>{{ app.ratingCount }}</dd></div>
          <div><dt>评论</dt><dd>{{ app.commentCount }}</dd></div>
          <div><dt>当前版本</dt><dd>{{ app.currentVersionId || '—' }}</dd></div>
        </dl>
        <div class="admin-record-actions">
          <button class="button button--compact button--ghost" type="button" @click="openEdit(app)"><Edit3 :size="15" /> 编辑信息</button>
          <button v-if="app.status === 'PUBLISHED'" class="button button--compact button--danger-quiet" type="button" @click="openAction('delist', app)"><ShieldAlert :size="15" /> 下架</button>
          <button
            v-else
            class="button button--compact button--positive"
            type="button"
            :disabled="!app.currentVersionId || !app.iconObjectKey || !app.coverObjectKey"
            :title="!app.currentVersionId ? '需要已审核版本才能上架' : (!app.iconObjectKey || !app.coverObjectKey ? '需要完整的图标和封面才能上架' : '上架小程序')"
            @click="openAction('publish', app)"
          ><ArrowUpCircle :size="15" /> 上架</button>
        </div>
      </article>
      <PaginationControls :page="result.page" :page-size="result.pageSize" :total="result.total" :loading="loading" @change="changePage" />
    </section>
    <EmptyState v-else title="没有匹配的小程序" description="请调整关键词或状态筛选后再试。" />

    <ModalDialog v-if="dialog?.type === 'edit'" :open="true" title="修改小程序信息" :description="dialog.app.appId" :busy="dialogBusy" @close="closeDialog">
      <div v-if="dialogLoading" class="skeleton-row"></div>
      <form v-else id="admin-app-edit" class="form-stack" @submit.prevent="saveApp">
        <div class="record-lock"><span>App ID 不可修改</span><strong>{{ dialog.app.appId }}</strong></div>
        <p v-if="dialog.app.status === 'PUBLISHED'" class="decision-warning">已上架应用需先下架，再修改名称或简介，并由开发者提交与新资料匹配的版本；图标和封面可直接替换。</p>
        <label class="field"><span>小程序名称</span><input v-model.trim="form.name" required maxlength="40" :disabled="dialog.app.status === 'PUBLISHED'" /><small>{{ form.name.length }}/40</small></label>
        <label class="field"><span>简介</span><textarea v-model.trim="form.description" required maxlength="120" rows="5" :disabled="dialog.app.status === 'PUBLISHED'"></textarea><small>{{ form.description.length }}/120</small></label>
        <div class="asset-upload-grid admin-asset-upload-grid">
          <label class="asset-uploader" :class="{ 'asset-uploader--has-preview': assetPreviewUrl('icon') && !assetPreviewErrors.icon }">
            <input type="file" accept="image/png,image/jpeg" :disabled="dialogBusy" @change="chooseAsset('icon', $event)" />
            <img v-if="assetPreviewUrl('icon') && !assetPreviewErrors.icon" class="asset-uploader__preview asset-uploader__preview--icon" :src="assetPreviewUrl('icon')" :alt="`${form.name} 图标预览`" @error="assetPreviewErrors.icon = true" />
            <span class="asset-uploader__content"><span v-if="!assetPreviewUrl('icon') || assetPreviewErrors.icon" class="asset-uploader__placeholder"><Image :size="22" /></span><strong>小程序图标</strong><small>PNG/JPEG，最大 1 MiB</small><em>{{ assetFiles.icon ? '已选择，保存后上传' : '点击替换' }}</em></span>
          </label>
          <label class="asset-uploader" :class="{ 'asset-uploader--has-preview': assetPreviewUrl('cover') && !assetPreviewErrors.cover }">
            <input type="file" accept="image/png,image/jpeg" :disabled="dialogBusy" @change="chooseAsset('cover', $event)" />
            <img v-if="assetPreviewUrl('cover') && !assetPreviewErrors.cover" class="asset-uploader__preview" :src="assetPreviewUrl('cover')" :alt="`${form.name} 封面预览`" @error="assetPreviewErrors.cover = true" />
            <span class="asset-uploader__content"><span v-if="!assetPreviewUrl('cover') || assetPreviewErrors.cover" class="asset-uploader__placeholder"><Image :size="22" /></span><strong>详情封面</strong><small>PNG/JPEG，最大 3 MiB</small><em>{{ assetFiles.cover ? '已选择，保存后上传' : '点击替换' }}</em></span>
          </label>
        </div>
        <p v-if="dialogError" class="form-error" role="alert">{{ dialogError }}</p>
      </form>
      <template #footer><button class="button button--ghost" type="button" :disabled="dialogBusy" @click="closeDialog">取消</button><button class="button button--primary" type="submit" form="admin-app-edit" :disabled="dialogBusy || dialogLoading || (dialog.app.status === 'PUBLISHED' && !assetFiles.icon && !assetFiles.cover)">{{ dialogBusy ? '保存中…' : dialog.app.status === 'PUBLISHED' ? '保存素材' : '保存修改' }}</button></template>
    </ModalDialog>

    <ModalDialog v-else-if="dialog" :open="true" :title="actionTitle" :description="`${dialog.app.name} · ${dialog.app.appId}`" :busy="dialogBusy" @close="closeDialog">
      <div class="impact-confirmation" :class="{ 'impact-confirmation--danger': dialog.type === 'delist' }">
        <strong>本次操作影响 1 个小程序</strong>
        <p v-if="dialog.type === 'delist'">确认后将立即从公开目录下架，UChat 用户不能再打开该小程序。</p>
        <p v-else>确认后将使用与当前名称、简介一致的已审核版本重新进入公开目录；历史下架记录不会被抹除。</p>
      </div>
      <p v-if="dialogError" class="form-error" role="alert">{{ dialogError }}</p>
      <template #footer><button class="button button--ghost" type="button" :disabled="dialogBusy" @click="closeDialog">取消</button><button class="button" :class="dialog.type === 'delist' ? 'button--danger' : 'button--positive'" type="button" :disabled="dialogBusy" @click="submitAction">{{ dialogBusy ? '处理中…' : actionButtonLabel }}</button></template>
    </ModalDialog>
  </div>
</template>
