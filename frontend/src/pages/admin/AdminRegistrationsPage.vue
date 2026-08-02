<script setup>
import { onMounted, ref } from 'vue'
import { Check, Mail, RefreshCw, X } from '@lucide/vue'
import { api } from '../../api/client.js'
import EmptyState from '../../components/common/EmptyState.vue'
import ModalDialog from '../../components/common/ModalDialog.vue'
import StatusBadge from '../../components/common/StatusBadge.vue'
import { asList, formatDateTime } from '../../utils/format.js'

const emit = defineEmits(['notice'])
const registrations = ref([])
const loading = ref(true)
const selected = ref(null)
const decision = ref(null)
const reason = ref('')
const busy = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try { registrations.value = asList(await api.admin.registrations('PENDING')) }
  catch (requestError) { error.value = requestError.message }
  finally { loading.value = false }
}

function openDecision(item, approved) {
  selected.value = item
  decision.value = approved
  reason.value = ''
}

async function submitDecision() {
  busy.value = true
  error.value = ''
  try {
    await api.admin.decideRegistration(selected.value.id, { approved: decision.value, reason: reason.value })
    selected.value = null
    emit('notice', decision.value ? '开发者账号已批准。' : '申请已拒绝。')
    await load()
  } catch (requestError) { error.value = requestError.message }
  finally { busy.value = false }
}

onMounted(load)
</script>

<template>
  <div class="page-frame">
    <header class="page-heading"><div><p class="eyebrow">ADMIN / ACCESS</p><h1>开发者注册审核</h1><p>核对申请用途、开发计划与联系方式后，再开放发布权限。</p></div><button class="button button--ghost" type="button" :disabled="loading" @click="load"><RefreshCw :size="16" /> 刷新</button></header>
    <p v-if="error" class="form-error page-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="audit-list"><div v-for="item in 4" :key="item" class="skeleton-row"></div></div>
    <div v-else-if="registrations.length" class="audit-list">
      <article v-for="item in registrations" :key="item.id" class="audit-record">
        <div class="audit-record__number">{{ String(item.id).padStart(4, '0') }}</div>
        <div class="audit-record__identity"><p class="eyebrow">开发者申请</p><h2>{{ item.developerName }}</h2><span>{{ item.username }} · {{ item.organizationName || '个人开发者' }}</span><a :href="`mailto:${item.contactEmail}`"><Mail :size="14" /> {{ item.contactEmail }}</a></div>
        <div class="audit-record__statement"><span>申请用途</span><p>{{ item.purpose }}</p><span>开发计划</span><p>{{ item.planDescription }}</p></div>
        <div class="audit-record__meta"><StatusBadge :status="item.status" /><small>提交于<br />{{ formatDateTime(item.createdAt) }}</small><div><button class="button button--compact button--danger-quiet" type="button" @click="openDecision(item, false)"><X :size="15" /> 拒绝</button><button class="button button--compact button--positive" type="button" @click="openDecision(item, true)"><Check :size="15" /> 通过</button></div></div>
      </article>
    </div>
    <EmptyState v-else title="没有待审核的注册申请" description="新的开发者申请提交后会出现在这里。" />

    <ModalDialog :open="Boolean(selected)" :title="decision ? '批准开发者账号' : '拒绝开发者申请'" :description="selected ? `${selected.developerName}（${selected.username}）` : ''" :busy="busy" @close="selected = null">
      <form id="registration-decision" class="form-stack" @submit.prevent="submitDecision"><label class="field"><span>审核备注 {{ decision ? '（选填）' : '' }}</span><textarea v-model.trim="reason" :required="!decision" maxlength="500" rows="5" :placeholder="decision ? '可记录审核依据' : '请填写拒绝原因，便于开发者调整后重新申请'"></textarea><small>{{ reason.length }}/500</small></label><p v-if="error" class="form-error">{{ error }}</p></form>
      <template #footer><button class="button button--ghost" type="button" :disabled="busy" @click="selected = null">取消</button><button class="button" :class="decision ? 'button--positive' : 'button--danger'" type="submit" form="registration-decision" :disabled="busy">{{ busy ? '提交中…' : '确认决定' }}</button></template>
    </ModalDialog>
  </div>
</template>
