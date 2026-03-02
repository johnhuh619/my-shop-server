import { httpClient } from '@/shared/api/httpClient'
import { unwrapApiResponse } from '@/shared/api/unwrapApiResponse'
import type { ApiResponse } from '@/shared/types/api'
import type { DeliveryResponse, ProductResponse, RefundResponse, RefundStatus } from '@/shared/types/domain'

export interface AdminInventoryResponse {
  id: number
  productId: number
  quantityAvailable: number
  quantityReserved: number
  createdAt: string
  updatedAt: string
}

export interface AdminProductCreateRequest {
  name: string
  description: string
  unitPrice: number
}

export interface AdminProductUpdateRequest {
  name?: string
  description?: string
  unitPrice?: number
}

export interface AdminAddStockRequest {
  quantity: number
}

export interface AdminRefundDecisionRequest {
  comment?: string
}

export interface AdminCreateDeliveryRequest {
  orderId: number
  recipientName: string
  recipientPhone: string
  address: string
  addressDetail: string
  zipCode: string
}

export interface AdminShipDeliveryRequest {
  carrier: string
  trackingNumber: string
}

export const adminApi = {
  createProduct: async (payload: AdminProductCreateRequest) => {
    const { data } = await httpClient.post<ApiResponse<ProductResponse>>('/api/admin/products', payload)
    return unwrapApiResponse(data)
  },

  updateProduct: async (productId: number, payload: AdminProductUpdateRequest) => {
    const { data } = await httpClient.patch<ApiResponse<ProductResponse>>(`/api/admin/products/${productId}`, payload)
    return unwrapApiResponse(data)
  },

  deactivateProduct: async (productId: number) => {
    const { data } = await httpClient.post<ApiResponse<ProductResponse>>(`/api/admin/products/${productId}/deactivate`)
    return unwrapApiResponse(data)
  },

  getInventory: async (productId: number) => {
    const { data } = await httpClient.get<ApiResponse<AdminInventoryResponse>>(
      `/api/admin/inventories/${productId}`,
    )
    return unwrapApiResponse(data)
  },

  addStock: async (productId: number, payload: AdminAddStockRequest) => {
    const { data } = await httpClient.patch<ApiResponse<AdminInventoryResponse>>(
      `/api/admin/inventories/${productId}/add-stock`,
      payload,
    )
    return unwrapApiResponse(data)
  },

  completeOrder: async (orderId: number) => {
    const { data } = await httpClient.post<ApiResponse<null>>(`/api/admin/orders/${orderId}/complete`)
    return unwrapApiResponse(data)
  },

  getRefunds: async (status?: RefundStatus) => {
    const { data } = await httpClient.get<ApiResponse<RefundResponse[]>>('/api/admin/refunds', {
      params: status ? { status } : undefined,
    })
    return unwrapApiResponse(data)
  },

  getRefund: async (refundId: number) => {
    const { data } = await httpClient.get<ApiResponse<RefundResponse>>(`/api/admin/refunds/${refundId}`)
    return unwrapApiResponse(data)
  },

  approveRefund: async (refundId: number, payload?: AdminRefundDecisionRequest) => {
    const { data } = await httpClient.post<ApiResponse<RefundResponse>>(
      `/api/admin/refunds/${refundId}/approve`,
      payload,
    )
    return unwrapApiResponse(data)
  },

  rejectRefund: async (refundId: number, payload?: AdminRefundDecisionRequest) => {
    const { data } = await httpClient.post<ApiResponse<RefundResponse>>(
      `/api/admin/refunds/${refundId}/reject`,
      payload,
    )
    return unwrapApiResponse(data)
  },

  getDeliveries: async (status?: DeliveryResponse['status']) => {
    const { data } = await httpClient.get<ApiResponse<DeliveryResponse[]>>('/api/admin/deliveries', {
      params: status ? { status } : undefined,
    })
    return unwrapApiResponse(data)
  },

  getDelivery: async (deliveryId: number) => {
    const { data } = await httpClient.get<ApiResponse<DeliveryResponse>>(`/api/admin/deliveries/${deliveryId}`)
    return unwrapApiResponse(data)
  },

  createDelivery: async (payload: AdminCreateDeliveryRequest) => {
    const { data } = await httpClient.post<ApiResponse<DeliveryResponse>>('/api/admin/deliveries', payload)
    return unwrapApiResponse(data)
  },

  shipDelivery: async (deliveryId: number, payload: AdminShipDeliveryRequest) => {
    const { data } = await httpClient.post<ApiResponse<DeliveryResponse>>(
      `/api/admin/deliveries/${deliveryId}/ship`,
      payload,
    )
    return unwrapApiResponse(data)
  },

  markDeliveryInTransit: async (deliveryId: number) => {
    const { data } = await httpClient.post<ApiResponse<DeliveryResponse>>(
      `/api/admin/deliveries/${deliveryId}/in-transit`,
    )
    return unwrapApiResponse(data)
  },

  markDeliveryDelivered: async (deliveryId: number) => {
    const { data } = await httpClient.post<ApiResponse<DeliveryResponse>>(`/api/admin/deliveries/${deliveryId}/deliver`)
    return unwrapApiResponse(data)
  },

  cancelDelivery: async (deliveryId: number) => {
    const { data } = await httpClient.post<ApiResponse<DeliveryResponse>>(`/api/admin/deliveries/${deliveryId}/cancel`)
    return unwrapApiResponse(data)
  },
}
