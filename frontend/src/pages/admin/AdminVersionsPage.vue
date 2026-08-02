<script setup>
import { onMounted, ref } from 'vue'
import { Check, FileArchive, RefreshCw, ShieldCheck, X } from '@lucide/vue'
import { api } from '../../api/client.js'
import EmptyState from '../../components/common/EmptyState.vue'
import ModalDialog from '../../components/common/ModalDialog.vue'
import StatusBadge from '../../components/common/StatusBadge.vue'
import { asList, formatDateTime } from '../../utils/format.js'

const emit = defineEmits(['notice'])
const versions = ref([])
const loading = ref(true)
const selected = ref(null)
const decision = ref(null)
const reason = ref('')
const busy = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try { versions.value = asList(await api.admin.versions('PENDING_REVIEW')) }
  catch (requestError) { error.value = requestError.message }
  finally { loading.value = false }
}
function openDecision(item, approved) { selected.value = item; decision.value = approved; reason.value = '' }
async function submitDecision() {
  busy.value = true
  error.value = ''
  try {
    await api.admin.decideVersion(selected.value.id, { approved: decision.value, reason: reason.value })
    selected.value = null
    emit('notice', decision.value ? '版本已审核通过并激活到 UChat。' : '版本已拒绝。')
    await load()
  } catch (requestError) { error.value = requestError.message }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page-frame">
    <header class="page-heading"><div><p class="eyebrow">ADMIN / RELEASE</p><h1>小程序版本审核</h1><p>审核通过后才会激活正式目录；失败时保持当前线上版本不变。</p></div><button class="button button--ghost" type="button" :disabled="loading" @click="load"><RefreshCw :size="16" /> 刷新</button></header>
    <div class="integrity-note"><ShieldCheck :size="19" /><div><strong>包体已完成服务端预校验</strong><span>仍需人工核对用途、权限范围、版本说明与公开资料。</span></div></div>
    <p v-if="error" class="form-error page-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="audit-list"><div v-for="item in 4" :key="item" class="skeleton-row"></div></div>
    <div v-else-if="versions.length" class="version-audit-grid">
      <article v-for="item in versions" :key="item.id" class="version-audit-card">
        <div class="version-audit-card__top"><div class="package-glyph"><FileArchive :size="23" /></div><div><p class="mono-label">{{ item.manifest?.appId }}</p><h2>{{ item.appName || item.manifest?.name }}</h2></div><StatusBadge :status="item.status" /></div>
        <div class="version-audit-card__version"><span>待审版本</span><strong>v{{ item.manifest?.version }}</strong><small>{{ formatDateTime(item.createdAt) }}</small></div>
        <dl><div><dt>开发者</dt><dd>{{ item.developerName }}</dd></div><div><dt>入口文件</dt><dd><code>{{ item.manifest?.entry }}</code></dd></div><div><dt>申请权限</dt><dd>{{ (item.manifest?.permissions || []).join('、') || '无' }}</dd></div><div><dt>包体摘要</dt><dd><code>{{ item.manifest?.archiveSha256?.slice(0, 20) }}…</code></dd></div></dl>
        <p class="version-audit-card__notes">{{ item.releaseNotes || '开发者未填写版本说明。' }}</p>
        <div class="version-audit-card__actions"><button class="button button--danger-quiet" type="button" @click="openDecision(item, false)"><X :size="16" /> 拒绝</button><button class="button button--positive" type="button" @click="openDecision(item, true)"><Check :size="16" /> 通过并发布</button></div>
      </article>
    </div>
    <EmptyState v-else title="没有待审核版本" description="开发者上传并通过包体校验后，版本会进入这里。" />

    <ModalDialog :open="Boolean(selected)" :title="decision ? '批准并激活版本' : '拒绝这个版本'" :description="selected ? `${selected.appName || selected.manifest?.name} · v${selected.manifest?.version}` : ''" :busy="busy" @close="selected = null">
      <form id="version-decision" class="form-stack" @submit.prevent="submitDecision"><label class="field"><span>审核备注 {{ decision ? '（选填）' : '' }}</span><textarea v-model.trim="reason" :required="!decision" maxlength="500" rows="5"></textarea><small>{{ reason.length }}/500</small></label><p v-if="decision" class="decision-warning">确认后会由发布服务重新校验私有包并切换正式目录。如果激活失败，本次审核不会生效。</p><p v-if="error" class="form-error">{{ error }}</p></form>
      <template #footer><button class="button button--ghost" type="button" :disabled="busy" @click="selected = null">取消</button><button class="button" :class="decision ? 'button--positive' : 'button--danger'" type="submit" form="version-decision" :disabled="busy">{{ busy ? '处理中…' : decision ? '确认发布' : '确认拒绝' }}</button></template>
    </ModalDialog>
  </div>
</template>
