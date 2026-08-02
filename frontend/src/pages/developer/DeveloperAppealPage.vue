<script setup>
import { computed, onMounted, ref } from 'vue'
import { RefreshCw, Scale, Send, ShieldAlert } from '@lucide/vue'
import { api } from '../../api/client.js'
import EmptyState from '../../components/common/EmptyState.vue'
import StatusBadge from '../../components/common/StatusBadge.vue'
import { asList, formatDateTime } from '../../utils/format.js'

const props = defineProps({ account: { type: Object, required: true } })
const emit = defineEmits(['notice', 'refresh-account'])
const appeals = ref([])
const content = ref('')
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const hasPendingAppeal = computed(() => appeals.value.some((appeal) => appeal.status === 'PENDING'))

async function load() {
  loading.value = true
  error.value = ''
  try {
    appeals.value = asList(await api.developer.appeals())
  } catch (requestError) {
    error.value = requestError.message
  } finally {
    loading.value = false
  }
}

async function submitAppeal() {
  busy.value = true
  error.value = ''
  try {
    await api.developer.createAppeal({ content: content.value })
    content.value = ''
    emit('notice', '申诉已提交，管理员处理后会更新状态。')
    await load()
  } catch (requestError) {
    error.value = requestError.message
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-frame appeal-page">
    <header class="page-heading">
      <div><p class="eyebrow">ACCOUNT / APPEAL</p><h1>账号申诉</h1><p>你的账号可以正常登录，但封禁解除前不能进入小程序工作台。</p></div>
      <button class="button button--ghost" type="button" @click="emit('refresh-account')"><RefreshCw :size="16" /> 检查账号状态</button>
    </header>

    <section class="ban-dossier" aria-labelledby="ban-dossier-title">
      <div class="ban-dossier__icon"><ShieldAlert :size="26" /></div>
      <div><p class="eyebrow">当前限制</p><h2 id="ban-dossier-title">开发者账号已封禁</h2><p>{{ account.banReason || '管理员未提供封禁原因，请在申诉中说明需要复核的情况。' }}</p><small>封禁于 {{ formatDateTime(account.bannedAt) }}</small></div>
      <StatusBadge :status="account.status" />
    </section>

    <div class="appeal-layout">
      <section class="work-panel">
        <div class="section-heading section-heading--compact"><div><p class="eyebrow">提交说明</p><h2>发起申诉</h2></div><Scale :size="20" /></div>
        <form class="form-stack" @submit.prevent="submitAppeal">
          <div v-if="hasPendingAppeal" class="decision-warning">已有一条申诉等待处理，请等待管理员决定后再提交新的申诉。</div>
          <label class="field"><span>申诉内容</span><textarea v-model.trim="content" required maxlength="1000" rows="9" :disabled="hasPendingAppeal" placeholder="说明需要复核的事实、整改情况以及后续计划"></textarea><small>{{ content.length }}/1000</small></label>
          <p class="field-help">申诉通过只会解除账号限制。封禁时下架的小程序不会自动恢复；解禁后可由管理员重新上架仍满足条件的已审核版本，资料或包内容有变化时需先提交匹配版本审核。</p>
          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <button class="button button--primary" type="submit" :disabled="busy || hasPendingAppeal"><Send :size="16" />{{ busy ? '提交中…' : '提交申诉' }}</button>
        </form>
      </section>

      <section class="work-panel">
        <div class="section-heading section-heading--compact"><div><p class="eyebrow">处理轨迹</p><h2>申诉记录</h2></div><span class="section-index">{{ appeals.length }}</span></div>
        <div v-if="loading" class="curation-loading"><div v-for="item in 3" :key="item" class="skeleton-row"></div></div>
        <ol v-else-if="appeals.length" class="appeal-history">
          <li v-for="appeal in appeals" :key="appeal.id">
            <div><StatusBadge :status="appeal.status" /><time>{{ formatDateTime(appeal.createdAt) }}</time></div>
            <p>{{ appeal.content }}</p>
            <blockquote v-if="appeal.reviewNote"><strong>管理员回复</strong>{{ appeal.reviewNote }}</blockquote>
            <small v-if="appeal.reviewedAt">处理于 {{ formatDateTime(appeal.reviewedAt) }}</small>
          </li>
        </ol>
        <EmptyState v-else title="还没有申诉记录" description="提交后可在这里查看处理状态和管理员回复。" />
      </section>
    </div>
  </div>
</template>
