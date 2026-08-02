<script setup>
import { computed } from 'vue'
import { AppWindow, ArrowRight, CircleGauge, PackageCheck, Star } from '@lucide/vue'
import AppDossierCard from '../../components/apps/AppDossierCard.vue'
import EmptyState from '../../components/common/EmptyState.vue'

const props = defineProps({
  account: { type: Object, required: true },
  dashboard: { type: Object, default: () => ({}) },
  apps: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})
const emit = defineEmits(['open-app', 'navigate'])

const metrics = computed(() => [
  { label: '小程序', value: props.dashboard.appCount ?? props.apps.length, helper: '最多可创建 10 个', icon: AppWindow },
  { label: '已上架', value: props.dashboard.publishedCount ?? props.apps.filter((item) => item.status === 'PUBLISHED').length, helper: '当前在线目录', icon: PackageCheck },
  { label: '审核中', value: props.dashboard.pendingVersionCount ?? 0, helper: '等待 admin 处理', icon: CircleGauge },
  { label: '综合评分', value: Number(props.dashboard.averageRating || 0) > 0 ? Number(props.dashboard.averageRating).toFixed(1) : '—', helper: '来自 UChat 用户', icon: Star },
])
</script>

<template>
  <div class="page-frame">
    <header class="page-heading">
      <div><p class="eyebrow">开发者概览</p><h1>{{ account.developerName }}，今天准备发布什么？</h1><p>版本、审核与用户反馈集中在同一条发布轨迹里。</p></div>
      <button class="button button--primary" type="button" @click="emit('navigate', 'apps')">管理小程序 <ArrowRight :size="17" /></button>
    </header>

    <section class="metric-strip" aria-label="账号概况">
      <article v-for="metric in metrics" :key="metric.label" class="metric-cell">
        <component :is="metric.icon" :size="19" stroke-width="1.7" />
        <span>{{ metric.label }}</span><strong>{{ metric.value }}</strong><small>{{ metric.helper }}</small>
      </article>
    </section>

    <section class="section-block">
      <div class="section-heading"><div><p class="eyebrow">最近维护</p><h2>小程序发布卷宗</h2></div><span class="section-index">A / 01</span></div>
      <div v-if="loading" class="skeleton-grid"><div v-for="item in 3" :key="item" class="skeleton-card"></div></div>
      <div v-else-if="apps.length" class="dossier-grid">
        <AppDossierCard v-for="app in apps.slice(0, 3)" :key="app.id" :app="app" @open="emit('open-app', $event)" />
      </div>
      <EmptyState v-else title="还没有小程序" description="创建第一份应用卷宗，上传图标、封面和程序包后提交审核。">
        <button class="button button--secondary" type="button" @click="emit('navigate', 'apps')">开始创建</button>
      </EmptyState>
    </section>

    <section class="release-guide">
      <div><p class="eyebrow eyebrow--light">发布规则</p><h2>一次提交，一条不可跳过的审核链</h2></div>
      <ol>
        <li><b>01</b><span><strong>资料完整</strong><small>名称、简介、图标、封面</small></span></li>
        <li><b>02</b><span><strong>包体校验</strong><small>manifest、权限、摘要</small></span></li>
        <li><b>03</b><span><strong>admin 审核</strong><small>通过后才激活正式版本</small></span></li>
      </ol>
    </section>
  </div>
</template>
