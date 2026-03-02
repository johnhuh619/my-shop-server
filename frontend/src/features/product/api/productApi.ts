import { httpClient } from '@/shared/api/httpClient'
import { unwrapApiResponse } from '@/shared/api/unwrapApiResponse'
import type { ApiResponse, PageResponse } from '@/shared/types/api'
import type { ProductResponse } from '@/shared/types/domain'

interface ProductListParams {
  page?: number
  size?: number
  keyword?: string
}

export const productApi = {
  getProducts: async (params: ProductListParams) => {
    const { data } = await httpClient.get<ApiResponse<PageResponse<ProductResponse>>>('/api/products', {
      params,
    })
    return unwrapApiResponse(data)
  },

  getProduct: async (id: number) => {
    const { data } = await httpClient.get<ApiResponse<ProductResponse>>(`/api/products/${id}`)
    return unwrapApiResponse(data)
  },
}

