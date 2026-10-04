import dayjs from 'dayjs'
import type { OrderStatus } from '@/types/order'

const pad = (n: number) => String(n).padStart(2, '0')

// 后端返回 ISO 本地时间（无时区后缀）。Safari 对 `new Date('2026-10-03T15:03:00')`
// 的解析历史上有分歧，统一替换成空格分隔交给 dayjs。
export function parseServerTime(iso: string): number {
  return dayjs(iso.replace('T', ' ')).valueOf()
}

export function formatDateTime(iso: string | null): string {
  if (!iso) return '—'
  return dayjs(parseServerTime(iso)).format('YYYY-MM-DD HH:mm')
}

export function formatMoney(amount: number): string {
  return `¥${amount.toFixed(2)}`
}

export function formatCountdown(msLeft: number): string {
  if (msLeft <= 0) return '00:00'
  const totalSeconds = Math.floor(msLeft / 1000)
  return `${pad(Math.floor(totalSeconds / 60))}:${pad(totalSeconds % 60)}`
}

export const ORDER_STATUS_META: Record<OrderStatus, { label: string; badgeClass: string; accentClass: string }> = {
  0: { label: '待支付', badgeClass: 'bg-secondary text-secondary-foreground', accentClass: 'bg-primary' },
  1: { label: '已支付', badgeClass: 'bg-success/15 text-success', accentClass: 'bg-success' },
  2: { label: '已取消', badgeClass: 'bg-muted text-muted-foreground', accentClass: 'bg-border' },
}
