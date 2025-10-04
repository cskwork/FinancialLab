<!--
  공통 DataTable 컴포넌트
  재사용 가능한 테이블 컴포넌트 (향후 확장 예정)
  TODO: 정렬, 필터링, 페이지네이션 기능 추가
-->
<template>
  <div class="data-table-wrapper">
    <table class="data-table">
      <thead>
        <tr>
          <th v-for="column in columns" :key="column.key" :class="column.headerClass">
            {{ column.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="data.length === 0">
          <td :colspan="columns.length" class="empty-state">
            {{ emptyMessage }}
          </td>
        </tr>
        <tr v-for="(row, index) in data" :key="index" :class="rowClass">
          <td v-for="column in columns" :key="column.key" :class="column.cellClass">
            <slot :name="`cell-${column.key}`" :row="row" :value="row[column.key]">
              {{ row[column.key] }}
            </slot>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts" generic="T extends Record<string, any>">
interface Column {
  key: string
  label: string
  headerClass?: string
  cellClass?: string
}

interface Props {
  columns: Column[]
  data: T[]
  emptyMessage?: string
  rowClass?: string
}

withDefaults(defineProps<Props>(), {
  emptyMessage: '데이터가 없습니다',
  rowClass: ''
})
</script>

<style scoped>
.data-table-wrapper {
  overflow-x: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-lg);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-sm);
}

.data-table thead {
  background-color: var(--color-gray-50);
  border-bottom: 2px solid var(--color-border);
}

.data-table th {
  padding: var(--space-3) var(--space-4);
  text-align: left;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-muted);
  white-space: nowrap;
}

.data-table td {
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--color-border);
}

.data-table tbody tr:last-child td {
  border-bottom: none;
}

.data-table tbody tr:hover {
  background-color: var(--color-gray-50);
}

.empty-state {
  text-align: center;
  color: var(--color-text-muted);
  padding: var(--space-8) !important;
}
</style>
