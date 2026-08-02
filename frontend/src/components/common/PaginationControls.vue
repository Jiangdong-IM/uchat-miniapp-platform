<script setup>
import { computed } from 'vue'
import { ChevronLeft, ChevronRight } from '@lucide/vue'

const props = defineProps({
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
})
const emit = defineEmits(['change'])

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
const firstItem = computed(() => props.total ? ((props.page - 1) * props.pageSize) + 1 : 0)
const lastItem = computed(() => Math.min(props.total, props.page * props.pageSize))
</script>

<template>
  <nav v-if="total > pageSize" class="pagination" aria-label="分页">
    <p>第 {{ firstItem }}–{{ lastItem }} 条，共 {{ total }} 条</p>
    <div>
      <button class="icon-button" type="button" aria-label="上一页" :disabled="loading || page <= 1" @click="emit('change', page - 1)">
        <ChevronLeft :size="18" />
      </button>
      <span aria-current="page">{{ page }} / {{ totalPages }}</span>
      <button class="icon-button" type="button" aria-label="下一页" :disabled="loading || page >= totalPages" @click="emit('change', page + 1)">
        <ChevronRight :size="18" />
      </button>
    </div>
  </nav>
</template>
