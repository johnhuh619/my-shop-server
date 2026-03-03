import axios from 'axios'
import { env } from '@/shared/config/env'

export const rawClient = axios.create({
  baseURL: env.apiBaseUrl,
  headers: {
    'Content-Type': 'application/json',
  },
})

