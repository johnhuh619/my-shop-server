import axios from 'axios'
import { httpClient } from '@/shared/api/httpClient'
import { unwrapApiResponse } from '@/shared/api/unwrapApiResponse'
import type { ApiResponse } from '@/shared/types/api'
import type { DeliveryResponse } from '@/shared/types/domain'

export const deliveryApi = {
  getDeliveries: async () => {
    const { data } = await httpClient.get<ApiResponse<DeliveryResponse[]>>('/api/deliveries')
    return unwrapApiResponse(data)
  },

  getDeliveryByOrder: async (orderId: number): Promise<DeliveryResponse | null> => {
    try {
      const { data } = await httpClient.get<ApiResponse<DeliveryResponse>>(`/api/deliveries/order/${orderId}`)
      return unwrapApiResponse(data)
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null
      }
      throw error
    }
  },
}
