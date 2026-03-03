import { Text } from '@seed-design/react'
import type { DeliveryStatus, OrderStatus, PaymentStatus, RefundStatus } from '@/shared/types/domain'

type Status = OrderStatus | PaymentStatus | RefundStatus | DeliveryStatus

const statusColorClassName: Record<Status, string> = {
  CREATED: 'bg-bg-informative-weak text-fg-informative',
  PAID: 'bg-bg-positive-weak text-fg-positive',
  COMPLETED: 'bg-bg-positive-weak text-fg-positive',
  CANCELED: 'bg-bg-neutral-weak text-fg-neutral-subtle',
  EXPIRED: 'bg-bg-critical-weak text-fg-critical',
  REFUND_REQUESTED: 'bg-bg-informative-weak text-fg-informative',
  REFUNDED: 'bg-bg-positive-weak text-fg-positive',
  REQUESTED: 'bg-bg-informative-weak text-fg-informative',
  PROCESSING: 'bg-bg-warning-weak text-fg-warning',
  FAILED: 'bg-bg-critical-weak text-fg-critical',
  APPROVED: 'bg-bg-informative-weak text-fg-informative',
  REJECTED: 'bg-bg-critical-weak text-fg-critical',
  PREPARING: 'bg-bg-informative-weak text-fg-informative',
  SHIPPED: 'bg-bg-informative-weak text-fg-informative',
  IN_TRANSIT: 'bg-bg-warning-weak text-fg-warning',
  DELIVERED: 'bg-bg-positive-weak text-fg-positive',
}

export const StatusChip = ({ status }: { status: Status }) => (
  <Text as="span" textStyle="t3Regular" className={`rounded-full px-x2 py-x1 ${statusColorClassName[status]}`}>
    {status}
  </Text>
)


