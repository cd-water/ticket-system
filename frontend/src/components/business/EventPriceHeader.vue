<script setup lang="ts">
import { MapPin } from '@lucide/vue'
import { formatMoney } from '@/lib/format'

defineProps<{ name: string; address: string; price: number }>()
</script>

<template>
  <div>
    <span class="font-serif text-[38px] leading-none font-black text-brand-press tabular-nums">
      {{ formatMoney(price) }}
    </span>
    <h2 class="mt-3.5 font-serif text-xl font-black">{{ name }}</h2>
    <p class="mt-4 flex items-start gap-2 border-t border-line-soft pt-4 text-sm text-muted-foreground">
      <MapPin class="mt-0.5 size-4 shrink-0 text-muted-foreground/70" />
      <span>{{ address }}</span>
    </p>
  </div>
</template>
