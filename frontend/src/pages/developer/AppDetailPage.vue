<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ArrowLeft, FileArchive, Image, Save, Star, UploadCloud } from '@lucide/vue'
import { api } from '../../api/client.js'
import StatusBadge from '../../components/common/StatusBadge.vue'
import ReviewList from '../../components/apps/ReviewList.vue'
import { asList, formatDateTime, formatScore } from '../../utils/format.js'

const props = defineProps({ app: { type: Object, required: true } })
const emit = defineEmits(['back', 'updated', 'notice'])
const activeTab = ref('overview')
const busy = ref(false)
const error = ref('')
const versions = ref([])
const reviews = ref([])
const detail = ref(props.app)
const form = reactive({ name: props.app.name || '', description: props.app.description || '' })
const versionFile = ref(null)
const releaseNotes = ref('')

const tabs = [
  { id: 'overview', label: '上架资料' },
  { id: 'versions', label: '版本记录' },
  { id: 'reviews', label: '用户评价' },
]
const score = computed(() => formatScore(detail.value.averageRating))

async function load() {
  const [appData, versionData, reviewData] = await Promise.all([
    api.developer.app(props.app.id),
    api.developer.versions(props.app.id),
    api.developer.reviews(props.app.id),
  ])
  detail.value = appData
  versions.value = asList(versionData)
  reviews.value = asList(reviewData?.comments).map((item) => ({
    ...item,
    displayName: item.displayName || item.userDisplayName,
  }))
  form.name = appData.name
  form.description = appData.description
}

async function guarded(action, successMessage) {
  busy.value = true
  error.value = ''
  try {
    await action()
    await load()
    emit('updated', detail.value)
    if (successMessage) emit('notice', successMessage)
  } catch (requestError) {
    error.value = requestError.message
  } finally {
    busy.value = false
  }
}

function saveMetadata() {
  return guarded(() => api.developer.updateApp(detail.value.id, form), '上架资料已保存。')
}

function uploadAsset(kind, event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  guarded(() => api.developer.uploadAsset(detail.value.id, kind, file), `${kind === 'icon' ? '图标' : '封面'}已上传。`)
}

function submitVersion() {
  if (!versionFile.value) {
    error.value = '请选择 ZIP 程序包。'
    return
  }
  guarded(async () => {
    await api.developer.uploadVersion(detail.value.id, versionFile.value, releaseNotes.value)
    versionFile.value = null
    releaseNotes.value = ''
  }, '版本已提交，等待 admin 审核。')
}

function delist() {
  if (!window.confirm('确定下架这个小程序？用户将无法继续搜索或获取新版本。')) return
  guarded(() => api.developer.delist(detail.value.id), '小程序已下架，历史评价仍会保留。')
}

watch(() => props.app.id, load)
onMounted(() => load().catch((requestError) => { error.value = requestError.message }))
</script>

<template>
  <div class="page-frame">
    <button class="back-link" type="button" @click="emit('back')"><ArrowLeft :size="17" /> 返回我的小程序</button>
    <header class="app-detail-hero">
      <div class="app-icon app-icon--large"><img v-if="detail.iconUrl" :src="detail.iconUrl" :alt="`${detail.name} 图标`" /><span v-else>{{ (detail.name || '?').slice(0, 1) }}</span></div>
      <div class="app-detail-hero__copy"><p class="mono-label">{{ detail.appId }}</p><h1>{{ detail.name }}</h1><div><StatusBadge :status="detail.status" /><span>开发者：{{ detail.developerName }}</span></div></div>
      <div class="app-detail-score"><Star :size="20" fill="currentColor" /><strong>{{ score }}</strong><span>{{ detail.ratingCount || 0 }} 人评分<br />{{ detail.commentCount || 0 }} 条评论</span></div>
    </header>

    <nav class="content-tabs" aria-label="小程序详情">
      <button v-for="tab in tabs" :key="tab.id" type="button" :class="{ active: activeTab === tab.id }" @click="activeTab = tab.id">{{ tab.label }}</button>
    </nav>
    <p v-if="error" class="form-error page-error" role="alert">{{ error }}</p>

    <div v-if="activeTab === 'overview'" class="detail-columns">
      <section class="work-panel">
        <div class="section-heading section-heading--compact"><div><p class="eyebrow">公开信息</p><h2>名称与简介</h2></div><span class="section-index">B / 01</span></div>
        <form class="form-stack" @submit.prevent="saveMetadata">
          <label class="field"><span>小程序名称</span><input v-model.trim="form.name" required maxlength="40" /><small>{{ form.name.length }}/40，须与 manifest 一致</small></label>
          <label class="field"><span>简介</span><textarea v-model.trim="form.description" required maxlength="120" rows="5"></textarea><small>{{ form.description.length }}/120，须与 manifest 一致</small></label>
          <button class="button button--secondary" type="submit" :disabled="busy"><Save :size="16" /> 保存资料</button>
        </form>
      </section>
      <section class="work-panel">
        <div class="section-heading section-heading--compact"><div><p class="eyebrow">视觉素材</p><h2>图标与封面</h2></div><span class="section-index">B / 02</span></div>
        <div class="asset-upload-grid">
          <label class="asset-uploader"><input type="file" accept="image/png,image/jpeg" :disabled="busy" @change="uploadAsset('icon', $event)" /><span><Image :size="22" /><strong>小程序图标</strong><small>PNG/JPEG，最大 1 MiB</small><em>{{ detail.iconObjectKey ? '重新上传' : '选择图片' }}</em></span></label>
          <label class="asset-uploader asset-uploader--cover"><input type="file" accept="image/png,image/jpeg" :disabled="busy" @change="uploadAsset('cover', $event)" /><span><Image :size="22" /><strong>详情封面</strong><small>PNG/JPEG，最大 3 MiB</small><em>{{ detail.coverObjectKey ? '重新上传' : '选择图片' }}</em></span></label>
        </div>
      </section>
      <section class="work-panel work-panel--full">
        <div class="section-heading section-heading--compact"><div><p class="eyebrow">新版本</p><h2>上传并提交审核</h2></div><span class="section-index">B / 03</span></div>
        <div class="version-upload-row">
          <label class="package-picker"><input type="file" accept=".zip,application/zip" :disabled="busy" @change="versionFile = $event.target.files?.[0] || null" /><FileArchive :size="24" /><span><strong>{{ versionFile?.name || '选择 ZIP 程序包' }}</strong><small>服务端会校验 manifest、权限、摘要与文件结构</small></span></label>
          <label class="field"><span>版本说明</span><textarea v-model.trim="releaseNotes" maxlength="500" rows="3" placeholder="这次更新解决了什么？"></textarea></label>
          <button class="button button--primary" type="button" :disabled="busy || !versionFile || !detail.iconObjectKey || !detail.coverObjectKey" @click="submitVersion"><UploadCloud :size="17" /> 提交审核</button>
        </div>
        <p v-if="!detail.iconObjectKey || !detail.coverObjectKey" class="form-hint">提交版本前必须先上传图标和封面。</p>
      </section>
      <section v-if="detail.status === 'PUBLISHED'" class="danger-zone"><div><strong>下架小程序</strong><p>下架后将从 UChat 搜索与下载目录移除，但历史版本和评价会保留。</p></div><button class="button button--danger" type="button" :disabled="busy" @click="delist">立即下架</button></section>
    </div>

    <section v-else-if="activeTab === 'versions'" class="work-panel">
      <div class="section-heading"><div><p class="eyebrow">审核台账</p><h2>版本记录</h2></div><span class="section-index">C / 01</span></div>
      <ol v-if="versions.length" class="version-ledger">
        <li v-for="version in versions" :key="version.id"><span class="version-ledger__rail"></span><div class="version-ledger__stamp"><b>v{{ version.manifest?.version }}</b><small>{{ formatDateTime(version.createdAt) }}</small></div><div class="version-ledger__body"><StatusBadge :status="version.status" /><p>{{ version.releaseNotes || '未填写版本说明' }}</p><small v-if="version.reviewNote">审核备注：{{ version.reviewNote }}</small><code>{{ version.manifest?.archiveSha256 ? version.manifest.archiveSha256.slice(0, 16) + '…' : '等待包体摘要' }}</code></div></li>
      </ol>
      <div v-else class="inline-empty">还没有提交过版本。</div>
    </section>

    <section v-else class="work-panel">
      <div class="section-heading"><div><p class="eyebrow">真实反馈</p><h2>UChat 用户评价</h2></div><span class="section-index">D / 01</span></div>
      <ReviewList :reviews="reviews" />
    </section>
  </div>
</template>
