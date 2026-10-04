<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import EventPriceHeader from '@/components/business/EventPriceHeader.vue'
import SeatMap from '@/components/business/SeatMap.vue'
import { createOrder } from '@/api/order'
import { getEventMeta, getSeatDetail, getTicketDetail } from '@/api/event'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { Seat, SeatDetail, TicketDetail } from '@/types/event'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const eventId = computed(() => Number(route.params.id))
const loading = ref(true)
const ticket = ref<TicketDetail | null>(null)
const seatDetail = ref<SeatDetail | null>(null)
const picked = ref<Seat | null>(null)
const submitting = ref(false)
const error = ref('')

watch(eventId, load, { immediate: true })

async function load() {
  loading.value = true
  error.value = ''
  picked.value = null
  // 不清这两个 ref，从别的活动切过来且第二个请求失败时，会拿旧活动的数据渲染新 URL
  ticket.value = null
  seatDetail.value = null
  try {
    const meta = await getEventMeta(eventId.value)
    if (meta.mode === 1) {
      ticket.value = await getTicketDetail(eventId.value)
      seatDetail.value = null
    } else {
      seatDetail.value = await getSeatDetail(eventId.value)
      ticket.value = null
    }
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '加载失败'
    toast.error(error.value)
  } finally {
    loading.value = false
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/events')
}

function pick(seat: Seat) {
  picked.value = picked.value?.seatId === seat.seatId ? null : seat
}

async function buy() {
  if (!auth.isLoggedIn) {
    toast.info('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (seatDetail.value && !picked.value) {
    toast.error('请选择座位')
    return
  }

  submitting.value = true
  try {
    await createOrder({
      eventId: eventId.value,
      seatId: picked.value?.seatId,
    })
    toast.success('下单成功，请在 15 分钟内完成支付')
    router.push('/orders')
  } catch (error) {
    toast.error(error instanceof ApiError ? error.message : '下单失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="mx-auto w-[min(1180px,calc(100vw-48px))] py-9 pb-22">
    <Button variant="ghost" class="mb-4 h-auto gap-1.5 p-0 text-muted-foreground" @click="goBack">
      <ArrowLeft class="size-3.5" />
      返回
    </Button>

    <Skeleton v-if="loading" class="h-56 rounded-[20px]" />

    <div
      v-else-if="error"
      class="rounded-[20px] border-2 border-dashed border-border bg-card py-16 text-center"
    >
      <p class="text-[15px] font-bold">{{ error }}</p>
      <p class="mt-2 text-sm text-muted-foreground">稍后再试</p>
      <Button variant="outline" class="mt-4 h-9 rounded-full border-border px-4" @click="load">
        重新加载
      </Button>
    </div>

    <template v-else-if="ticket">
      <Card class="rounded-[20px] shadow-card">
        <CardContent class="p-6">
          <EventPriceHeader :name="ticket.name" :address="ticket.address" :price="ticket.price" />
          <div class="mt-5 flex items-center justify-between gap-4 border-t border-line-soft pt-5">
            <div>
              <p class="text-xs font-semibold text-muted-foreground">剩余票数</p>
              <p class="mt-0.5 font-serif text-3xl font-black text-brand-press tabular-nums">
                {{ ticket.stock }}
              </p>
            </div>
            <Button class="h-11 rounded-full px-6" :disabled="submitting" @click="buy">
              {{ submitting ? '提交中' : '立即购票' }}
            </Button>
          </div>
        </CardContent>
      </Card>
    </template>

    <template v-else-if="seatDetail">
      <Card class="rounded-[20px] shadow-card">
        <CardContent class="p-7">
          <EventPriceHeader
            :name="seatDetail.name"
            :address="seatDetail.address"
            :price="seatDetail.price"
          />

          <SeatMap
            :rows="seatDetail.rowCount"
            :cols="seatDetail.colCount"
            :seats="seatDetail.seats"
            :picked="picked?.seatId ?? null"
            class="mt-6"
            @pick="pick"
          />

          <div class="mt-6 flex flex-wrap items-center gap-3 border-t border-line-soft pt-5">
            <div
              v-if="picked"
              class="flex items-center rounded-sm bg-secondary px-3.5 py-2.5 text-sm font-bold text-secondary-foreground"
            >
              {{ picked.rowNo }} 排 {{ picked.colNo }} 座
            </div>
            <p v-else class="text-sm text-muted-foreground">请选择一个座位</p>
            <Button
              class="ml-auto h-11 rounded-full px-6"
              :disabled="submitting || !picked"
              @click="buy"
            >
              {{ submitting ? '提交中' : '确认购票' }}
            </Button>
          </div>
        </CardContent>
      </Card>
    </template>
  </main>
</template>
