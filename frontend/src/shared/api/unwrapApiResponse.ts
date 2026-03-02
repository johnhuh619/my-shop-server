import type { ApiResponse } from '@/shared/types/api'

export const unwrapApiResponse = <T>(response: ApiResponse<T>) => {
  if (!response.success || response.data === null || response.data === undefined) {
    throw new Error(response.errorMessage || response.errorCode || '응답 데이터가 없습니다.')
  }

  return response.data
}

