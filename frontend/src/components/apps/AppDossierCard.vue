<script setup>
import { ArrowUpRight, MessageSquareText, Star } from '@lucide/vue'
import StatusBadge from '../common/StatusBadge.vue'
import { formatScore } from '../../utils/format.js'

defineProps({ app: { type: Object, required: true } })
defineEmits(['open'])
</script>

<template>
  <article class="app-dossier">
    <button class="app-dossier__open" type="button" :aria-label="`打开 ${app.name}`" @click="$emit('open', app)"><ArrowUpRight :size="18" /></button>
    <div class="app-dossier__identity">
      <div class="app-icon">
        <img v-if="app.iconUrl" :src="app.iconUrl" :alt="`${app.name} 图标`" />
        <span v-else>{{ (app.name || '?').slice(0, 1) }}</span>
      </div>
      <div><p class="mono-label">{{ app.appId }}</p><h3>{{ app.name }}</h3></div>
    </div>
    <p class="app-dossier__description">{{ app.description || '尚未填写简介' }}</p>
    <div class="app-dossier__rule"></div>
    <div class="app-dossier__metrics">
      <div><span>状态</span><StatusBadge :status="app.status" /></div>
      <div><span>平均分</span><strong><Star :size="15" fill="currentColor" /> {{ formatScore(app.averageRating) }}</strong></div>
      <div><span>评论</span><strong><MessageSquareText :size="15" /> {{ app.commentCount || 0 }}</strong></div>
    </div>
  </article>
</template>
