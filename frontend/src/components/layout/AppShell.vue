<script setup>
import { computed } from 'vue'
import { AppWindow, ClipboardCheck, LayoutDashboard, LogOut, MessageSquareText, PackageCheck, Scale, ShieldAlert, ShieldCheck, Users } from '@lucide/vue'

const props = defineProps({
  account: { type: Object, required: true },
  currentView: { type: String, required: true },
})
const emit = defineEmits(['navigate', 'logout'])

const isAdmin = computed(() => props.account.role === 'ADMIN')
const isBannedDeveloper = computed(() => props.account.role === 'DEVELOPER' && props.account.status === 'BANNED')
const navigation = computed(() => {
  if (isBannedDeveloper.value) return [{ id: 'appeal', label: '账号申诉', icon: Scale }]
  return isAdmin.value ? [
      { id: 'admin-registrations', label: '注册审核', icon: ClipboardCheck },
      { id: 'admin-versions', label: '版本审核', icon: PackageCheck },
      { id: 'admin-apps', label: '小程序', icon: AppWindow },
      { id: 'admin-reviews', label: '评论', icon: MessageSquareText },
      { id: 'admin-developers', label: '开发者', icon: Users },
      { id: 'admin-appeals', label: '申诉', icon: Scale },
    ]
  : [
      { id: 'dashboard', label: '概览', icon: LayoutDashboard },
      { id: 'apps', label: '我的小程序', icon: AppWindow },
      { id: 'reviews', label: '用户评价', icon: MessageSquareText },
    ]
})
</script>

<template>
  <div class="app-shell">
    <aside class="side-rail">
      <div class="brand-lockup">
        <div class="brand-mark" aria-hidden="true"><span></span><span></span></div>
        <div><strong>UChat</strong><small>MINI APP DESK</small></div>
      </div>

      <div class="side-rail__context">
        <ShieldAlert v-if="isBannedDeveloper" :size="17" />
        <ShieldCheck v-else :size="17" />
        <span>{{ isAdmin ? '审核控制台' : isBannedDeveloper ? '账号受限' : '开发者工作台' }}</span>
      </div>

      <nav class="side-nav" aria-label="主导航">
        <button
          v-for="item in navigation"
          :key="item.id"
          type="button"
          :class="{ active: currentView === item.id }"
          @click="emit('navigate', item.id)"
        >
          <component :is="item.icon" :size="18" stroke-width="1.8" />
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <div class="side-rail__account">
        <div class="account-avatar">{{ (account.developerName || account.username || 'U').slice(0, 1).toUpperCase() }}</div>
        <div class="account-copy"><strong>{{ account.developerName || account.username }}</strong><small>{{ account.contactEmail || '平台管理员' }}</small></div>
        <button class="icon-button icon-button--dark" type="button" aria-label="退出登录" @click="emit('logout')"><LogOut :size="18" /></button>
      </div>
    </aside>

    <main class="workspace"><slot /></main>
  </div>
</template>
