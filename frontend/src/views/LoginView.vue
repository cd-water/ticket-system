<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { toast } from 'vue-sonner'
import { useIntervalFn } from '@vueuse/core'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuthStore } from '@/stores/auth'
import { sendCode } from '@/api/auth'
import { ApiError } from '@/api/http'

type Mode = 'sms' | 'pwd'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const mode = ref<Mode>('sms')
const submitting = ref(false)
const codeSent = ref(false)

// schema 必须跟着 mode 走：全局 .partial 会让未填的凭证「键缺失」而通过校验，
// 短信模式就能拿着空 code 打到后端，换成按模式切换后缺哪个就卡哪个。
const schema = computed(() =>
  toTypedSchema(
    z.object({
      phone: z.string().regex(/^1[3-9]\d{9}$/, '手机号格式不正确'),
      ...(mode.value === 'sms'
        ? { code: z.string({ error: '验证码为 6 位数字' }).regex(/^\d{6}$/, '验证码为 6 位数字') }
        : { password: z.string({ error: '密码至少 8 位' }).min(8, '密码至少 8 位') }),
    }),
  ),
)

const { handleSubmit, errors, defineField, resetField, validateField } = useForm({
  validationSchema: schema,
  initialValues: { phone: '' },
})

const [phone, phoneAttrs] = defineField('phone')
const [code, codeAttrs] = defineField('code')
const [password, passwordAttrs] = defineField('password')

const sending = ref(false)
const countdown = ref(0)
const { pause, resume } = useIntervalFn(
  () => {
    countdown.value -= 1
    if (countdown.value <= 0) pause()
  },
  1000,
  { immediate: false },
)

async function handleSendCode() {
  const { valid } = await validateField('phone')
  if (!valid) return
  sending.value = true
  try {
    await sendCode(phone.value ?? '')
    codeSent.value = true
    countdown.value = 60
    resume()
    toast.success('验证码已发送（Mock 固定为 123456）')
  } catch (error) {
    toast.error(error instanceof ApiError ? error.message : '发送失败')
  } finally {
    sending.value = false
  }
}

const targetPath = computed(() => {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
    ? redirect
    : '/events'
})

const onSubmit = handleSubmit(async (values) => {
  submitting.value = true
  try {
    if ('code' in values) await auth.loginSms(values.phone, values.code)
    else await auth.loginPwd(values.phone, values.password)
    toast.success('登录成功')
    router.push(targetPath.value)
  } catch (error) {
    toast.error(error instanceof ApiError ? error.message : '登录失败')
  } finally {
    submitting.value = false
  }
})

function switchMode(next: Mode) {
  mode.value = next
  codeSent.value = false
  resetField('code')
  resetField('password')
}
</script>

<template>
  <main class="grid min-h-dvh place-items-center px-6 py-12">
    <div class="w-full max-w-[400px] rounded-[20px] bg-card p-8 shadow-sm">
      <h1 class="text-center font-serif text-2xl font-black">Ticket-System</h1>
      <p class="mt-1 mb-6 text-center text-xl font-black">高并发票务系统</p>

      <div class="mb-5 flex gap-0.5 rounded-full bg-muted p-1">
        <button
          v-for="item in ([['sms', '验证码登录'], ['pwd', '密码登录']] as const)"
          :key="item[0]"
          type="button"
          class="flex-1 rounded-full py-2 text-sm font-bold transition-colors"
          :class="mode === item[0] ? 'bg-primary text-primary-foreground' : 'text-muted-foreground'"
          @click="switchMode(item[0])"
        >
          {{ item[1] }}
        </button>
      </div>

      <form class="space-y-3.5" novalidate @submit="onSubmit">
        <div>
          <Label for="phone" class="text-muted-foreground">手机号</Label>
          <Input id="phone" v-model="phone" v-bind="phoneAttrs" inputmode="numeric" autocomplete="tel" placeholder="请输入手机号" class="mt-1.5 h-11 bg-muted/40" :aria-invalid="!!errors.phone" :aria-describedby="errors.phone ? 'phone-error' : undefined" />
          <p v-if="errors.phone" id="phone-error" class="mt-1 text-xs text-destructive">{{ errors.phone }}</p>
        </div>

        <div v-if="mode === 'sms'">
          <Label for="code" class="text-muted-foreground">验证码</Label>
          <div class="mt-1.5 flex gap-2">
            <Input id="code" v-model="code" v-bind="codeAttrs" inputmode="numeric" maxlength="6" placeholder="请输入验证码" class="h-11 flex-1 bg-muted/40" :aria-invalid="!!errors.code" :aria-describedby="errors.code ? 'code-error' : undefined" />
            <Button
              type="button"
              variant="outline"
              class="h-11 shrink-0 border-border bg-muted/40 px-3.5 text-xs"
              :disabled="sending || countdown > 0"
              @click="handleSendCode"
            >
              {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
            </Button>
          </div>
          <p v-if="errors.code" id="code-error" class="mt-1 text-xs text-destructive">{{ errors.code }}</p>
        </div>

        <div v-else>
          <Label for="password" class="text-muted-foreground">密码</Label>
          <Input id="password" v-model="password" v-bind="passwordAttrs" type="password" autocomplete="current-password" placeholder="请输入密码" class="mt-1.5 h-11 bg-muted/40" :aria-invalid="!!errors.password" :aria-describedby="errors.password ? 'password-error' : undefined" />
          <p v-if="errors.password" id="password-error" class="mt-1 text-xs text-destructive">{{ errors.password }}</p>
        </div>

        <Button type="submit" class="h-11 w-full rounded-full" :disabled="submitting">
          {{ submitting ? '登录中' : '登录' }}
        </Button>
      </form>

      <p v-if="codeSent" class="mt-4 text-center text-xs text-muted-foreground">
        Mock 环境验证码固定为 <span class="font-bold text-primary-foreground">123456</span>
      </p>
    </div>
  </main>
</template>
