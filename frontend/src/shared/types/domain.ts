export type UserRole = 'CUSTOMER' | 'ADMIN'
export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'DELETED'
export type ProductStatus = 'ACTIVE' | 'INACTIVE' | 'DELETED'
export type OrderStatus =
  | 'CREATED'
  | 'PAID'
  | 'COMPLETED'
  | 'CANCELED'
  | 'EXPIRED'
  | 'REFUND_REQUESTED'
  | 'REFUNDED'
export type PaymentStatus = 'REQUESTED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
export type RefundStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'FAILED'
export type DeliveryStatus = 'PREPARING' | 'SHIPPED' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELED'

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  userId: number
  email: string
  name: string
}

export interface RefreshTokenRequest {
  refreshToken: string
}

export interface RefreshTokenResponse {
  accessToken: string
}

export interface RegisterRequest {
  email: string
  password: string
  name: string
}

export interface UserResponse {
  id: number
  email: string
  name: string
  status: UserStatus
  createdAt: string
}

export interface ProductResponse {
  id: number
  name: string
  description: string
  unitPrice: number
  status: ProductStatus
  quantityAvailable: number | null
  createdAt: string
  updatedAt: string
}

export interface OrderItemRequest {
  productId: number
  quantity: number
}

export interface CreateOrderRequest {
  items: OrderItemRequest[]
  recipientName: string
  recipientPhone: string
  address: string
  addressDetail?: string
  zipCode: string
}

export interface OrderItemResponse {
  id: number
  productId: number
  productName: string
  unitPrice: number
  quantity: number
  subtotal: number
  refunded: boolean
}

export interface OrderResponse {
  id: number
  userId: number
  status: OrderStatus
  totalAmount: number
  items: OrderItemResponse[]
  deliveryStatus?: DeliveryStatus | null
  createdAt: string
  updatedAt: string
}

export interface CreatePaymentRequest {
  orderId: number
}

export interface PreparePaymentResponse {
  paymentId: number
  tossOrderId: string
  amount: number
  orderName: string
}

export interface ConfirmPaymentRequest {
  paymentKey: string
  orderId: string
  amount: number
}

export interface PaymentResponse {
  id: number
  userId: number
  orderId: number
  status: PaymentStatus
  amount: number
  tossOrderId: string
  paymentKey: string | null
  createdAt: string
  updatedAt: string
}

export interface RefundItemRequest {
  orderItemId: number
  quantity: number
}

export interface CreateRefundRequest {
  paymentId: number
  items: RefundItemRequest[]
  reason?: string
}

export interface RefundItemResponse {
  id: number
  orderItemId: number
  productId: number
  productName: string
  unitPrice: number
  quantity: number
  subtotal: number
}

export interface RefundResponse {
  id: number
  userId: number
  paymentId: number
  orderId: number
  status: RefundStatus
  amount: number
  reason: string | null
  adminComment: string | null
  items: RefundItemResponse[]
  createdAt: string
  updatedAt: string
}

export interface DeliveryResponse {
  id: number
  orderId: number
  userId: number
  status: DeliveryStatus
  recipientName: string
  recipientPhone: string
  address: string
  addressDetail: string
  zipCode: string
  carrier: string | null
  trackingNumber: string | null
  createdAt: string
  updatedAt: string
  shippedAt: string | null
  deliveredAt: string | null
}
