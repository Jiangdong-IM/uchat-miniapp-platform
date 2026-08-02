<script setup>
import { reactive, ref } from 'vue'
import { Plus } from '@lucide/vue'
import { api } from '../../api/client.js'
import AppDossierCard from '../../components/apps/AppDossierCard.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import ModalDialog from '../../components/common/ModalDialog.vue'

defineProps({ apps: { type: Array, default: () => [] }, loading: Boolean })
const emit = defineEmits(['created', 'open-app', 'notice'])
const showCreate = ref(false)
const busy = ref(false)
const error = ref('')
const form = reactive({ appId: '', name: '', description: '' })

async function createApp() {
  busy.value = true
  error.value = ''
  try {
    const app = await api.developer.createApp(form)
    showCreate.value = false
    Object.assign(form, { appId: '', name: '', description: '' })
    emit('created', app)
    emit('notice', '小程序卷宗已创建，请继续补齐图标、封面和版本。')
  } catch (requestError) {
    error.value = requestError.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="page-frame">
    <header class="page-heading">
      <div><p class="eyebrow">应用卷宗</p><h1>我的小程序</h1><p>每个开发者账号最多创建 10 个，小程序 appId 创建后不可修改。</p></div>
      <button class="button button--primary" type="button" :disabled="apps.length >= 10" @click="showCreate = true"><Plus :size="17" /> 新建小程序</button>
    </header>

    <div v-if="loading" class="skeleton-grid"><div v-for="item in 6" :key="item" class="skeleton-card"></div></div>
    <div v-else-if="apps.length" class="dossier-grid dossier-grid--full">
      <AppDossierCard v-for="app in apps" :key="app.id" :app="app" @open="emit('open-app', $event)" />
    </div>
    <EmptyState v-else title="这里还没有应用卷宗" description="先创建 appId 和基础资料，再上传上架素材与版本包。">
      <button class="button button--primary" type="button" @click="showCreate = true"><Plus :size="17" /> 新建小程序</button>
    </EmptyState>

    <ModalDialog :open="showCreate" title="创建小程序卷宗" description="appId 将写入程序包清单，创建后不可修改。" :busy="busy" @close="showCreate = false">
      <form id="create-app-form" class="form-stack" @submit.prevent="createApp">
        <label class="field"><span>App ID</span><input v-model.trim="form.appId" required maxlength="120" pattern="[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+" placeholder="com.example.miniapp" /><small>使用小写反向域名格式，例如 com.example.demo</small></label>
        <label class="field"><span>小程序名称</span><input v-model.trim="form.name" required maxlength="40" /><small>{{ form.name.length }}/40</small></label>
        <label class="field"><span>简介</span><textarea v-model.trim="form.description" required maxlength="120" rows="4"></textarea><small>{{ form.description.length }}/120</small></label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      </form>
      <template #footer><button class="button button--ghost" type="button" :disabled="busy" @click="showCreate = false">取消</button><button class="button button--primary" type="submit" form="create-app-form" :disabled="busy">{{ busy ? '创建中…' : '创建卷宗' }}</button></template>
    </ModalDialog>
  </div>
</template>
