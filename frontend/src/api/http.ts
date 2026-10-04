import axios, { type AxiosInstance } from 'axios'
import { AUTH_STORAGE_KEY } from '@/lib/constants'
import type { Result } from '@/types/api'

export class ApiError extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

export function unwrap<T>(body: Result<T>): T {
  if (body.code === 'A200') return body.data
  throw new ApiError(body.code, body.message)
}

export const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 拦截器直接读 localStorage 而非 auth store：http.ts 若 import store，
// 而 store 又要 import api/*，会构成循环依赖。
http.interceptors.request.use((config) => {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    if (raw) {
      const { accessToken } = JSON.parse(raw) as { accessToken?: string }
      if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
    }
  } catch {
    // 缓存损坏时按未登录处理
  }
  return config
})

// 登录态失效（C401）清空持久化登录态并跳登录页。同样不 import store / router，
// 避免 http.ts ↔ stores / router ↔ 视图的循环依赖——store 的持久化 key 就是
// AUTH_STORAGE_KEY，内存态随整页跳转一并丢弃。
// 契约里 C401 走 HTTP 200 的响应体，故成功臂也要判；错误臂兼容未来 JWT 过滤器直接回 401。
// 不做并发 401 的 refresh 重放：清态跳登录已足够，重放要引入单飞与重试，得不偿失。
function handleUnauthorized() {
  try {
    localStorage.removeItem(AUTH_STORAGE_KEY)
  } catch {
    // 缓存损坏时按未登录处理
  }
  if (window.location.pathname !== '/login') {
    window.location.assign(
      `/login?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`,
    )
  }
}

http.interceptors.response.use(
  (response) => {
    if ((response.data as Result<unknown> | undefined)?.code === 'C401') handleUnauthorized()
    return response
  },
  (error) => {
    if (
      error.response?.status === 401 ||
      (error.response?.data as Result<unknown> | undefined)?.code === 'C401'
    ) {
      handleUnauthorized()
    }
    return Promise.reject(error)
  },
)
