import type { AxiosError } from 'axios'
import type { ApiErrorPayload } from '@/shared/types/api'

export const getErrorMessage = (error: unknown) => {
  const axiosError = error as AxiosError<ApiErrorPayload>
  return (
    axiosError.response?.data?.errorMessage ||
    axiosError.response?.data?.errorCode ||
    axiosError.message ||
    '요청 처리 중 오류가 발생했습니다.'
  )
}

