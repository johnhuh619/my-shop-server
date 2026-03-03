import { httpClient } from '@/shared/api/httpClient'
import { unwrapApiResponse } from '@/shared/api/unwrapApiResponse'
import type { ApiResponse } from '@/shared/types/api'
import type {
  LoginRequest,
  LoginResponse,
  RefreshTokenResponse,
  RegisterRequest,
  UserResponse,
} from '@/shared/types/domain'

export const authApi = {
  login: async (payload: LoginRequest) => {
    const { data } = await httpClient.post<ApiResponse<LoginResponse>>('/api/auth/login', payload)
    return unwrapApiResponse(data)
  },

  refresh: async (refreshToken: string) => {
    const { data } = await httpClient.post<ApiResponse<RefreshTokenResponse>>('/api/auth/refresh', {
      refreshToken,
    })
    return unwrapApiResponse(data)
  },

  logout: async (refreshToken: string | null) => {
    await httpClient.post<ApiResponse<null>>('/api/auth/logout', {
      refreshToken,
    })
  },

  register: async (payload: RegisterRequest) => {
    const { data } = await httpClient.post<ApiResponse<UserResponse>>('/api/users/register', payload)
    return unwrapApiResponse(data)
  },
}

