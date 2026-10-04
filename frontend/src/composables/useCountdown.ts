import { ref, toValue, watch } from 'vue'
import type { MaybeRefOrGetter } from 'vue'
import { useIntervalFn } from '@vueuse/core'
import { formatCountdown, parseServerTime } from '@/lib/format'

// enabled 为 false 时完全不启动：已支付 / 已取消的订单 expireTime 是过去时间，
// 若不关掉会立刻触发 onExpire，进而对已支付订单发出取消请求。
// 传入 getter 可在订单列表原地复用组件后随状态更新（v-for 按 orderNo 复用实例）。
export function useCountdown(expireTime: string, enabled: MaybeRefOrGetter<boolean>, onExpire?: () => void) {
  const text = ref('00:00')
  const expired = ref(false)

  const left = () => parseServerTime(expireTime) - Date.now()

  const { pause, resume } = useIntervalFn(
    () => {
      if (!toValue(enabled)) return
      const remaining = left()
      if (remaining > 0) {
        text.value = formatCountdown(remaining)
        return
      }
      text.value = '00:00'
      if (expired.value) return
      expired.value = true
      onExpire?.()
    },
    1000,
    // immediateCallback 让首次剩余量在挂载时即可算出，否则会先闪一帧 00:00
    { immediate: false, immediateCallback: true },
  )

  watch(
    () => toValue(enabled),
    (on) => {
      if (on) resume()
      else pause()
    },
    { immediate: true },
  )

  return { text, expired }
}
