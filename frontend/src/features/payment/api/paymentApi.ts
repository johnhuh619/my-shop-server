import { httpClient } from '@/shared/api/httpClient'
import { unwrapApiResponse } from '@/shared/api/unwrapApiResponse'
import type { ApiResponse } from '@/shared/types/api'
import type {
  ConfirmPaymentRequest,
  CreatePaymentRequest,
  PaymentResponse,
  PreparePaymentResponse,
} from '@/shared/types/domain'

export const paymentApi = {
  preparePayment: async (payload: CreatePaymentRequest, idempotencyKey: string) => {
    const { data } = await httpClient.post<ApiResponse<PreparePaymentResponse>>('/api/payments', payload, {
      headers: {
        'X-Idempotency-Key': idempotencyKey,
      },
    })
    return unwrapApiResponse(data)
  },

  confirmPayment: async (payload: ConfirmPaymentRequest) => {
    const { data } = await httpClient.post<ApiResponse<PaymentResponse>>('/api/payments/confirm', payload)
    return unwrapApiResponse(data)
  },

  getPayments: async () => {
    const { data } = await httpClient.get<ApiResponse<PaymentResponse[]>>('/api/payments')
    return unwrapApiResponse(data)
  },

  getPayment: async (paymentId: number) => {
    const { data } = await httpClient.get<ApiResponse<PaymentResponse>>(`/api/payments/${paymentId}`)
    return unwrapApiResponse(data)
  },
}

