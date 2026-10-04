import { defineStore } from 'pinia'
import 'pinia-plugin-persistedstate'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import { AUTH_STORAGE_KEY } from '@/lib/constants'
import type { LoginResult, UserInfo } from '@/types/user'

export const useAuthStore = defineStore(
  'auth',
  () => {
    const user = ref<UserInfo | null>(null)
    const accessToken = ref('')
    const refreshToken = ref('')

    const isLoggedIn = computed(() => !!accessToken.value)

    function applySession(result: LoginResult) {
      user.value = result.userInfo
      accessToken.value = result.accessToken
      refreshToken.value = result.refreshToken
    }

    async function loginSms(phone: string, code: string) {
      applySession(await authApi.smsLogin({ phone, code }))
    }

    async function loginPwd(phone: string, password: string) {
      applySession(await authApi.pwdLogin({ phone, password }))
    }

    function clear() {
      user.value = null
      accessToken.value = ''
      refreshToken.value = ''
    }

    async function logout() {
      try {
        await authApi.logout({ refreshToken: refreshToken.value })
      } finally {
        clear()
      }
    }

    return { user, accessToken, refreshToken, isLoggedIn, applySession, loginSms, loginPwd, logout, clear }
  },
  { persist: { key: AUTH_STORAGE_KEY } },
)
