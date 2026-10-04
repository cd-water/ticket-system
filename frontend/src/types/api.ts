export interface Result<T> {
  code: string
  message: string
  data: T
}

export interface PageResult<T> {
  total: number
  records: T[]
  page: number
  size: number
}
