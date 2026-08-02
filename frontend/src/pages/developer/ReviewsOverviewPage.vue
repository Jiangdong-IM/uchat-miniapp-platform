<script setup>
import { MessageSquareText, Star } from '@lucide/vue'
import EmptyState from '../../components/common/EmptyState.vue'
import { formatScore } from '../../utils/format.js'

defineProps({ apps: { type: Array, default: () => [] } })
defineEmits(['open-app'])
</script>

<template>
  <div class="page-frame">
    <header class="page-heading"><div><p class="eyebrow">用户声音</p><h1>评分与评论</h1><p>评分与评论只来自已登录的 UChat 用户，开发者只能查看，不能修改。</p></div></header>
    <section class="review-overview">
      <button v-for="app in apps" :key="app.id" class="review-app-row" type="button" @click="$emit('open-app', app)">
        <div class="app-icon"><img v-if="app.iconUrl" :src="app.iconUrl" :alt="`${app.name} 图标`" /><span v-else>{{ (app.name || '?').slice(0, 1) }}</span></div>
        <div><p class="mono-label">{{ app.appId }}</p><strong>{{ app.name }}</strong><small>{{ app.description }}</small></div>
        <span><Star :size="16" fill="currentColor" /> {{ formatScore(app.averageRating) }}</span>
        <span><MessageSquareText :size="16" /> {{ app.commentCount || 0 }} 条</span>
      </button>
      <EmptyState v-if="!apps.length" title="暂无可查看的小程序" description="先创建并上架小程序，用户评价会在这里汇总。" />
    </section>
  </div>
</template>
