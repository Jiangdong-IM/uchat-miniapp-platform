<script setup>
import { computed, reactive, ref } from 'vue'
import { ArrowRight, Check, Code2, FileCheck2, LockKeyhole, PackageOpen, ShieldCheck } from '@lucide/vue'
import { api } from '../../api/client.js'

const emit = defineEmits(['authenticated', 'notice'])
const mode = ref('login')
const busy = ref(false)
const error = ref('')
const login = reactive({ username: '', password: '' })
const registration = reactive({
  username: '', password: '', developerName: '', contactEmail: '', organizationName: '', purpose: '', planDescription: '',
})

const isLogin = computed(() => mode.value === 'login')

async function submitLogin() {
  busy.value = true
  error.value = ''
  try {
    const result = await api.auth.login(login)
    emit('authenticated', result)
  } catch (requestError) {
    error.value = requestError.message
  } finally {
    busy.value = false
  }
}

async function submitRegistration() {
  busy.value = true
  error.value = ''
  try {
    await api.auth.register(registration)
    mode.value = 'login'
    login.username = registration.username
    emit('notice', '申请已提交。admin 审核通过后即可登录。')
  } catch (requestError) {
    error.value = requestError.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-story">
      <div class="brand-lockup brand-lockup--light">
        <div class="brand-mark" aria-hidden="true"><span></span><span></span></div>
        <div><strong>UChat</strong><small>MINI APP DESK</small></div>
      </div>
      <div class="auth-story__copy">
        <p class="eyebrow eyebrow--light">从代码包到可信发布</p>
        <h1>每一次上架，<br />都有清晰的审核轨迹。</h1>
        <p>集中管理小程序、版本、上架资料与真实用户反馈。安装包只有通过审核后，才会进入 UChat 的正式目录。</p>
      </div>
      <ol class="release-ledger" aria-label="发布流程">
        <li><span><Code2 :size="18" /></span><div><strong>准备程序包</strong><small>Vue 构建产物与 uchat-miniapp.json</small></div><b>01</b></li>
        <li><span><FileCheck2 :size="18" /></span><div><strong>提交版本审核</strong><small>统一校验清单、权限和完整性</small></div><b>02</b></li>
        <li><span><PackageOpen :size="18" /></span><div><strong>激活正式版本</strong><small>审核通过后同步到 UChat</small></div><b>03</b></li>
      </ol>
      <div class="auth-story__seal"><ShieldCheck :size="16" /> 私有包存储 · 审核后发布</div>
    </section>

    <section class="auth-form-panel">
      <div class="auth-form-card">
        <header>
          <p class="eyebrow">开发者通行证</p>
          <h2>{{ isLogin ? '登录管理平台' : '提交开发者申请' }}</h2>
          <p>{{ isLogin ? '使用已通过审核的账号继续。' : '请如实填写用途与开发计划，admin 将据此审核。' }}</p>
        </header>

        <div class="auth-mode-switch" role="tablist" aria-label="账号操作">
          <button type="button" :class="{ active: isLogin }" @click="mode = 'login'; error = ''">登录</button>
          <button type="button" :class="{ active: !isLogin }" @click="mode = 'register'; error = ''">申请账号</button>
        </div>

        <form v-if="isLogin" class="form-stack" @submit.prevent="submitLogin">
          <label class="field"><span>账号</span><input v-model.trim="login.username" autocomplete="username" required maxlength="64" placeholder="输入开发者账号" /></label>
          <label class="field"><span>密码</span><input v-model="login.password" type="password" autocomplete="current-password" required minlength="8" maxlength="72" placeholder="输入密码" /></label>
          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <button class="button button--primary button--wide" type="submit" :disabled="busy">
            <LockKeyhole :size="17" />{{ busy ? '正在验证…' : '进入工作台' }}<ArrowRight :size="17" />
          </button>
        </form>

        <form v-else class="form-stack form-stack--registration" @submit.prevent="submitRegistration">
          <div class="field-grid">
            <label class="field"><span>登录账号</span><input v-model.trim="registration.username" autocomplete="username" required minlength="4" maxlength="64" /></label>
            <label class="field"><span>登录密码</span><input v-model="registration.password" type="password" autocomplete="new-password" required minlength="8" maxlength="72" /></label>
            <label class="field"><span>开发者名称</span><input v-model.trim="registration.developerName" required maxlength="80" placeholder="上架时公开显示" /></label>
            <label class="field"><span>联系邮箱</span><input v-model.trim="registration.contactEmail" type="email" required maxlength="160" /></label>
          </div>
          <label class="field"><span>组织名称 <em>选填</em></span><input v-model.trim="registration.organizationName" maxlength="120" /></label>
          <label class="field"><span>申请用途</span><textarea v-model.trim="registration.purpose" required maxlength="500" rows="3" placeholder="为什么需要 UChat 小程序开发权限？"></textarea><small>{{ registration.purpose.length }}/500</small></label>
          <label class="field"><span>开发计划</span><textarea v-model.trim="registration.planDescription" required maxlength="1000" rows="4" placeholder="计划做什么、服务哪些用户、如何维护？"></textarea><small>{{ registration.planDescription.length }}/1000</small></label>
          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <button class="button button--primary button--wide" type="submit" :disabled="busy"><Check :size="17" />{{ busy ? '正在提交…' : '提交审核申请' }}</button>
        </form>
      </div>
    </section>
  </main>
</template>
