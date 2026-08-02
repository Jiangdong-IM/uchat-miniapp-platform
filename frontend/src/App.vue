<script setup>
import { onMounted, ref } from 'vue'
import { api, hasAccessToken, setAccessToken } from './api/client.js'
import ToastStack from './components/common/ToastStack.vue'
import AppShell from './components/layout/AppShell.vue'
import AuthPage from './pages/auth/AuthPage.vue'
import DashboardPage from './pages/developer/DashboardPage.vue'
import AppsPage from './pages/developer/AppsPage.vue'
import AppDetailPage from './pages/developer/AppDetailPage.vue'
import ReviewsOverviewPage from './pages/developer/ReviewsOverviewPage.vue'
import AdminRegistrationsPage from './pages/admin/AdminRegistrationsPage.vue'
import AdminVersionsPage from './pages/admin/AdminVersionsPage.vue'
import AdminReviewsPage from './pages/admin/AdminReviewsPage.vue'
import { asList } from './utils/format.js'

const booting = ref(true)
const account = ref(null)
const currentView = ref('dashboard')
const selectedApp = ref(null)
const apps = ref([])
const dashboard = ref({})
const loadingApps = ref(false)
const toasts = ref([])
let nextToastId = 1

function notify(message, tone = 'success') {
  const id = nextToastId++
  toasts.value.push({ id, message, tone })
  window.setTimeout(() => dismissToast(id), 4200)
}
function dismissToast(id) { toasts.value = toasts.value.filter((item) => item.id !== id) }

async function refreshDeveloperData() {
  if (account.value?.role !== 'DEVELOPER') return
  loadingApps.value = true
  try {
    const [appData, dashboardData] = await Promise.all([api.developer.apps(), api.developer.dashboard()])
    apps.value = asList(appData)
    dashboard.value = dashboardData || {}
    if (selectedApp.value) selectedApp.value = apps.value.find((item) => item.id === selectedApp.value.id) || selectedApp.value
  } catch (error) { notify(error.message, 'error') }
  finally { loadingApps.value = false }
}

async function acceptAuthentication(result) {
  setAccessToken(result.token)
  account.value = result.account
  currentView.value = account.value.role === 'ADMIN' ? 'admin-registrations' : 'dashboard'
  await refreshDeveloperData()
}

async function logout() {
  try { await api.auth.logout() } catch { /* 本地退出仍需生效 */ }
  setAccessToken('')
  account.value = null
  apps.value = []
  selectedApp.value = null
}

function navigate(view) { selectedApp.value = null; currentView.value = view }
function openApp(app) { selectedApp.value = app; currentView.value = 'app-detail' }
async function onAppChanged() { await refreshDeveloperData() }

onMounted(async () => {
  if (hasAccessToken()) {
    try {
      account.value = await api.auth.me()
      currentView.value = account.value.role === 'ADMIN' ? 'admin-registrations' : 'dashboard'
      await refreshDeveloperData()
    } catch { setAccessToken(''); account.value = null }
  }
  booting.value = false
})
</script>

<template>
  <div v-if="booting" class="app-boot"><div class="brand-mark brand-mark--pulse" aria-hidden="true"><span></span><span></span></div><p>正在校验工作台会话…</p></div>
  <AuthPage v-else-if="!account" @authenticated="acceptAuthentication" @notice="notify" />
  <AppShell v-else :account="account" :current-view="currentView" @navigate="navigate" @logout="logout">
    <AdminRegistrationsPage v-if="currentView === 'admin-registrations'" @notice="notify" />
    <AdminVersionsPage v-else-if="currentView === 'admin-versions'" @notice="notify" />
    <AdminReviewsPage v-else-if="currentView === 'admin-reviews'" @notice="notify" />
    <AppDetailPage v-else-if="currentView === 'app-detail' && selectedApp" :app="selectedApp" @back="navigate('apps')" @updated="onAppChanged" @notice="notify" />
    <AppsPage v-else-if="currentView === 'apps'" :apps="apps" :loading="loadingApps" @created="refreshDeveloperData" @open-app="openApp" @notice="notify" />
    <ReviewsOverviewPage v-else-if="currentView === 'reviews'" :apps="apps" @open-app="openApp" />
    <DashboardPage v-else :account="account" :dashboard="dashboard" :apps="apps" :loading="loadingApps" @open-app="openApp" @navigate="navigate" />
  </AppShell>
  <ToastStack :items="toasts" @dismiss="dismissToast" />
</template>
