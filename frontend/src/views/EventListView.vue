<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyTitle,
} from '@/components/ui/empty'
import EventCardTile from '@/components/business/EventCardTile.vue'
import { listEvents } from '@/api/event'
import { ApiError } from '@/api/http'
import type { EventCard, EventMode } from '@/types/event'

const props = defineProps<{ mode: EventMode }>()

const route = useRoute()
const router = useRouter()

const PAGE_SIZE = 10

const loading = ref(false)
const records = ref<EventCard[]>([])
const total = ref(0)
const error = ref('')
const keyword = ref((route.query.keyword as string) ?? '')

const page = computed(() => {
  const raw = Number(route.query.page ?? 1)
  return Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : 1
})
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

let seq = 0

watch(() => route.query.keyword, (value) => {
  keyword.value = (value as string) ?? ''
})

// 单一 watcher 覆盖 mode / keyword / page；漏掉任何一个都会导致搜索后列表不刷新
watch(() => [props.mode, route.query.keyword, route.query.page], load, { immediate: true })

async function load() {
  const id = ++seq
  loading.value = true
  error.value = ''
  try {
    const result = await listEvents({
      mode: props.mode,
      keyword: (route.query.keyword as string) || undefined,
      page: page.value,
      size: PAGE_SIZE,
    })
    if (id !== seq) return
    // 越界页码后端返回空切片，回退第 1 页重拉，否则空态会谎称「没有找到相关活动」；
    // total > 0 只是不把真正的空结果误弹回第 1 页，防死循环靠 page > 1 一条
    if (!result.records.length && result.total > 0 && page.value > 1) {
      await router.replace({ query: { ...route.query, page: '1' } })
      return
    }
    records.value = result.records
    total.value = result.total
  } catch (cause) {
    if (id !== seq) return
    records.value = []
    total.value = 0
    error.value = cause instanceof ApiError ? cause.message : '加载失败'
    toast.error(error.value)
  } finally {
    if (id === seq) loading.value = false
  }
}

function search() {
  void router.push({ query: { ...(keyword.value ? { keyword: keyword.value } : {}) } })
}

function reset() {
  keyword.value = ''
  void router.replace({ query: {} })
}

function goPage(next: number) {
  if (next < 1 || next > pageCount.value) return
  void router.push({ query: { ...route.query, page: String(next) } })
}
</script>

<template>
  <main class="mx-auto w-[min(1180px,calc(100vw-48px))] py-9 pb-22">
    <h1 class="mb-6 font-serif text-[27px] font-black">{{ mode === 1 ? '抢票' : '选座' }}</h1>

    <div class="mb-5 flex flex-wrap items-center gap-3">
      <div class="relative max-w-100 flex-1 basis-full sm:basis-0">
        <Search class="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          v-model="keyword"
          placeholder="请输入活动名称"
          class="h-[42px] pl-9"
          @keydown.enter="search"
        />
      </div>
      <Button class="h-[42px] rounded-full px-5" @click="search">搜索</Button>
      <Button variant="outline" class="h-[42px] rounded-full border-border px-5" @click="reset">重置</Button>
    </div>

    <div v-if="loading" class="grid grid-cols-5 gap-4 max-lg:grid-cols-3 max-md:grid-cols-2 max-sm:grid-cols-1">
      <Skeleton v-for="n in 5" :key="n" class="h-[132px] rounded-md" />
    </div>

    <div
      v-else-if="error"
      class="rounded-[20px] border-2 border-dashed border-border bg-card py-16 text-center"
    >
      <p class="text-[15px] font-bold">{{ error }}</p>
      <p class="mt-2 text-sm text-muted-foreground">稍后再试</p>
    </div>

    <Empty v-else-if="!records.length" class="rounded-[20px] border-2 border-dashed border-border bg-card py-16">
      <EmptyHeader>
        <EmptyTitle>没有找到相关活动</EmptyTitle>
        <EmptyDescription>换个关键词试试</EmptyDescription>
      </EmptyHeader>
    </Empty>

    <template v-else>
      <div class="grid grid-cols-5 gap-4 max-lg:grid-cols-3 max-md:grid-cols-2 max-sm:grid-cols-1">
        <EventCardTile v-for="event in records" :key="event.id" :event="event" />
      </div>

      <div
        v-if="pageCount > 1"
        class="mt-6 flex items-center justify-center gap-3 text-sm text-muted-foreground"
      >
        <Button
          variant="outline"
          class="h-9 rounded-full border-border px-4"
          :disabled="page <= 1"
          @click="goPage(page - 1)"
        >
          上一页
        </Button>
        <span class="tabular-nums">{{ page }} / {{ pageCount }}</span>
        <Button
          variant="outline"
          class="h-9 rounded-full border-border px-4"
          :disabled="page >= pageCount"
          @click="goPage(page + 1)"
        >
          下一页
        </Button>
      </div>
    </template>
  </main>
</template>
