export interface ApiResponse<T> {
  success: boolean
  data: T
  errorCode: string | null
  errorMessage: string | null
}

export interface ApiErrorPayload {
  success: false
  data: null
  errorCode: string
  errorMessage: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

