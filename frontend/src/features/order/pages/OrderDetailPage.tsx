import { ActionButton, HStack, Text, VStack } from '@seed-design/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { deliveryApi } from '@/features/delivery/api/deliveryApi'
import { orderApi } from '@/features/order/api/orderApi'
import { OrderItemSummaryCard } from '@/features/order/components/OrderItemSummaryCard'
import { paymentApi } from '@/features/payment/api/paymentApi'
import { ErrorView } from '@/shared/ui/ErrorView'
import { LoadingView } from '@/shared/ui/LoadingView'
import { StatusChip } from '@/shared/ui/StatusChip'
import { formatDateTime } from '@/shared/utils/date'
import { getErrorMessage } from '@/shared/utils/errors'
import { formatCurrency } from '@/shared/utils/format'

const formatOrderDateLabel = (iso: string) => {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return iso
  }

  const yy = String(date.getFullYear()).slice(2)
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const weekdays = ['일', '월', '화', '수', '목', '금', '토']
  return `${yy}.${mm}.${dd}(${weekdays[date.getDay()]})`
}

export const OrderDetailPage = () => {
  const { orderId } = useParams()
  const id = Number(orderId)
  const queryClient = useQueryClient()

  const orderQuery = useQuery({
    queryKey: ['order', id],
    queryFn: () => orderApi.getOrder(id),
    enabled: Number.isFinite(id),
  })

  const order = orderQuery.data
  const shouldFetchRelatedDetails =
    order?.status === 'PAID' ||
    order?.status === 'COMPLETED' ||
    order?.status === 'REFUND_REQUESTED' ||
    order?.status === 'REFUNDED'

  const paymentsQuery = useQuery({
    queryKey: ['payments'],
    queryFn: paymentApi.getPayments,
    enabled: Number.isFinite(id) && shouldFetchRelatedDetails,
  })

  const deliveryByOrderQuery = useQuery({
    queryKey: ['deliveryByOrder', id],
    queryFn: () => deliveryApi.getDeliveryByOrder(id),
    enabled: Number.isFinite(id) && shouldFetchRelatedDetails,
    retry: false,
  })

  const cancelMutation = useMutation({
    mutationFn: () => orderApi.cancelOrder(id),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['orders'] }),
        queryClient.invalidateQueries({ queryKey: ['order', id] }),
        queryClient.invalidateQueries({ queryKey: ['payments'] }),
        queryClient.invalidateQueries({ queryKey: ['deliveryByOrder', id] }),
      ])
    },
  })

  if (!Number.isFinite(id)) {
    return <ErrorView message="잘못된 주문 경로입니다." />
  }

  if (orderQuery.isLoading) {
    return <LoadingView message="주문 상세를 불러오는 중..." />
  }

  if (orderQuery.isError) {
    return <ErrorView message={getErrorMessage(orderQuery.error)} onRetry={() => void orderQuery.refetch()} />
  }

  if (!order) {
    return <LoadingView message="주문 상세를 불러오는 중..." />
  }

  const relatedPayments = (paymentsQuery.data ?? []).filter((payment) => payment.orderId === order.id)
  const latestPayment = [...relatedPayments].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  )[0]
  const relatedDelivery = deliveryByOrderQuery.data

  const showRefundDetailAction = order.status === 'REFUND_REQUESTED' || order.status === 'REFUNDED'

  return (
    <VStack gap="x3" className="w-full">
      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating px-4 py-5">
        <VStack gap="x2" align="flex-start" className="w-full">
          <HStack justify="space-between" align="flex-start" className="w-full">
            <VStack gap="x1" align="flex-start">
              <Text textStyle="t6Bold">주문 상세</Text>
              <Text textStyle="t5Bold">{formatOrderDateLabel(order.createdAt)}</Text>
              <Text textStyle="t4Regular" color="fg.neutralSubtle">
                주문번호 {order.id}
              </Text>
            </VStack>
            <StatusChip status={order.status} />
          </HStack>
        </VStack>
      </section>

      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating px-4 py-4">
        <VStack gap="x1" align="flex-start" className="w-full">
          <Text textStyle="t6Bold">배송지 정보</Text>
          {shouldFetchRelatedDetails && deliveryByOrderQuery.isLoading ? (
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              배송 정보를 불러오는 중...
            </Text>
          ) : null}
          {shouldFetchRelatedDetails && deliveryByOrderQuery.isError ? (
            <VStack gap="x2" align="flex-start">
              <Text textStyle="t4Regular" color="fg.critical">
                {getErrorMessage(deliveryByOrderQuery.error)}
              </Text>
              <ActionButton variant="neutralWeak" size="small" onClick={() => void deliveryByOrderQuery.refetch()}>
                다시 불러오기
              </ActionButton>
            </VStack>
          ) : null}

          {!shouldFetchRelatedDetails ? (
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              결제 완료 후 배송지 정보가 표시됩니다.
            </Text>
          ) : null}

          {shouldFetchRelatedDetails && !deliveryByOrderQuery.isLoading && !deliveryByOrderQuery.isError && !relatedDelivery ? (
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              배송 정보가 아직 등록되지 않았습니다.
            </Text>
          ) : null}

          {shouldFetchRelatedDetails && !deliveryByOrderQuery.isLoading && !deliveryByOrderQuery.isError && relatedDelivery ? (
            <>
              <Text textStyle="t5Medium">{relatedDelivery.recipientName}</Text>
              <Text textStyle="t4Regular" color="fg.neutralSubtle">
                {relatedDelivery.address} {relatedDelivery.addressDetail} ({relatedDelivery.zipCode})
              </Text>
              <Text textStyle="t4Regular" color="fg.neutralSubtle">
                {relatedDelivery.recipientPhone}
              </Text>
            </>
          ) : null}
        </VStack>
      </section>

      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating px-4 py-4">
        <VStack gap="x3" align="flex-start" className="w-full">
          <Text textStyle="t6Bold">주문 상품 {order.items.length}개</Text>

          {order.items.map((item) => (
            <OrderItemSummaryCard
              key={item.id}
              item={item}
              orderId={order.id}
              showRefundDetailAction={showRefundDetailAction}
            />
          ))}
        </VStack>
      </section>

      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating px-4 py-4">
        <VStack gap="x3" align="flex-start" className="w-full">
          <Text textStyle="t6Bold">결제 정보</Text>

          {!shouldFetchRelatedDetails ? (
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              결제 완료 후 상세 결제 정보가 표시됩니다.
            </Text>
          ) : null}

          {shouldFetchRelatedDetails && paymentsQuery.isLoading ? (
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              결제 정보를 불러오는 중...
            </Text>
          ) : null}

          {shouldFetchRelatedDetails && paymentsQuery.isError ? (
            <VStack gap="x2" align="flex-start">
              <Text textStyle="t4Regular" color="fg.critical">
                {getErrorMessage(paymentsQuery.error)}
              </Text>
              <ActionButton variant="neutralWeak" size="small" onClick={() => void paymentsQuery.refetch()}>
                다시 불러오기
              </ActionButton>
            </VStack>
          ) : null}

          {shouldFetchRelatedDetails && !paymentsQuery.isLoading && !paymentsQuery.isError && !latestPayment ? (
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              결제 정보가 아직 없습니다.
            </Text>
          ) : null}

          {shouldFetchRelatedDetails && !paymentsQuery.isLoading && !paymentsQuery.isError && latestPayment ? (
            <div className="w-full rounded-r2 bg-bg-layer-default px-3 py-3">
              <VStack gap="x2" align="flex-start" className="w-full">
                <div className="flex w-full items-center justify-between gap-2">
                  <Text textStyle="t4Regular" color="fg.neutralSubtle">
                    결제 상태
                  </Text>
                  <StatusChip status={latestPayment.status} />
                </div>
                <div className="flex w-full items-center justify-between gap-2 border-t border-stroke-neutral-subtle pt-2">
                  <Text textStyle="t4Regular" color="fg.neutralSubtle">
                    상품 금액
                  </Text>
                  <Text textStyle="t4Medium">{formatCurrency(order.totalAmount)}</Text>
                </div>
                <div className="flex w-full items-center justify-between gap-2 border-t border-stroke-neutral-subtle pt-2">
                  <Text textStyle="t4Regular" color="fg.neutralSubtle">
                    결제 금액
                  </Text>
                  <Text textStyle="t5Bold">{formatCurrency(latestPayment.amount)}</Text>
                </div>
                <div className="flex w-full items-center justify-between gap-2 border-t border-stroke-neutral-subtle pt-2">
                  <Text textStyle="t3Regular" color="fg.neutralSubtle">
                    결제 일시
                  </Text>
                  <Text textStyle="t3Regular" color="fg.neutralSubtle">
                    {formatDateTime(latestPayment.createdAt)}
                  </Text>
                </div>
              </VStack>
            </div>
          ) : null}
        </VStack>
      </section>

      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating px-4 py-4">
        <VStack gap="x2" align="flex-start" className="w-full">
          <Text textStyle="t6Bold">주문 액션</Text>
          <div className="grid w-full grid-cols-2 gap-2">
            <ActionButton asChild variant="neutralWeak" className="w-full">
              <Link to={`/me/deliveries?orderId=${order.id}`}>배송 조회</Link>
            </ActionButton>
            <ActionButton asChild variant="neutralWeak" className="w-full">
              <Link to={`/me/refunds?orderId=${order.id}`}>환불 페이지</Link>
            </ActionButton>
            <ActionButton asChild variant="neutralWeak" className="w-full">
              <Link to="/me/orders">목록으로</Link>
            </ActionButton>
            {order.status === 'CREATED' ? (
              <ActionButton asChild className="w-full">
                <Link to={`/checkout/${order.id}`}>결제하기</Link>
              </ActionButton>
            ) : (
              <ActionButton variant="neutralWeak" disabled className="w-full" aria-label="결제 완료 또는 결제 불가 상태">
                결제 완료/불가
              </ActionButton>
            )}
          </div>

          {order.status === 'CREATED' ? (
            <ActionButton
              variant="criticalSolid"
              loading={cancelMutation.isPending}
              disabled={cancelMutation.isPending}
              onClick={() => cancelMutation.mutate()}
            >
              주문 취소
            </ActionButton>
          ) : null}

          <ActionButton variant="neutralWeak" size="small" disabled aria-label="주문 내역 삭제 기능 준비중">
            주문 내역 삭제 (준비중)
          </ActionButton>
        </VStack>
      </section>

      {cancelMutation.isError ? (
        <Text textStyle="t4Regular" color="fg.critical">
          {getErrorMessage(cancelMutation.error)}
        </Text>
      ) : null}
    </VStack>
  )
}
