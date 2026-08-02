<script setup>
import { X } from '@lucide/vue'

defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, required: true },
  description: { type: String, default: '' },
  busy: { type: Boolean, default: false },
  wide: { type: Boolean, default: false },
})
defineEmits(['close'])
</script>

<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="open" class="modal-backdrop" @mousedown.self="$emit('close')">
        <section class="modal-panel" :class="{ 'modal-panel--wide': wide }" role="dialog" aria-modal="true" :aria-label="title">
          <header class="modal-panel__header">
            <div>
              <p class="eyebrow">工作台操作</p>
              <h2>{{ title }}</h2>
              <p v-if="description" class="modal-panel__description">{{ description }}</p>
            </div>
            <button class="icon-button" type="button" aria-label="关闭" :disabled="busy" @click="$emit('close')"><X :size="20" /></button>
          </header>
          <div class="modal-panel__body"><slot /></div>
          <footer v-if="$slots.footer" class="modal-panel__footer"><slot name="footer" /></footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
