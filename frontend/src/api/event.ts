import { http, unwrap } from './http'
import type { Result } from '@/types/api'
import type { EventListPage, EventMeta, EventQuery, SeatDetail, TicketDetail } from '@/types/event'

export const listEvents = (params: EventQuery) =>
  http
    .get<never, { data: Result<EventListPage> }>('/events', { params })
    .then((r) => unwrap(r.data))

export const getEventMeta = (eventId: number) =>
  http
    .get<never, { data: Result<EventMeta> }>(`/events/${eventId}`)
    .then((r) => unwrap(r.data))

export const getTicketDetail = (eventId: number) =>
  http
    .get<never, { data: Result<TicketDetail> }>(`/events/${eventId}/ticket`)
    .then((r) => unwrap(r.data))

export const getSeatDetail = (eventId: number) =>
  http
    .get<never, { data: Result<SeatDetail> }>(`/events/${eventId}/seat`)
    .then((r) => unwrap(r.data))
