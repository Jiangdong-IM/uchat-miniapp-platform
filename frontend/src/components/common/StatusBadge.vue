<script setup>
import { computed } from 'vue'
import { statusLabel, statusTone } from '../../utils/format.js'

const props = defineProps({ status: { type: String, default: '' } })
const tone = computed(() => statusTone(props.status))
</script>

<template>
  <span class="status-badge" :class="`status-badge--${tone}`">
    <span class="status-badge__dot" aria-hidden="true"></span>
    {{ statusLabel(status) }}
  </span>
</template>
