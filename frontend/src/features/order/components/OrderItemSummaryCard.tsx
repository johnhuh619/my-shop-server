import { ActionButton, Text, VStack } from '@seed-design/react'
import { Link } from 'react-router-dom'
import type { OrderItemResponse } from '@/shared/types/domain'
import { formatCurrency } from '@/shared/utils/format'

interface OrderItemSummaryCardProps {
  item: OrderItemResponse
  orderId?: number
  showRefundDetailAction?: boolean
}

export const OrderItemSummaryCard = ({ item, orderId, showRefundDetailAction = false }: OrderItemSummaryCardProps) => {
  return (
    <article className="w-full border-t border-stroke-neutral-muted pt-3 first:border-t-0 first:pt-0">
      <div className="flex w-full items-center gap-3">
        <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-r2 bg-bg-layer-default text-xs text-fg-neutral-subtle">
          상품
        </div>

        <div className="flex min-w-0 flex-1 items-center justify-between gap-3">
          <VStack gap="x1" align="flex-start" className="min-w-0 flex-1">
            <div className="flex items-center gap-1.5">
              <Text textStyle="t5Medium" className="line-clamp-2">
                {item.productName}
              </Text>
              {item.refunded ? (
                <span className="shrink-0 rounded-r1 bg-bg-informative-weak px-1.5 py-0.5 text-xs text-fg-informative">
                  환불 완료
                </span>
              ) : null}
            </div>
            <Text textStyle="t3Regular" color="fg.neutralSubtle">
              수량 {item.quantity}개 / 단가 {formatCurrency(item.unitPrice)}
            </Text>
            <Text textStyle="t5Bold">{formatCurrency(item.subtotal)}</Text>
          </VStack>

          <VStack gap="x1" align="flex-end" className="shrink-0">
            <ActionButton asChild variant="neutralWeak" size="small">
              <Link to={`/products/${item.productId}`}>재구매</Link>
            </ActionButton>
            {showRefundDetailAction && item.refunded && orderId ? (
              <ActionButton asChild variant="neutralWeak" size="small">
                <Link to={`/me/refunds?orderId=${orderId}`}>환불 내역</Link>
              </ActionButton>
            ) : null}
          </VStack>
        </div>
      </div>
    </article>
  )
}
