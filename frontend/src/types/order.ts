import type { PageResult } from './api'

export type OrderStatus = 0 | 1 | 2

export interface SeatPosition {
  rowNo: number
  colNo: number
}

export interface CreateOrderRequest {
  eventId: number
  seatId?: number
}

export interface CreateOrderResult {
  orderNo: string
  amount: number
  expireTime: string
  eventName: string
  eventAddress: string
  seat: SeatPosition | null
}

export interface OrderVO {
  orderNo: string
  eventId: number
  eventName: string
  eventAddress: string
  eventPrice: number
  amount: number
  status: OrderStatus
  seat: SeatPosition | null
  createTime: string
  expireTime: string
  payTime: string | null
}

export interface OrderQuery {
  orderNo?: string
  status?: OrderStatus
  page?: number
  size?: number
}

export type OrderPage = PageResult<OrderVO>
