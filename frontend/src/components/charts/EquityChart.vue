<!--
  EquityChart: DCA vs lump-sum value over time, plotted on the pad grid.
  One unbroken timeline; the scrub marker always shows where "now" is (defaults to the last session).
  Pointer: hover/drag to scrub. Keyboard: focus the plot, then ← → Home End.
-->
<template>
  <figure class="equity-chart">
    <div class="readout" aria-live="polite">
      <span class="ro-date fig">{{ active ? formatDay(active.date) : '' }}</span>
      <span class="ro-item">
        <i class="key key-dca" aria-hidden="true"></i>{{ labels.dca }}
        <strong class="fig">{{ active ? money(active.dcaValue) : '--' }}</strong>
      </span>
      <span class="ro-item">
        <i class="key key-lump" aria-hidden="true"></i>{{ labels.lump }}
        <strong class="fig">{{ active ? money(active.lumpValue) : '--' }}</strong>
      </span>
      <span class="ro-item ro-invested">
        <i class="key key-invested" aria-hidden="true"></i>{{ labels.invested }}
        <strong class="fig">{{ active ? money(active.dcaInvested) : '--' }}</strong>
      </span>
    </div>

    <div ref="boxRef" class="plot-box">
      <svg
        v-if="width > 0 && points.length > 1"
        class="plot"
        :width="width"
        :height="height"
        :viewBox="`0 0 ${width} ${height}`"
        role="img"
        tabindex="0"
        :aria-label="summary"
        @pointermove="onPointer"
        @pointerdown="onPointer"
        @keydown="onKey"
      >
        <!-- y gridlines + labels -->
        <g class="y-axis">
          <g v-for="tick in yTicks" :key="tick">
            <line :x1="pad.l" :x2="width - pad.r" :y1="y(tick)" :y2="y(tick)" class="gridline" />
            <text :x="pad.l - 8" :y="y(tick)" class="tick-label" text-anchor="end" dominant-baseline="middle">
              {{ compactMoney(tick) }}
            </text>
          </g>
        </g>
        <!-- x month ticks -->
        <g class="x-axis">
          <line :x1="pad.l" :x2="width - pad.r" :y1="height - pad.b" :y2="height - pad.b" class="axis" />
          <g v-for="tick in xTicks" :key="tick.i">
            <line :x1="x(tick.i)" :x2="x(tick.i)" :y1="height - pad.b" :y2="height - pad.b + 6" class="axis" />
            <text :x="x(tick.i)" :y="height - pad.b + 20" class="tick-label" text-anchor="middle">{{ tick.label }}</text>
          </g>
        </g>

        <path :d="investedPath" class="line-invested" />
        <path :d="lumpPath" class="line line-lump" pathLength="1" />
        <path :d="dcaPath" class="line line-dca" pathLength="1" />

        <!-- scrub marker -->
        <g v-if="active" class="marker">
          <line :x1="x(activeIndex)" :x2="x(activeIndex)" :y1="pad.t" :y2="height - pad.b" class="marker-line" />
          <circle :cx="x(activeIndex)" :cy="y(active.lumpValue)" r="4.5" class="dot dot-lump" />
          <circle :cx="x(activeIndex)" :cy="y(active.dcaValue)" r="4.5" class="dot dot-dca" />
        </g>
      </svg>
    </div>
    <figcaption class="caption">{{ caption }}</figcaption>
  </figure>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { EquityPoint } from '@/utils/equityCurve'

const props = withDefaults(
  defineProps<{
    points: EquityPoint[]
    labels?: { dca: string; lump: string; invested: string }
    caption?: string
  }>(),
  {
    labels: () => ({ dca: 'DCA', lump: 'Lump Sum', invested: 'DCA invested' }),
    caption: ''
  }
)

const boxRef = ref<HTMLDivElement | null>(null)
const width = ref(0)
const activeIndex = ref(0)
let observer: ResizeObserver | null = null

const height = computed(() => (width.value < 560 ? 240 : 320))
const pad = computed(() => ({ t: 16, r: 16, b: 32, l: width.value < 560 ? 48 : 64 }))

const values = computed(() =>
  props.points.flatMap((p) => [p.dcaValue, p.lumpValue, p.dcaInvested]).filter((v) => v > 0)
)

const yDomain = computed<[number, number]>(() => {
  if (values.value.length === 0) return [0, 1]
  const min = Math.min(...values.value)
  const max = Math.max(...values.value)
  const span = max - min || max || 1
  return [Math.max(0, min - span * 0.08), max + span * 0.08]
})

const yTicks = computed(() => niceTicks(yDomain.value[0], yDomain.value[1], width.value < 560 ? 4 : 5))

function x(i: number) {
  const n = Math.max(props.points.length - 1, 1)
  return pad.value.l + (i / n) * (width.value - pad.value.l - pad.value.r)
}

function y(v: number) {
  const [lo, hi] = yDomain.value
  const inner = height.value - pad.value.t - pad.value.b
  return pad.value.t + inner - ((v - lo) / (hi - lo || 1)) * inner
}

function pathFor(pick: (p: EquityPoint) => number, step = false) {
  let d = ''
  let started = false
  props.points.forEach((p, i) => {
    const v = pick(p)
    if (v <= 0) return
    const px = x(i).toFixed(1)
    const py = y(v).toFixed(1)
    if (!started) {
      d += `M${px},${py}`
      started = true
    } else if (step) {
      d += `H${px}V${py}`
    } else {
      d += `L${px},${py}`
    }
  })
  return d
}

const dcaPath = computed(() => pathFor((p) => p.dcaValue))
const lumpPath = computed(() => pathFor((p) => p.lumpValue))
const investedPath = computed(() => pathFor((p) => p.dcaInvested, true))

const xTicks = computed(() => {
  const ticks: { i: number; label: string }[] = []
  let lastMonth = ''
  props.points.forEach((p, i) => {
    const month = p.date.slice(0, 7)
    if (month !== lastMonth) {
      ticks.push({ i, label: new Date(`${p.date}T00:00:00Z`).toLocaleDateString('en-US', { month: 'short', timeZone: 'UTC' }) })
      lastMonth = month
    }
  })
  return ticks
})

const active = computed(() => props.points[activeIndex.value] ?? null)

const summary = computed(() => {
  const last = props.points.at(-1)
  if (!last) return ''
  return `${props.labels.dca} ends at ${money(last.dcaValue)}; ${props.labels.lump} ends at ${money(last.lumpValue)}. Use left and right arrow keys to read each session.`
})

function onPointer(event: PointerEvent) {
  const svg = event.currentTarget as SVGSVGElement
  const rect = svg.getBoundingClientRect()
  const px = event.clientX - rect.left
  const n = props.points.length - 1
  const ratio = (px - pad.value.l) / (width.value - pad.value.l - pad.value.r)
  activeIndex.value = Math.min(n, Math.max(0, Math.round(ratio * n)))
}

function onKey(event: KeyboardEvent) {
  const n = props.points.length - 1
  const moves: Record<string, number> = {
    ArrowLeft: activeIndex.value - 1,
    ArrowRight: activeIndex.value + 1,
    Home: 0,
    End: n
  }
  const next = moves[event.key]
  if (next === undefined) return
  event.preventDefault()
  activeIndex.value = Math.min(n, Math.max(0, next))
}

function niceTicks(lo: number, hi: number, count: number) {
  const span = hi - lo
  if (span <= 0) return [lo]
  const raw = span / count
  const mag = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 2.5, 5, 10].map((m) => m * mag).find((s) => s >= raw) ?? raw
  const start = Math.ceil(lo / step) * step
  const ticks: number[] = []
  for (let v = start; v <= hi; v += step) ticks.push(Math.round(v * 100) / 100)
  return ticks
}

function money(v: number) {
  return `$${v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function compactMoney(v: number) {
  return `$${v.toLocaleString('en-US', { notation: 'compact', maximumFractionDigits: 1 })}`
}

function formatDay(date: string) {
  return new Date(`${date}T00:00:00Z`).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    timeZone: 'UTC'
  })
}

watch(
  () => props.points,
  (pts) => {
    activeIndex.value = Math.max(pts.length - 1, 0)
  },
  { immediate: true }
)

onMounted(() => {
  if (!boxRef.value) return
  width.value = boxRef.value.clientWidth
  observer = new ResizeObserver((entries) => {
    const entry = entries[0]
    if (entry) width.value = Math.floor(entry.contentRect.width)
  })
  observer.observe(boxRef.value)
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<style scoped>
.equity-chart {
  margin: 0;
}

.readout {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2) var(--space-6);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--rule);
  font-size: var(--text-sm);
  color: var(--ink-2);
}

.ro-date {
  color: var(--ink);
  font-weight: 600;
  min-width: 9rem;
}

.ro-item {
  display: inline-flex;
  align-items: baseline;
  gap: var(--space-2);
}

.ro-item strong {
  color: var(--ink);
  font-weight: 600;
}

.key {
  display: inline-block;
  width: 18px;
  height: 0;
  border-top: 2.5px solid var(--series-1);
  transform: translateY(-3px);
}

.key-lump {
  border-top-color: var(--series-2);
}

.key-invested {
  border-top: 2px dashed var(--series-invested);
}

.plot-box {
  width: 100%;
  min-height: 240px;
}

.plot {
  display: block;
  touch-action: pan-y;
  cursor: crosshair;
}

.plot:focus-visible {
  outline: 2px solid var(--focus);
  outline-offset: 4px;
}

.gridline {
  stroke: var(--grid-major);
  stroke-width: 1;
}

.axis {
  stroke: var(--ink);
  stroke-width: 1.5;
}

.tick-label {
  fill: var(--ink-2);
  font-family: var(--font-figure);
  font-size: 11px;
}

.line {
  fill: none;
  stroke-linejoin: round;
  stroke-linecap: round;
  stroke-dasharray: 1;
  stroke-dashoffset: 0;
  animation: draw var(--dur-draw) var(--ease-out) both;
}

.line-dca {
  stroke: var(--series-1);
  stroke-width: 2.5;
}

.line-lump {
  stroke: var(--series-2);
  stroke-width: 2.5;
  animation-delay: 120ms;
}

.line-invested {
  stroke: var(--series-invested);
  stroke-width: 1.5;
  stroke-dasharray: 5 4;
  fill: none;
}

.marker-line {
  stroke: var(--ink);
  stroke-width: 1;
  stroke-dasharray: 3 3;
}

.dot {
  stroke: var(--paper);
  stroke-width: 2;
}

.dot-dca {
  fill: var(--series-1);
}

.dot-lump {
  fill: var(--series-2);
}

@keyframes draw {
  from {
    stroke-dashoffset: 1;
  }
  to {
    stroke-dashoffset: 0;
  }
}

.caption {
  margin-top: var(--space-2);
  font-size: var(--text-xs);
  color: var(--ink-2);
}
</style>
