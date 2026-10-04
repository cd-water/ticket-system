import { http, unwrap } from './http'
import type { LoginResult } from '@/types/user'
import type { Result } from '@/types/api'

export interface SmsLoginBody {
  phone: string
  code: string
}

export interface PwdLoginBody {
  phone: string
  password: string
}

export interface RefreshBody {
  refreshToken: string
}

export const sendCode = (phone: string) =>
  http.post<never, { data: Result<null> }>('/auth/send-code', { phone }).then((r) => unwrap(r.data))

export const smsLogin = (body: SmsLoginBody) =>
  http
    .post<never, { data: Result<LoginResult> }>('/auth/sms-login', body)
    .then((r) => unwrap(r.data))

export const pwdLogin = (body: PwdLoginBody) =>
  http
    .post<never, { data: Result<LoginResult> }>('/auth/pwd-login', body)
    .then((r) => unwrap(r.data))

export const refreshToken = (body: RefreshBody) =>
  http
    .post<never, { data: Result<LoginResult> }>('/auth/refresh', body)
    .then((r) => unwrap(r.data))

export const logout = (body: RefreshBody) =>
  http.post<never, { data: Result<null> }>('/auth/logout', body).then((r) => unwrap(r.data))

export const changePassword = (newPassword: string) =>
  http
    .post<never, { data: Result<null> }>('/auth/password', { newPassword })
    .then((r) => unwrap(r.data))
