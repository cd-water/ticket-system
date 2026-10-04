<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ChevronDown, LogOut, KeyRound } from '@lucide/vue'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { toast } from 'vue-sonner'

const emit = defineEmits<{ changePassword: [] }>()

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const tabs = [
  { to: '/events', label: '抢票' },
  { to: '/seat', label: '选座' },
  { to: '/orders', label: '我的订单' },
] as const

const activeTab = computed(() => {
  if (route.path === '/seat') return '/seat'
  if (route.path === '/events') return '/events'
  if (route.path.startsWith('/events/')) return '/events'
  if (route.path === '/orders') return '/orders'
  return ''
})

async function handleLogout() {
  try {
    await auth.logout()
    toast.success('已退出登录')
  } catch {
    // store 已清本地登录态，请求失败也照常跳转
    toast.error('退出登录失败')
  } finally {
    router.push('/events')
  }
}
</script>

<template>
  <header class="sticky top-0 z-50 border-b border-border bg-background/95 backdrop-blur">
    <div class="mx-auto flex h-15 w-[min(1180px,calc(100vw-48px))] items-center gap-9 max-lg:gap-2">
      <RouterLink to="/events" class="flex shrink-0 items-center gap-2.5 no-underline max-sm:gap-2">
        <span class="size-3.5 rounded-[3px] bg-primary ring-4 ring-primary/25" />
        <span class="font-serif text-lg font-black tracking-tight text-foreground max-sm:text-base">
          Ticket-System
        </span>
      </RouterLink>

      <nav class="flex h-full min-w-0 flex-1 gap-1 overflow-x-auto max-sm:gap-0.5">
        <RouterLink
          v-for="tab in tabs"
          :key="tab.to"
          :to="tab.to"
          class="relative flex items-center px-4 text-sm font-semibold whitespace-nowrap transition-colors max-sm:px-2 max-sm:text-[13px]"
          :class="activeTab === tab.to ? 'text-foreground' : 'text-foreground/60 hover:text-foreground'"
        >
          {{ tab.label }}
          <span
            v-if="activeTab === tab.to"
            class="absolute inset-x-3 bottom-0 h-[3px] rounded-t-[3px] bg-primary max-sm:inset-x-2"
          />
        </RouterLink>
      </nav>

      <div class="ml-auto shrink-0">
        <Button
          v-if="!auth.isLoggedIn"
          class="rounded-full max-sm:h-8 max-sm:px-3 max-sm:text-[13px]"
          @click="router.push('/login')"
        >
          登录
        </Button>

        <DropdownMenu v-else>
          <DropdownMenuTrigger
            class="flex h-[34px] items-center gap-2 rounded-full border border-border bg-card px-3.5 text-sm font-bold text-foreground outline-none max-sm:px-2.5"
          >
            {{ auth.user?.phone }}
            <ChevronDown class="size-3.5" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" class="min-w-40">
            <DropdownMenuItem @click="emit('changePassword')">
              <KeyRound class="size-4" />
              修改密码
            </DropdownMenuItem>
            <DropdownMenuItem @click="handleLogout">
              <LogOut class="size-4" />
              退出登录
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  </header>
</template>
