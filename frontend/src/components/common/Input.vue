<!--
  공통 Input 컴포넌트
  재사용 가능한 입력 필드 컴포넌트
-->
<template>
  <div class="input-wrapper">
    <label v-if="label" :for="inputId" class="input-label">
      {{ label }}
      <span v-if="required" class="required">*</span>
    </label>
    <input
      :id="inputId"
      v-model="internalValue"
      :type="type"
      :placeholder="placeholder"
      :required="required"
      :disabled="disabled"
      :class="['input-field', { 'input-error': error }]"
      @blur="handleBlur"
    />
    <span v-if="error" class="error-message">{{ error }}</span>
    <span v-else-if="hint" class="hint-message">{{ hint }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

interface Props {
  modelValue?: string | number
  label?: string
  type?: 'text' | 'email' | 'password' | 'number' | 'url' | 'tel' | 'date'
  placeholder?: string
  required?: boolean
  disabled?: boolean
  error?: string
  hint?: string
}

interface Emits {
  (e: 'update:modelValue', value: string | number): void
  (e: 'blur'): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  type: 'text',
  required: false,
  disabled: false
})

const emit = defineEmits<Emits>()

const inputId = ref(`input-${Math.random().toString(36).substring(7)}`)

const internalValue = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

function handleBlur() {
  emit('blur')
}
</script>

<style scoped>
.input-wrapper {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.input-label {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.required {
  color: var(--color-error-600);
}

.input-field {
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-base);
  transition: all var(--transition-base);
}

.input-field:focus {
  outline: none;
  border-color: var(--color-primary-600);
  box-shadow: 0 0 0 3px var(--color-primary-50);
}

.input-field:disabled {
  background-color: var(--color-gray-100);
  cursor: not-allowed;
}

.input-error {
  border-color: var(--color-error-600);
}

.input-error:focus {
  box-shadow: 0 0 0 3px var(--color-error-50);
}

.error-message {
  font-size: var(--font-size-sm);
  color: var(--color-error-600);
}

.hint-message {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}
</style>
