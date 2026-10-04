<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { formatMoney } from '@/lib/format'
import type { EventCard } from '@/types/event'

defineProps<{ event: EventCard }>()
</script>

<template>
  <RouterLink
    :to="`/events/${event.id}`"
    class="flex flex-col rounded-md bg-card p-4 shadow-sm transition-transform duration-200 hover:-translate-y-1 hover:shadow-md"
  >
    <span class="line-clamp-2 text-[14.5px] font-bold leading-snug">{{ event.name }}</span>
    <span class="mt-1.5 line-clamp-2 text-xs text-muted-foreground">{{ event.address }}</span>
    <span class="mt-3.5 font-serif text-[22px] font-black text-brand-press tabular-nums">
      {{ formatMoney(event.price) }}
    </span>
  </RouterLink>
</template>
