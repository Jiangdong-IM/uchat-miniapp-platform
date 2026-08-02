<script setup>
import { computed, onMounted, ref } from 'vue'
import { MessageSquareText, RefreshCw, Star } from '@lucide/vue'
import { api } from '../../api/client.js'
import ReviewList from '../../components/apps/ReviewList.vue'
import { asList } from '../../utils/format.js'

const emit = defineEmits(['notice'])
const reviews = ref([])
const loading = ref(true)
const busyId = ref(null)
const error = ref('')
const filter = ref('all')

const featuredCount = computed(() => reviews.value.filter((review) => review.featured).length)
const filteredReviews = computed(() => {
  if (filter.value === 'featured') return reviews.value.filter((review) => review.featured)
  if (filter.value === 'standard') return reviews.value.filter((review) => !review.featured)
  return reviews.value
})
const filters = computed(() => [
  { id: 'all', label: '全部评价', count: reviews.value.length },
  { id: 'featured', label: '已精选', count: featuredCount.value },
  { id: 'standard', label: '未精选', count: reviews.value.length - featuredCount.value },
])

async function load() {
  loading.value = true
  error.value = ''
  try {
    reviews.value = asList(await api.admin.comments()).map((review) => ({
      ...review,
      displayName: review.userDisplayName,
    }))
  } catch (requestError) {
    error.value = requestError.message
  } finally {
    loading.value = false
  }
}

async function toggleFeatured(review) {
  busyId.value = review.id
  error.value = ''
  const featured = !review.featured
  try {
    await api.admin.featureComment(review.id, featured)
    review.featured = featured
    emit('notice', featured ? '评价已设为精选。' : '已取消精选评价。')
  } catch (requestError) {
    error.value = requestError.message
  } finally {
    busyId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page-frame">
    <header class="page-heading">
      <div><p class="eyebrow">ADMIN / VOICE</p><h1>用户评价精选</h1><p>从真实反馈中挑选有参考价值的内容；精选评价会展示在 UChat 小程序详情中。</p></div>
      <button class="button button--ghost" type="button" :disabled="loading" @click="load"><RefreshCw :size="16" /> 刷新</button>
    </header>

    <section class="curation-board">
      <header class="curation-toolbar">
        <div class="curation-toolbar__title"><span><MessageSquareText :size="18" /></span><div><strong>评价台账</strong><small>{{ reviews.length }} 条评价中，{{ featuredCount }} 条已精选</small></div></div>
        <nav class="curation-filters" aria-label="评价筛选">
          <button v-for="item in filters" :key="item.id" type="button" :class="{ active: filter === item.id }" :aria-pressed="filter === item.id" @click="filter = item.id"><span>{{ item.label }}</span><b>{{ item.count }}</b></button>
        </nav>
      </header>

      <p v-if="error" class="form-error curation-error" role="alert">{{ error }}</p>
      <div v-if="loading" class="curation-loading"><div v-for="item in 4" :key="item" class="skeleton-row"></div></div>
      <ReviewList v-else :reviews="filteredReviews" can-feature :busy-id="busyId" @feature="toggleFeatured" />
    </section>

    <aside class="curation-note"><Star :size="17" fill="currentColor" /><p><strong>精选原则</strong><span>优先选择信息具体、能帮助用户判断小程序价值的评价；取消精选不会删除原评论。</span></p></aside>
  </div>
</template>
