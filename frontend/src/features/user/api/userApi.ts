import { httpClient } from '@/shared/api/httpClient'
import { unwrapApiResponse } from '@/shared/api/unwrapApiResponse'
import type { ApiResponse } from '@/shared/types/api'
import type { UserResponse } from '@/shared/types/domain'

export const userApi = {
  getMe: async () => {
    const { data } = await httpClient.get<ApiResponse<UserResponse>>('/api/users/me')
    return unwrapApiResponse(data)
  },

  deactivate: async () => {
    const { data } = await httpClient.patch<ApiResponse<UserResponse>>('/api/users/me/deactivate')
    return unwrapApiResponse(data)
  },
}

