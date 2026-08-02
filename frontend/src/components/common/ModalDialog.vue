<script setup>
import { nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'
import { X } from '@lucide/vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, required: true },
  description: { type: String, default: '' },
  busy: { type: Boolean, default: false },
  wide: { type: Boolean, default: false },
})
const emit = defineEmits(['close'])
const panel = ref(null)
const titleId = useId()
let previouslyFocused = null

function close() {
  if (!props.busy) emit('close')
}

function handleKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
    return
  }
  if (event.key !== 'Tab') return
  const focusable = [...panel.value.querySelectorAll('button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [href], [tabindex]:not([tabindex="-1"])')]
  if (!focusable.length) {
    event.preventDefault()
    panel.value.focus()
    return
  }
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(() => props.open, async (open) => {
  if (open) {
    previouslyFocused = document.activeElement
    await nextTick()
    panel.value?.focus()
  } else if (previouslyFocused instanceof HTMLElement) {
    previouslyFocused.focus()
    previouslyFocused = null
  }
}, { immediate: true })

onBeforeUnmount(() => {
  if (previouslyFocused instanceof HTMLElement) previouslyFocused.focus()
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="open" class="modal-backdrop" @mousedown.self="close">
        <section ref="panel" class="modal-panel" :class="{ 'modal-panel--wide': wide }" role="dialog" aria-modal="true" :aria-labelledby="titleId" tabindex="-1" @keydown="handleKeydown">
          <header class="modal-panel__header">
            <div>
              <p class="eyebrow">工作台操作</p>
              <h2 :id="titleId">{{ title }}</h2>
              <p v-if="description" class="modal-panel__description">{{ description }}</p>
            </div>
            <button class="icon-button" type="button" aria-label="关闭" :disabled="busy" @click="close"><X :size="20" /></button>
          </header>
          <div class="modal-panel__body"><slot /></div>
          <footer v-if="$slots.footer" class="modal-panel__footer"><slot name="footer" /></footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
