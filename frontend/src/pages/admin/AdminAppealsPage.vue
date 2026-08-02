<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Check, RefreshCw, Scale, X } from '@lucide/vue'
import { api } from '../../api/client.js'
import EmptyState from '../../components/common/EmptyState.vue'
import ModalDialog from '../../components/common/ModalDialog.vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import StatusBadge from '../../components/common/StatusBadge.vue'
import { asList, formatDateTime } from '../../utils/format.js'

const props = defineProps({ initialDeveloperId: { type: Number, default: null } })
const emit = defineEmits(['notice'])
const query = reactive({ status: '', developerAccountId: '', page: 1, pageSize: 20 })
const result = ref({ items: [], page: 1, pageSize: 20, total: 0 })
const loading = ref(true)
const pageError = ref('')
const dialog = ref(null)
const dialogBusy = ref(false)
const dialogError = ref('')
const reviewNote = ref('')

async function load() {
  loading.value = true
  pageError.value = ''
  try {
    const data = await api.admin.appeals(query)
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

function openDecision(appeal, decision) {
  reviewNote.value = ''
  dialogError.value = ''
  dialog.value = { appeal, decision }
}

function closeDialog() {
  if (!dialogBusy.value) dialog.value = null
}

async function submitDecision() {
  const { appeal, decision } = dialog.value
  dialogBusy.value = true
  dialogError.value = ''
  try {
    await api.admin.decideAppeal(appeal.id, { decision, reviewNote: reviewNote.value })
    dialog.value = null
    emit('notice', decision === 'APPROVED' ? '申诉已通过，开发者账号已解禁；小程序保持下架。' : '申诉已驳回。')
    await load()
  } catch (error) {
    dialogError.value = error.message
  } finally {
    dialogBusy.value = false
  }
}

onMounted(() => {
  if (props.initialDeveloperId) query.developerAccountId = props.initialDeveloperId
  load()
})
</script>

<template>
  <div class="page-frame">
    <header class="page-heading">
      <div><p class="eyebrow">ADMIN / APPEALS</p><h1>开发者申诉</h1><p>复核封禁争议并留下处理依据；通过申诉只解除账号限制，不恢复任何小程序。</p></div>
      <button class="button button--ghost" type="button" :disabled="loading" @click="load"><RefreshCw :size="16" /> 刷新</button>
    </header>

    <form class="admin-filter-bar" role="search" @submit.prevent="search">
      <label class="compact-field compact-field--grow"><span>开发者账号 ID</span><input v-model="query.developerAccountId" type="number" min="1" inputmode="numeric" placeholder="全部开发者" /></label>
      <label class="filter-select"><span>申诉状态</span><select v-model="query.status" @change="search"><option value="">全部状态</option><option value="PENDING">待处理</option><option value="APPROVED">已通过</option><option value="REJECTED">已驳回</option></select></label>
      <button class="button button--secondary" type="submit" :disabled="loading">筛选</button>
    </form>

    <p v-if="pageError" class="form-error page-error" role="alert">{{ pageError }}</p>
    <div v-if="loading" class="admin-record-list"><div v-for="item in 5" :key="item" class="skeleton-row"></div></div>
    <section v-else-if="result.items.length" class="admin-record-list" aria-label="申诉列表">
      <article v-for="appeal in result.items" :key="appeal.id" class="appeal-record">
        <div class="appeal-record__stamp"><Scale :size="20" /><code>#{{ appeal.id }}</code></div>
        <div class="appeal-record__body">
          <div><h2>{{ appeal.developerName }}</h2><StatusBadge :status="appeal.status" /></div>
          <small>{{ appeal.username }} · 开发者账号 {{ appeal.developerAccountId }} · {{ formatDateTime(appeal.createdAt) }}</small>
          <blockquote>{{ appeal.content }}</blockquote>
          <p v-if="appeal.reviewNote"><strong>处理备注</strong>{{ appeal.reviewNote }}</p>
        </div>
        <div v-if="appeal.status === 'PENDING'" class="admin-record-actions">
          <button class="button button--compact button--danger-quiet" type="button" @click="openDecision(appeal, 'REJECTED')"><X :size="15" /> 驳回</button>
          <button class="button button--compact button--positive" type="button" @click="openDecision(appeal, 'APPROVED')"><Check :size="15" /> 通过并解禁</button>
        </div>
        <small v-else class="appeal-record__reviewed">处理于 {{ formatDateTime(appeal.reviewedAt) }}</small>
      </article>
      <PaginationControls :page="result.page" :page-size="result.pageSize" :total="result.total" :loading="loading" @change="changePage" />
    </section>
    <EmptyState v-else title="没有匹配的申诉" description="被封禁开发者提交申诉后会显示在这里。" />

    <ModalDialog v-if="dialog" :open="true" :title="dialog.decision === 'APPROVED' ? '通过申诉并解禁' : '驳回开发者申诉'" :description="`${dialog.appeal.developerName} · 申诉 ${dialog.appeal.id}`" :busy="dialogBusy" @close="closeDialog">
      <form id="appeal-decision-form" class="form-stack" @submit.prevent="submitDecision">
        <div class="impact-confirmation" :class="{ 'impact-confirmation--danger': dialog.decision === 'REJECTED' }">
          <strong v-if="dialog.decision === 'APPROVED'">解除 1 个开发者账号限制，自动恢复 0 个小程序</strong>
          <strong v-else>驳回 1 条待处理申诉</strong>
          <p v-if="dialog.decision === 'APPROVED'">开发者可以重新进入工作台，但所有历史小程序仍保持下架，需要之后逐个重新上架。</p>
          <p v-else>账号继续保持封禁，开发者仍可登录查看结果并再次申诉。</p>
        </div>
        <label class="field"><span>处理备注（选填）</span><textarea v-model.trim="reviewNote" maxlength="500" rows="5" placeholder="记录复核依据，便于后续追踪"></textarea><small>{{ reviewNote.length }}/500</small></label>
        <p v-if="dialogError" class="form-error" role="alert">{{ dialogError }}</p>
      </form>
      <template #footer><button class="button button--ghost" type="button" :disabled="dialogBusy" @click="closeDialog">取消</button><button class="button" :class="dialog.decision === 'APPROVED' ? 'button--positive' : 'button--danger'" type="submit" form="appeal-decision-form" :disabled="dialogBusy">{{ dialogBusy ? '处理中…' : dialog.decision === 'APPROVED' ? '确认通过并解禁' : '确认驳回' }}</button></template>
    </ModalDialog>
  </div>
</template>
