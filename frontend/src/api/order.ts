import { http, unwrap } from './http'
import type { Result } from '@/types/api'
import type { CreateOrderRequest, CreateOrderResult, OrderPage, OrderQuery } from '@/types/order'

export const createOrder = (body: CreateOrderRequest) =>
  http
    .post<never, { data: Result<CreateOrderResult> }>('/orders', body)
    .then((r) => unwrap(r.data))

export const payOrder = (orderNo: string) =>
  http
    .post<never, { data: Result<null> }>('/pay', { orderNo })
    .then((r) => unwrap(r.data))

export const listOrders = (params: OrderQuery) =>
  http
    .get<never, { data: Result<OrderPage> }>('/orders', { params })
    .then((r) => unwrap(r.data))

export const cancelOrder = (orderNo: string) =>
  http
    .post<never, { data: Result<null> }>('/orders/cancel', { orderNo })
    .then((r) => unwrap(r.data))
