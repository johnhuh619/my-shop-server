import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { env } from '@/shared/config/env'
import { refreshAccessToken } from '@/shared/auth/refreshAccessToken'
import { sessionStore } from '@/shared/auth/sessionStore'

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

export const httpClient = axios.create({
  baseURL: env.apiBaseUrl,
  headers: {
    'Content-Type': 'application/json',
  },
})

httpClient.interceptors.request.use((config) => {
  const accessToken = sessionStore.getState().accessToken
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

httpClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const requestConfig = error.config as RetryableRequestConfig | undefined
    const status = error.response?.status

    if (!requestConfig || status !== 401 || requestConfig._retry) {
      return Promise.reject(error)
    }

    if (requestConfig.url?.includes('/api/auth/refresh')) {
      sessionStore.clear()
      return Promise.reject(error)
    }

    try {
      requestConfig._retry = true
      const newAccessToken = await refreshAccessToken()
      requestConfig.headers.Authorization = `Bearer ${newAccessToken}`
      return httpClient(requestConfig)
    } catch (refreshError) {
      sessionStore.clear()
      return Promise.reject(refreshError)
    }
  },
)

