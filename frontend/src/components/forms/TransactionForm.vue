<!--
  Transaction Form 컴포넌트 (Placeholder)
  거래 생성/수정을 위한 폼 (향후 구현 예정)
-->
<template>
  <form class="transaction-form" @submit.prevent="handleSubmit">
    <div class="form-group">
      <label for="type">거래 유형</label>
      <select id="type" v-model="formData.type" required>
        <option value="BUY">매수</option>
        <option value="SELL">매도</option>
        <option value="DEPOSIT">입금</option>
        <option value="WITHDRAW">출금</option>
      </select>
    </div>

    <div class="form-group">
      <label for="symbol">심볼</label>
      <input id="symbol" v-model="formData.symbol" type="text" placeholder="AAPL" />
    </div>

    <div class="form-group">
      <label for="qty">수량</label>
      <input id="qty" v-model.number="formData.qty" type="number" step="0.0001" />
    </div>

    <div class="form-group">
      <label for="price">가격</label>
      <input id="price" v-model.number="formData.price" type="number" step="0.01" />
    </div>

    <div class="form-actions">
      <button type="button" class="btn-cancel" @click="handleCancel">취소</button>
      <button type="submit" class="btn-submit">저장</button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { reactive } from 'vue'

interface TransactionFormData {
  type: 'BUY' | 'SELL' | 'DEPOSIT' | 'WITHDRAW'
  symbol?: string
  qty?: number
  price?: number
}

interface Emits {
  (e: 'submit', data: TransactionFormData): void
  (e: 'cancel'): void
}

const emit = defineEmits<Emits>()

const formData = reactive<TransactionFormData>({
  type: 'BUY'
})

function handleSubmit() {
  emit('submit', formData)
}

function handleCancel() {
  emit('cancel')
}
</script>

<style scoped>
.transaction-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.form-group label {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.form-group input,
.form-group select {
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-base);
}

.form-actions {
  display: flex;
  gap: var(--space-3);
  justify-content: flex-end;
  margin-top: var(--space-4);
}

.btn-cancel,
.btn-submit {
  padding: var(--space-3) var(--space-6);
  border: none;
  border-radius: var(--border-radius-md);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  transition: all var(--transition-base);
}

.btn-cancel {
  background-color: var(--color-gray-200);
  color: var(--color-text);
}

.btn-cancel:hover {
  background-color: var(--color-gray-300);
}

.btn-submit {
  background-color: var(--color-primary-600);
  color: #fff;
}

.btn-submit:hover {
  background-color: var(--color-primary-700);
}
</style>
