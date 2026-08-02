<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Edit3, Plus, RefreshCw, Search, Star, Trash2 } from '@lucide/vue'
import { api } from '../../api/client.js'
import EmptyState from '../../components/common/EmptyState.vue'
import ModalDialog from '../../components/common/ModalDialog.vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import StatusBadge from '../../components/common/StatusBadge.vue'
import { asList, formatDateTime } from '../../utils/format.js'

const emit = defineEmits(['notice'])
const query = reactive({ keyword: '', status: '', miniAppId: '', page: 1, pageSize: 20 })
const result = ref({ items: [], page: 1, pageSize: 20, total: 0 })
const loading = ref(true)
const pageError = ref('')
const dialog = ref(null)
const dialogLoading = ref(false)
const dialogBusy = ref(false)
const dialogError = ref('')
const form = reactive({ miniAppId: '', uchatUserId: '', userDisplayName: '', content: '', featured: false, status: 'VISIBLE' })

async function load() {
  loading.value = true
  pageError.value = ''
  try {
    const data = await api.admin.comments(query)
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

function openCreate() {
  dialogError.value = ''
  Object.assign(form, { miniAppId: '', uchatUserId: '', userDisplayName: '', content: '', featured: false, status: 'VISIBLE' })
  dialog.value = { type: 'create' }
}

async function openEdit(item) {
  dialogError.value = ''
  Object.assign(form, {
    miniAppId: item.miniAppId,
    uchatUserId: item.uchatUserId,
    userDisplayName: item.userDisplayName || '',
    content: item.content || '',
    featured: Boolean(item.featured),
    status: item.status || 'VISIBLE',
  })
  dialog.value = { type: 'edit', comment: item }
  dialogLoading.value = true
  try {
    const comment = await api.admin.comment(item.id)
    dialog.value = { type: 'edit', comment }
    Object.assign(form, {
      miniAppId: comment.miniAppId,
      uchatUserId: comment.uchatUserId,
      userDisplayName: comment.userDisplayName || '',
      content: comment.content || '',
      featured: Boolean(comment.featured),
      status: comment.status || 'VISIBLE',
    })
  } catch (error) {
    dialogError.value = error.message
  } finally {
    dialogLoading.value = false
  }
}

function openDelete(comment) {
  dialogError.value = ''
  dialog.value = { type: 'delete', comment }
}

function closeDialog() {
  if (!dialogBusy.value) dialog.value = null
}

async function saveComment() {
  const type = dialog.value.type
  dialogBusy.value = true
  dialogError.value = ''
  try {
    if (type === 'create') {
      await api.admin.createComment({
        miniAppId: form.miniAppId,
        uchatUserId: form.uchatUserId,
        userDisplayName: form.userDisplayName,
        content: form.content,
        featured: form.featured,
        status: form.status,
      })
    } else {
      await api.admin.updateComment(dialog.value.comment.id, {
        userDisplayName: form.userDisplayName,
        content: form.content,
        featured: form.featured,
        status: form.status,
      })
    }
    dialog.value = null
    emit('notice', type === 'create' ? '评论已创建。' : '评论已更新。')
    await load()
  } catch (error) {
    dialogError.value = error.message
  } finally {
    dialogBusy.value = false
  }
}

async function deleteComment() {
  dialogBusy.value = true
  dialogError.value = ''
  try {
    await api.admin.deleteComment(dialog.value.comment.id)
    dialog.value = null
    emit('notice', '评论已删除。')
    if (result.value.items.length === 1 && query.page > 1) query.page -= 1
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
      <div><p class="eyebrow">ADMIN / COMMENTS</p><h1>评论管理</h1><p>查询、创建和维护 UChat 用户评论；隐藏与精选状态会同步影响公开展示。</p></div>
      <div class="heading-actions"><button class="button button--ghost" type="button" :disabled="loading" @click="load"><RefreshCw :size="16" /> 刷新</button><button class="button button--primary" type="button" @click="openCreate"><Plus :size="16" /> 新建评论</button></div>
    </header>

    <form class="admin-filter-bar admin-filter-bar--comments" role="search" @submit.prevent="search">
      <label class="admin-search"><Search :size="17" aria-hidden="true" /><span class="sr-only">搜索评论</span><input v-model.trim="query.keyword" type="search" maxlength="120" placeholder="搜索评论内容、用户或小程序" /></label>
      <label class="filter-select"><span>状态</span><select v-model="query.status" @change="search"><option value="">全部状态</option><option value="VISIBLE">公开可见</option><option value="HIDDEN">已隐藏</option></select></label>
      <label class="compact-field"><span>小程序 ID</span><input v-model="query.miniAppId" type="number" min="1" inputmode="numeric" placeholder="全部" /></label>
      <button class="button button--secondary" type="submit" :disabled="loading">搜索</button>
    </form>

    <p v-if="pageError" class="form-error page-error" role="alert">{{ pageError }}</p>
    <div v-if="loading" class="admin-record-list"><div v-for="item in 5" :key="item" class="skeleton-row"></div></div>
    <section v-else-if="result.items.length" class="admin-record-list" aria-label="评论列表">
      <article v-for="comment in result.items" :key="comment.id" class="admin-comment-record" :class="{ 'admin-comment-record--featured': comment.featured }">
        <div class="review-row__avatar">{{ (comment.userDisplayName || 'U').slice(0, 1) }}</div>
        <div class="admin-comment-record__content">
          <div class="admin-comment-record__meta"><strong>{{ comment.userDisplayName }}</strong><code>用户 {{ comment.uchatUserId }}</code><StatusBadge :status="comment.status" /><span v-if="comment.featured" class="featured-mark"><Star :size="13" fill="currentColor" /> 精选</span></div>
          <p>{{ comment.content }}</p>
          <small>{{ comment.appName }} · {{ comment.appId }} · {{ formatDateTime(comment.updatedAt || comment.createdAt) }}</small>
        </div>
        <div class="admin-record-actions">
          <button class="button button--compact button--ghost" type="button" @click="openEdit(comment)"><Edit3 :size="15" /> 编辑</button>
          <button class="button button--compact button--danger-quiet" type="button" @click="openDelete(comment)"><Trash2 :size="15" /> 删除</button>
        </div>
      </article>
      <PaginationControls :page="result.page" :page-size="result.pageSize" :total="result.total" :loading="loading" @change="changePage" />
    </section>
    <EmptyState v-else title="没有匹配的评论" description="请调整关键词、状态或小程序 ID 后再试。" />

    <ModalDialog v-if="dialog && dialog.type !== 'delete'" :open="true" :title="dialog.type === 'create' ? '新建评论' : '编辑评论'" :description="dialog.type === 'edit' ? `${dialog.comment.appName} · 评论 ${dialog.comment.id}` : '以管理账号录入一条 UChat 评论'" :busy="dialogBusy" wide @close="closeDialog">
      <div v-if="dialogLoading" class="skeleton-row"></div>
      <form v-else id="admin-comment-form" class="form-stack" @submit.prevent="saveComment">
        <div v-if="dialog.type === 'create'" class="field-grid">
          <label class="field"><span>小程序 ID</span><input v-model.number="form.miniAppId" type="number" min="1" inputmode="numeric" required /></label>
          <label class="field"><span>UChat 用户 ID</span><input v-model.number="form.uchatUserId" type="number" min="1" inputmode="numeric" required /></label>
        </div>
        <div v-else class="record-lock"><span>归属信息不可修改</span><strong>小程序 {{ form.miniAppId }} · 用户 {{ form.uchatUserId }}</strong></div>
        <label class="field"><span>用户显示名称</span><input v-model.trim="form.userDisplayName" required maxlength="120" /><small>{{ form.userDisplayName.length }}/120</small></label>
        <label class="field"><span>评论内容</span><textarea v-model.trim="form.content" required maxlength="500" rows="6"></textarea><small>{{ form.content.length }}/500</small></label>
        <div class="field-grid">
          <label class="field"><span>展示状态</span><select v-model="form.status" required><option value="VISIBLE">公开可见</option><option value="HIDDEN">隐藏</option></select></label>
          <label class="check-field"><input v-model="form.featured" type="checkbox" /><span><strong>设为精选评论</strong><small>精选评论会优先展示在小程序详情中。</small></span></label>
        </div>
        <p v-if="dialogError" class="form-error" role="alert">{{ dialogError }}</p>
      </form>
      <template #footer><button class="button button--ghost" type="button" :disabled="dialogBusy" @click="closeDialog">取消</button><button class="button button--primary" type="submit" form="admin-comment-form" :disabled="dialogBusy || dialogLoading">{{ dialogBusy ? '保存中…' : dialog.type === 'create' ? '创建评论' : '保存修改' }}</button></template>
    </ModalDialog>

    <ModalDialog v-else-if="dialog" :open="true" title="确认删除评论" :description="`${dialog.comment.appName} · 评论 ${dialog.comment.id}`" :busy="dialogBusy" @close="closeDialog">
      <div class="impact-confirmation impact-confirmation--danger"><strong>本次操作永久删除 1 条评论</strong><p>删除后无法恢复，评论数量与精选展示会随之更新。如只需停止公开展示，请改用“隐藏”状态。</p></div>
      <p v-if="dialogError" class="form-error" role="alert">{{ dialogError }}</p>
      <template #footer><button class="button button--ghost" type="button" :disabled="dialogBusy" @click="closeDialog">取消</button><button class="button button--danger" type="button" :disabled="dialogBusy" @click="deleteComment">{{ dialogBusy ? '删除中…' : '确认永久删除' }}</button></template>
    </ModalDialog>
  </div>
</template>
