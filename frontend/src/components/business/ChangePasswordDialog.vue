<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { changePassword } from '@/api/auth'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const auth = useAuthStore()
const router = useRouter()
const saving = ref(false)

const schema = toTypedSchema(
  z.object({ newPassword: z.string().min(8, '长度 8–20').max(20, '长度 8–20') }),
)

// 输入框自动获焦后立刻 blur，defineField 的 onBlur 走 validateField 默认的
// 'validated-only' 并先把 validated 置真，错误因此在用户动过之前就落进 errors；
// submitCount 是唯一能区分「用户提交过」与「被 blur 顺手校验过」的公开状态
const { handleSubmit, errors, submitCount, defineField, resetForm } = useForm({
  validationSchema: schema,
  initialValues: { newPassword: '' },
})

const [newPassword, passwordAttrs] = defineField('newPassword')

const showError = computed(() => submitCount.value > 0 && !!errors.value.newPassword)

watch(
  () => props.open,
  (value) => {
    if (!value) resetForm()
  },
)

const onSubmit = handleSubmit(async (values) => {
  saving.value = true
  try {
    await changePassword(values.newPassword)
    // 改密后旧 accessToken 仍能过校验，清登录态是接口文档对前端的硬要求
    auth.clear()
    emit('update:open', false)
    toast.success('密码已修改，请重新登录')
    router.push('/login')
  } catch (error) {
    toast.error(error instanceof ApiError ? error.message : '修改失败')
  } finally {
    saving.value = false
  }
})
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="rounded-[20px]" :aria-describedby="undefined">
      <DialogHeader>
        <DialogTitle class="font-serif font-black">修改密码</DialogTitle>
      </DialogHeader>

      <form class="space-y-4" novalidate @submit="onSubmit">
        <div>
          <Label for="newPassword" class="text-muted-foreground">新密码</Label>
          <Input
            id="newPassword"
            v-model="newPassword"
            v-bind="passwordAttrs"
            type="password"
            autocomplete="new-password"
            placeholder="请输入新密码"
            class="mt-1.5"
            :aria-invalid="!!errors.newPassword"
            :aria-describedby="showError ? 'newPassword-error' : undefined"
          />
          <p v-if="showError" id="newPassword-error" class="mt-1 text-xs text-destructive">
            {{ errors.newPassword }}
          </p>
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            class="rounded-full border-border"
            @click="emit('update:open', false)"
          >
            取消
          </Button>
          <Button type="submit" class="rounded-full" :disabled="saving">
            {{ saving ? '保存中' : '保存' }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
