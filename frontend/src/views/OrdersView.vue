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
import OrderCard from '@/components/business/OrderCard.vue'
import { cancelOrder, listOrders, payOrder } from '@/api/order'
import { ApiError } from '@/api/http'
import type { OrderStatus, OrderVO } from '@/types/order'

const route = useRoute()
const router = useRouter()

const PAGE_SIZE = 10
const STATUS_TABS: { value: '' | '0' | '1' | '2'; label: string }[] = [
  { value: '', label: '全部' },
  { value: '0', label: '待支付' },
  { value: '1', label: '已支付' },
  { value: '2', label: '已取消' },
]

const loading = ref(false)
const records = ref<OrderVO[]>([])
const total = ref(0)
const error = ref('')
const orderNoInput = ref((route.query.orderNo as string) ?? '')

const page = computed(() => {
  const raw = Number(route.query.page ?? 1)
  return Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : 1
})
const status = computed(() => (route.query.status as string) ?? '')
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

// settling 挡住「归零与手动取消同刻发生」；autoCancelled 记录已自动取消过的订单，
// 否则取消失败后订单仍是待支付、卡片重建会再次触发归零，形成无限重试
const settling = new Set<string>()
const autoCancelled = new Set<string>()

let seq = 0

watch(() => route.query.orderNo, (value) => {
  orderNoInput.value = (value as string) ?? ''
})

// 单一 watcher 覆盖 orderNo / status / page；load 是唯一的取数入口
watch(() => [route.query.orderNo, route.query.status, route.query.page], load, { immediate: true })

async function load() {
  const id = ++seq
  loading.value = true
  error.value = ''
  try {
    const result = await listOrders({
      orderNo: (route.query.orderNo as string) || undefined,
      status: status.value ? (Number(status.value) as OrderStatus) : undefined,
      page: page.value,
      size: PAGE_SIZE,
    })
    if (id !== seq) return
    // 越界页码后端返回空切片，回退第 1 页重拉，否则空态会谎称「没有符合条件的订单」
    if (!result.records.length && result.total > 0 && page.value > 1) {
      await router.replace({ query: buildQuery({ page: undefined }) })
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

function buildQuery(next: Record<string, string | undefined>) {
  const query: Record<string, string> = {}
  for (const [key, value] of Object.entries({ ...route.query, ...next })) {
    if (typeof value === 'string' && value) query[key] = value
  }
  return query
}

function switchStatus(value: string) {
  void router.push({ query: buildQuery({ status: value || undefined, page: undefined }) })
}

function searchByOrderNo() {
  void router.push({ query: buildQuery({ orderNo: orderNoInput.value || undefined, page: undefined }) })
}

function reset() {
  orderNoInput.value = ''
  void router.replace({ query: {} })
}

function goPage(next: number) {
  if (next < 1 || next > pageCount.value) return
  void router.push({ query: buildQuery({ page: String(next) }) })
}

async function handlePay(orderNo: string) {
  if (settling.has(orderNo)) return
  settling.add(orderNo)
  try {
    await payOrder(orderNo)
    toast.success('支付成功')
    await load()
  } catch (cause) {
    toast.error(cause instanceof ApiError ? cause.message : '支付失败')
  } finally {
    settling.delete(orderNo)
  }
}

// 手动取消只受 settling 约束：autoCancelled 是自动路径的去重开关，套到这里会让
// 自动取消失败后的手动重试被静默吞掉，卡片永远停在「待支付 00:00」
function handleCancel(orderNo: string) {
  if (settling.has(orderNo)) return
  return runCancel(orderNo, '订单已取消')
}

function handleExpired(orderNo: string) {
  if (settling.has(orderNo) || autoCancelled.has(orderNo)) return
  autoCancelled.add(orderNo)
  return runCancel(orderNo, '订单已超时关闭')
}

async function runCancel(orderNo: string, okMessage: string) {
  settling.add(orderNo)
  try {
    await cancelOrder(orderNo)
    toast.success(okMessage)
    await load()
  } catch (cause) {
    toast.error(cause instanceof ApiError ? cause.message : '取消失败')
  } finally {
    settling.delete(orderNo)
  }
}
</script>

<template>
  <main class="mx-auto w-[min(1180px,calc(100vw-48px))] py-9 pb-22">
    <h1 class="mb-6 font-serif text-[27px] font-black">我的订单</h1>

    <div class="mb-5 flex flex-wrap items-center gap-3">
      <div class="relative max-w-100 flex-1 basis-full sm:basis-0">
        <Search
          class="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
        />
        <Input
          v-model="orderNoInput"
          placeholder="请输入订单号"
          class="h-[42px] pl-9"
          @keydown.enter="searchByOrderNo"
        />
      </div>

      <div class="flex gap-0.5 rounded-full border border-border bg-card p-[3px]">
        <button
          v-for="tab in STATUS_TABS"
          :key="tab.value"
          type="button"
          class="rounded-full px-4 py-1.5 text-sm font-semibold transition-colors"
          :class="
            status === tab.value ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'
          "
          @click="switchStatus(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>

      <Button class="h-[42px] rounded-full px-5" @click="searchByOrderNo">搜索</Button>
      <Button variant="outline" class="h-[42px] rounded-full border-border px-5" @click="reset">
        重置
      </Button>
    </div>

    <div v-if="loading" class="space-y-3">
      <Skeleton v-for="n in 2" :key="n" class="h-44 rounded-md" />
    </div>

    <div
      v-else-if="error"
      class="rounded-[20px] border-2 border-dashed border-border bg-card py-16 text-center"
    >
      <p class="text-[15px] font-bold">{{ error }}</p>
      <p class="mt-2 text-sm text-muted-foreground">稍后再试</p>
    </div>

    <Empty
      v-else-if="!records.length"
      class="rounded-[20px] border-2 border-dashed border-border bg-card py-16"
    >
      <EmptyHeader>
        <EmptyTitle>还没有符合条件的订单</EmptyTitle>
        <EmptyDescription>去挑一场活动吧</EmptyDescription>
      </EmptyHeader>
    </Empty>

    <div v-else class="space-y-3">
      <OrderCard
        v-for="order in records"
        :key="order.orderNo"
        :order="order"
        @pay="handlePay"
        @cancel="handleCancel"
        @expired="handleExpired"
      />

      <div
        v-if="pageCount > 1"
        class="flex items-center justify-center gap-3 pt-2 text-sm text-muted-foreground"
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
    </div>
  </main>
</template>
