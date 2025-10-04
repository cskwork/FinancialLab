<!--
  Portfolio Chart 컴포넌트 (Placeholder)
  Chart.js를 활용한 포트폴리오 차트 (향후 구현 예정)
-->
<template>
  <div class="portfolio-chart">
    <canvas ref="chartCanvas"></canvas>
    <p v-if="!hasData" class="no-data">차트 데이터가 없습니다</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

interface Props {
  data?: number[]
  labels?: string[]
}

const props = withDefaults(defineProps<Props>(), {
  data: () => [],
  labels: () => []
})

const chartCanvas = ref<HTMLCanvasElement>()
const hasData = ref(false)

onMounted(() => {
  hasData.value = props.data.length > 0
  // TODO: Chart.js 초기화
  // import { Chart } from 'chart.js/auto'
  // const chart = new Chart(chartCanvas.value!, { ... })
})
</script>

<style scoped>
.portfolio-chart {
  position: relative;
  padding: var(--space-4);
  min-height: 300px;
}

.no-data {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: var(--color-text-muted);
  text-align: center;
}
</style>
