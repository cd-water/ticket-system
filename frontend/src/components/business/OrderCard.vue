<script setup lang="ts">
import { computed } from 'vue'
import { MapPin } from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useCountdown } from '@/composables/useCountdown'
import { ORDER_STATUS_META, formatDateTime, formatMoney } from '@/lib/format'
import type { OrderVO } from '@/types/order'

const props = defineProps<{ order: OrderVO }>()
const emit = defineEmits<{
  pay: [orderNo: string]
  cancel: [orderNo: string]
  expired: [orderNo: string]
}>()

const meta = computed(() => ORDER_STATUS_META[props.order.status])

// enabled 传 getter 而非布尔快照：v-for 按 orderNo 复用卡片实例，
// 支付后 Vue 就地改 props，捕获的布尔会继续跑倒计时并对已支付订单发取消
const { text } = useCountdown(
  props.order.expireTime,
  () => props.order.status === 0,
  () => emit('expired', props.order.orderNo),
)
</script>

<template>
  <article
    class="relative overflow-hidden rounded-md border border-border bg-card p-5 pl-6 shadow-card"
  >
    <span
      class="absolute inset-y-0 left-0 w-[3px]"
      :class="meta.accentClass"
      aria-hidden="true"
    />

    <header class="flex items-baseline gap-3">
      <h3 class="min-w-0 flex-1 truncate text-base font-bold">{{ order.eventName }}</h3>
      <span class="flex shrink-0 items-baseline gap-1.5">
        <span class="text-xs text-muted-foreground">应付</span>
        <b class="font-serif text-xl font-black text-brand-press tabular-nums">
          {{ formatMoney(order.amount) }}
        </b>
      </span>
      <Badge :class="meta.badgeClass" class="shrink-0 rounded-full">{{ meta.label }}</Badge>
    </header>

    <p class="mt-1.5 flex items-center gap-1.5 text-xs text-muted-foreground">
      <MapPin class="size-3 shrink-0" />
      <span class="truncate">{{ order.eventAddress }}</span>
    </p>

    <dl
      class="mt-3.5 grid grid-cols-2 gap-x-10 gap-y-2 border-t border-line-soft pt-3.5 text-xs max-sm:grid-cols-1"
    >
      <div class="flex gap-2.5">
        <dt class="w-14 shrink-0 text-muted-foreground">订单号</dt>
        <dd class="truncate text-muted-foreground tabular-nums">{{ order.orderNo }}</dd>
      </div>
      <div class="flex gap-2.5">
        <dt class="w-14 shrink-0 text-muted-foreground">座位</dt>
        <dd class="text-muted-foreground">
          {{ order.seat ? `${order.seat.rowNo} 排 ${order.seat.colNo} 座` : '不限座' }}
        </dd>
      </div>
      <div class="flex gap-2.5">
        <dt class="w-14 shrink-0 text-muted-foreground">下单时间</dt>
        <dd class="text-muted-foreground tabular-nums">{{ formatDateTime(order.createTime) }}</dd>
      </div>
      <div class="flex gap-2.5">
        <dt class="w-14 shrink-0 text-muted-foreground">支付时间</dt>
        <dd class="text-muted-foreground tabular-nums">{{ formatDateTime(order.payTime) }}</dd>
      </div>
    </dl>

    <div
      v-if="order.status === 0"
      class="mt-3.5 flex flex-wrap items-center gap-2.5 border-t border-line-soft pt-3.5"
    >
      <span class="mr-auto flex items-baseline gap-1">
        <span class="text-xs font-medium text-muted-foreground">剩余</span>
        <span class="font-serif text-lg font-black text-brand-press tabular-nums">{{ text }}</span>
      </span>
      <Button
        variant="outline"
        class="h-9 rounded-full border-border px-4"
        @click="emit('cancel', order.orderNo)"
      >
        取消订单
      </Button>
      <Button class="h-9 rounded-full px-4" @click="emit('pay', order.orderNo)">立即支付</Button>
    </div>
  </article>
</template>
