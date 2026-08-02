<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Ban, Mail, RefreshCw, Search, ShieldCheck } from '@lucide/vue'
import { api } from '../../api/client.js'
import EmptyState from '../../components/common/EmptyState.vue'
import ModalDialog from '../../components/common/ModalDialog.vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import StatusBadge from '../../components/common/StatusBadge.vue'
import { asList, formatDateTime } from '../../utils/format.js'

const emit = defineEmits(['notice', 'open-appeals'])
const query = reactive({ keyword: '', status: '', page: 1, pageSize: 20 })
const result = ref({ items: [], page: 1, pageSize: 20, total: 0 })
const loading = ref(true)
const pageError = ref('')
const dialog = ref(null)
const dialogBusy = ref(false)
const dialogError = ref('')
const note = ref('')

async function load() {
  loading.value = true
  pageError.value = ''
  try {
    const data = await api.admin.developers(query)
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

function openAction(type, developer) {
  note.value = ''
  dialogError.value = ''
  dialog.value = { type, developer }
}

function closeDialog() {
  if (!dialogBusy.value) dialog.value = null
}

async function submitAction() {
  const { type, developer } = dialog.value
  dialogBusy.value = true
  dialogError.value = ''
  try {
    const response = type === 'ban'
      ? await api.admin.banDeveloper(developer.id, { reason: note.value })
      : await api.admin.unbanDeveloper(developer.id, { note: note.value })
    dialog.value = null
    if (type === 'ban') emit('notice', `开发者已封禁，本次下架 ${response.delistedAppCount} 个小程序。`)
    else emit('notice', '开发者已解禁；历史小程序仍保持下架。')
    await load()
  } catch (error) {
    dialogError.value = error.message
  } finally {
    dialogBusy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-frame">
    <header class="page-heading">
      <div><p class="eyebrow">ADMIN / DEVELOPERS</p><h1>开发者管理</h1><p>查看账号与应用规模，封禁违规开发者或在复核后解除限制。</p></div>
      <button class="button button--ghost" type="button" :disabled="loading" @click="load"><RefreshCw :size="16" /> 刷新</button>
    </header>

    <div class="governance-warning"><Ban :size="19" /><div><strong>封禁时会立即下架该开发者全部已上架小程序</strong><span>账号仍可登录申诉，但不能访问开发者工作台。解禁不会自动恢复历史小程序。</span></div></div>

    <form class="admin-filter-bar" role="search" @submit.prevent="search">
      <label class="admin-search"><Search :size="17" aria-hidden="true" /><span class="sr-only">搜索开发者</span><input v-model.trim="query.keyword" type="search" maxlength="120" placeholder="搜索开发者名称、账号或邮箱" /></label>
      <label class="filter-select"><span>账号状态</span><select v-model="query.status" @change="search"><option value="">全部状态</option><option value="PENDING">待审核</option><option value="APPROVED">已通过</option><option value="REJECTED">已拒绝</option><option value="BANNED">已封禁</option></select></label>
      <button class="button button--secondary" type="submit" :disabled="loading">搜索</button>
    </form>

    <p v-if="pageError" class="form-error page-error" role="alert">{{ pageError }}</p>
    <div v-if="loading" class="admin-record-list"><div v-for="item in 5" :key="item" class="skeleton-row"></div></div>
    <section v-else-if="result.items.length" class="admin-record-list" aria-label="开发者列表">
      <article v-for="developer in result.items" :key="developer.id" class="admin-developer-record" :class="{ 'admin-developer-record--banned': developer.status === 'BANNED' }">
        <div class="account-avatar">{{ (developer.developerName || developer.username || 'D').slice(0, 1).toUpperCase() }}</div>
        <div class="admin-developer-record__identity">
          <div><h2>{{ developer.developerName }}</h2><StatusBadge :status="developer.status" /></div>
          <p>{{ developer.username }} · {{ developer.organizationName || '个人开发者' }}</p>
          <a :href="`mailto:${developer.contactEmail}`"><Mail :size="14" /> {{ developer.contactEmail }}</a>
          <small v-if="developer.status === 'BANNED'">封禁于 {{ formatDateTime(developer.bannedAt) }} · {{ developer.banReason }}</small>
        </div>
        <dl class="admin-developer-record__metrics">
          <div><dt>全部小程序</dt><dd>{{ developer.appCount }}</dd></div>
          <div><dt>当前已上架</dt><dd>{{ developer.publishedAppCount }}</dd></div>
          <div><dt>注册时间</dt><dd>{{ formatDateTime(developer.createdAt) }}</dd></div>
        </dl>
        <div class="admin-record-actions">
          <button v-if="developer.status === 'BANNED'" class="button button--compact button--ghost" type="button" @click="emit('open-appeals', developer.id)">查看申诉</button>
          <button v-if="developer.status === 'BANNED'" class="button button--compact button--positive" type="button" @click="openAction('unban', developer)"><ShieldCheck :size="15" /> 解禁</button>
          <button v-else-if="developer.status === 'APPROVED'" class="button button--compact button--danger-quiet" type="button" @click="openAction('ban', developer)"><Ban :size="15" /> 封禁</button>
        </div>
      </article>
      <PaginationControls :page="result.page" :page-size="result.pageSize" :total="result.total" :loading="loading" @change="changePage" />
    </section>
    <EmptyState v-else title="没有匹配的开发者" description="请调整关键词或账号状态后再试。" />

    <ModalDialog v-if="dialog" :open="true" :title="dialog.type === 'ban' ? '确认封禁开发者' : '确认解除封禁'" :description="`${dialog.developer.developerName} · ${dialog.developer.username}`" :busy="dialogBusy" @close="closeDialog">
      <form id="developer-status-form" class="form-stack" @submit.prevent="submitAction">
        <div class="impact-confirmation" :class="{ 'impact-confirmation--danger': dialog.type === 'ban' }">
          <strong v-if="dialog.type === 'ban'">将立即下架 {{ dialog.developer.publishedAppCount }} 个已上架小程序</strong>
          <strong v-else>解除 1 个开发者账号的访问限制</strong>
          <p v-if="dialog.type === 'ban'">该开发者共有 {{ dialog.developer.appCount }} 个小程序。封禁后仍可登录并提交申诉，但不能进入开发者工作台。</p>
          <p v-else>解禁后可以重新进入工作台；此前下架的 {{ dialog.developer.appCount }} 个小程序不会自动上架。</p>
        </div>
        <label class="field"><span>{{ dialog.type === 'ban' ? '封禁原因' : '解禁备注（选填）' }}</span><textarea v-model.trim="note" :required="dialog.type === 'ban'" maxlength="500" rows="5" :placeholder="dialog.type === 'ban' ? '说明违规事实，开发者申诉时可据此整改' : '记录复核依据或关联申诉' "></textarea><small>{{ note.length }}/500</small></label>
        <p v-if="dialogError" class="form-error" role="alert">{{ dialogError }}</p>
      </form>
      <template #footer><button class="button button--ghost" type="button" :disabled="dialogBusy" @click="closeDialog">取消</button><button class="button" :class="dialog.type === 'ban' ? 'button--danger' : 'button--positive'" type="submit" form="developer-status-form" :disabled="dialogBusy">{{ dialogBusy ? '处理中…' : dialog.type === 'ban' ? '确认封禁并下架' : '确认解禁' }}</button></template>
    </ModalDialog>
  </div>
</template>
