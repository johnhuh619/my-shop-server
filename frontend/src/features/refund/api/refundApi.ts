import { httpClient } from '@/shared/api/httpClient'
import { unwrapApiResponse } from '@/shared/api/unwrapApiResponse'
import type { ApiResponse } from '@/shared/types/api'
import type { CreateRefundRequest, RefundResponse } from '@/shared/types/domain'

export const refundApi = {
  createRefund: async (payload: CreateRefundRequest) => {
    const { data } = await httpClient.post<ApiResponse<RefundResponse>>('/api/refunds', payload)
    return unwrapApiResponse(data)
  },

  getRefunds: async () => {
    const { data } = await httpClient.get<ApiResponse<RefundResponse[]>>('/api/refunds')
    return unwrapApiResponse(data)
  },
}

