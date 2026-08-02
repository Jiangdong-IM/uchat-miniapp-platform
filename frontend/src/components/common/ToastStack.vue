<script setup>
import { CircleCheck, CircleX, Info, X } from '@lucide/vue'

defineProps({ items: { type: Array, default: () => [] } })
defineEmits(['dismiss'])
</script>

<template>
  <div class="toast-stack" aria-live="polite" aria-atomic="true">
    <div v-for="item in items" :key="item.id" class="toast" :class="`toast--${item.tone}`">
      <CircleCheck v-if="item.tone === 'success'" :size="18" />
      <CircleX v-else-if="item.tone === 'error'" :size="18" />
      <Info v-else :size="18" />
      <span>{{ item.message }}</span>
      <button type="button" aria-label="关闭提示" @click="$emit('dismiss', item.id)"><X :size="16" /></button>
    </div>
  </div>
</template>
