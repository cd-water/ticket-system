import type { PageResult } from './api'

export type EventMode = 1 | 2

export interface EventCard {
  id: number
  name: string
  address: string
  price: number
}

export interface EventQuery {
  mode: EventMode
  keyword?: string
  page?: number
  size?: number
}

export interface TicketDetail {
  id: number
  name: string
  address: string
  price: number
  stock: number
}

export type SeatStatus = 0 | 1

export interface Seat {
  rowNo: number
  colNo: number
  seatId: number
  status: SeatStatus
}

export interface SeatDetail {
  id: number
  name: string
  address: string
  price: number
  rowCount: number
  colCount: number
  seats: Seat[]
}

export interface EventMeta {
  id: number
  name: string
  address: string
  price: number
  mode: EventMode
}

export type EventListPage = PageResult<EventCard>
