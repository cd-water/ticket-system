<script setup lang="ts">
import { computed } from 'vue'
import type { Seat } from '@/types/event'

const props = defineProps<{
  rows: number
  cols: number
  seats: Seat[]
  picked: number | null
}>()

const emit = defineEmits<{ pick: [seat: Seat] }>()

const aisleAt = computed(() => Math.ceil(props.cols / 2))

const byRow = computed(() => {
  const map = new Map<number, Seat[]>()
  for (const seat of props.seats) {
    const list = map.get(seat.rowNo)
    if (list) list.push(seat)
    else map.set(seat.rowNo, [seat])
  }
  return map
})

const rowNumbers = computed(() => Array.from({ length: props.rows }, (_, i) => i + 1))

function onPick(seat: Seat) {
  if (seat.status === 1) return
  emit('pick', seat)
}
</script>

<template>
  <div>
    <div class="overflow-x-auto pb-1">
      <div class="mx-auto w-max">
        <div v-for="rowNo in rowNumbers" :key="rowNo" class="flex items-center gap-2.5">
          <span class="w-6 shrink-0 text-right text-xs font-semibold text-muted-foreground">
            {{ rowNo }}
          </span>

          <template v-for="(seat, index) in byRow.get(rowNo) ?? []" :key="seat.seatId">
            <span v-if="index === aisleAt" class="w-5 shrink-0" aria-hidden="true" />
            <button
              type="button"
              class="grid size-[34px] shrink-0 place-items-center rounded-[7px] border border-transparent text-[11.5px] font-semibold transition-all duration-150"
              :class="[
                seat.status === 1
                  ? 'cursor-not-allowed bg-seat-taken text-muted-foreground/60'
                  : picked === seat.seatId
                    ? 'scale-105 bg-primary font-extrabold text-primary-foreground shadow-seat-glow'
                    : 'bg-seat-free text-muted-foreground hover:-translate-y-0.5 hover:bg-primary hover:text-primary-foreground',
              ]"
              :disabled="seat.status === 1"
              :aria-label="`${seat.rowNo}排${seat.colNo}座${seat.status === 1 ? ' 不可选' : ''}`"
              :style="{ '--r': rowNo }"
              @click="onPick(seat)"
            >
              {{ seat.colNo }}
            </button>
          </template>
        </div>
      </div>
    </div>

    <div class="mt-6 flex justify-center gap-4 border-t border-line-soft pt-4 text-xs text-muted-foreground">
      <span class="flex items-center gap-1.5">
        <i class="size-3.5 rounded-[6px] bg-seat-free" />可选
      </span>
      <span class="flex items-center gap-1.5">
        <i class="size-3.5 rounded-[6px] bg-primary" />已选
      </span>
      <span class="flex items-center gap-1.5">
        <i class="size-3.5 rounded-[6px] bg-seat-taken" />不可选
      </span>
    </div>
  </div>
</template>

<style scoped>
button {
  opacity: 0;
  animation: seat-drop 0.3s cubic-bezier(0.22, 0.61, 0.36, 1) forwards;
  animation-delay: calc(var(--r, 0) * 45ms);
}

@keyframes seat-drop {
  from {
    opacity: 0;
    transform: translateY(-7px) scale(0.9);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  button {
    animation-duration: 0.001ms;
    animation-delay: 0ms;
  }
}
</style>
