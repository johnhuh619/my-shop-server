import { httpClient } from '@/shared/api/httpClient'
import { unwrapApiResponse } from '@/shared/api/unwrapApiResponse'
import type { ApiResponse } from '@/shared/types/api'
import type { CreateOrderRequest, OrderResponse } from '@/shared/types/domain'

export const orderApi = {
  createOrder: async (payload: CreateOrderRequest) => {
    const { data } = await httpClient.post<ApiResponse<OrderResponse>>('/api/orders', payload)
    return unwrapApiResponse(data)
  },

  getOrders: async () => {
    const { data } = await httpClient.get<ApiResponse<OrderResponse[]>>('/api/orders')
    return unwrapApiResponse(data)
  },

  getOrder: async (orderId: number) => {
    const { data } = await httpClient.get<ApiResponse<OrderResponse>>(`/api/orders/${orderId}`)
    return unwrapApiResponse(data)
  },

  cancelOrder: async (orderId: number) => {
    const { data } = await httpClient.patch<ApiResponse<OrderResponse>>(`/api/orders/${orderId}/cancel`)
    return unwrapApiResponse(data)
  },
}

