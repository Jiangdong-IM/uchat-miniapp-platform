<script setup>
import { computed } from 'vue'
import { AppWindow, ClipboardCheck, LayoutDashboard, LogOut, MessageSquareText, PackageCheck, ShieldCheck } from '@lucide/vue'

const props = defineProps({
  account: { type: Object, required: true },
  currentView: { type: String, required: true },
})
const emit = defineEmits(['navigate', 'logout'])

const isAdmin = computed(() => props.account.role === 'ADMIN')
const navigation = computed(() => isAdmin.value
  ? [
      { id: 'admin-registrations', label: '注册审核', icon: ClipboardCheck },
      { id: 'admin-versions', label: '版本审核', icon: PackageCheck },
      { id: 'admin-reviews', label: '评价精选', icon: MessageSquareText },
    ]
  : [
      { id: 'dashboard', label: '概览', icon: LayoutDashboard },
      { id: 'apps', label: '我的小程序', icon: AppWindow },
      { id: 'reviews', label: '用户评价', icon: MessageSquareText },
    ])
</script>

<template>
  <div class="app-shell">
    <aside class="side-rail">
      <div class="brand-lockup">
        <div class="brand-mark" aria-hidden="true"><span></span><span></span></div>
        <div><strong>UChat</strong><small>MINI APP DESK</small></div>
      </div>

      <div class="side-rail__context">
        <ShieldCheck :size="17" />
        <span>{{ isAdmin ? '审核控制台' : '开发者工作台' }}</span>
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
