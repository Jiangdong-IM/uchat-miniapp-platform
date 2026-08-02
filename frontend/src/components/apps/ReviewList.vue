<script setup>
import { AppWindow, MessageSquareText, Star } from '@lucide/vue'
import EmptyState from '../common/EmptyState.vue'
import StatusBadge from '../common/StatusBadge.vue'
import { formatDateTime } from '../../utils/format.js'

defineProps({ reviews: { type: Array, default: () => [] }, canFeature: Boolean, busyId: { default: null } })
defineEmits(['feature'])
</script>

<template>
  <div v-if="reviews.length" class="review-list">
    <article v-for="review in reviews" :key="review.id" class="review-row" :class="{ 'review-row--featured': review.featured }">
      <div class="review-row__avatar">{{ (review.displayName || 'U').slice(0, 1) }}</div>
      <div class="review-row__content">
        <div v-if="review.appName" class="review-row__app"><AppWindow :size="13" /><strong>{{ review.appName }}</strong><code>{{ review.appId }}</code><StatusBadge :status="review.status" /></div>
        <div class="review-row__byline"><strong>{{ review.displayName || 'UChat 用户' }}</strong><span>{{ formatDateTime(review.updatedAt || review.createdAt) }}</span></div>
        <p>{{ review.content }}</p>
        <small v-if="review.score"><Star :size="13" fill="currentColor" /> {{ review.score }} 分</small>
      </div>
      <button v-if="canFeature" class="button button--compact" :class="review.featured ? 'button--secondary' : 'button--ghost'" type="button" :disabled="busyId === review.id" @click="$emit('feature', review)">
        {{ review.featured ? '取消精选' : '设为精选' }}
      </button>
      <span v-else-if="review.featured" class="featured-mark"><MessageSquareText :size="14" /> 精选</span>
    </article>
  </div>
  <EmptyState v-else title="暂无用户评价" description="上架后，UChat 用户提交的评论与评分会显示在这里。" />
</template>
