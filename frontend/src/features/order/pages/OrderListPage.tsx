import { ActionButton, HStack, Text, VStack } from '@seed-design/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { orderApi } from '@/features/order/api/orderApi'
import { OrderItemSummaryCard } from '@/features/order/components/OrderItemSummaryCard'
import { ErrorView } from '@/shared/ui/ErrorView'
import { LoadingView } from '@/shared/ui/LoadingView'
import { StatusChip } from '@/shared/ui/StatusChip'
import { formatDateTime } from '@/shared/utils/date'
import { getErrorMessage } from '@/shared/utils/errors'

const getStatusSummary = (statuses: string[]) =>
  statuses.reduce<Record<string, number>>((acc, status) => {
    acc[status] = (acc[status] ?? 0) + 1
    return acc
  }, {})

export const OrderListPage = () => {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const ordersQuery = useQuery({
    queryKey: ['orders'],
    queryFn: orderApi.getOrders,
  })

  const cancelMutation = useMutation({
    mutationFn: (orderId: number) => orderApi.cancelOrder(orderId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['orders'] })
    },
  })

  if (ordersQuery.isLoading) {
    return <LoadingView message="주문 목록을 불러오는 중..." />
  }

  if (ordersQuery.isError) {
    return <ErrorView message={getErrorMessage(ordersQuery.error)} onRetry={() => void ordersQuery.refetch()} />
  }

  const orders = ordersQuery.data ?? []
  const summary = getStatusSummary(orders.map((order) => order.status))
  const isInteractiveTarget = (target: EventTarget | null) =>
    target instanceof Element && !!target.closest('a,button,input,textarea,select,label')

  return (
    <VStack gap="x5">
      <section className="rounded-r3 border border-stroke-neutral-muted bg-bg-layer-floating px-5 py-6">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t7Bold">내 주문</Text>
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            주문별 상세로 이동해 날짜/주문항목/결제정보를 확인하고 배송/환불 페이지로 이동할 수 있습니다.
          </Text>
          <HStack gap="x2" className="flex-wrap">
            <span className="rounded-r2 bg-bg-neutral-weak px-3 py-1 text-xs font-semibold text-fg-neutral-subtle">
              전체 {orders.length}
            </span>
            <span className="rounded-r2 bg-bg-brand-weak px-3 py-1 text-xs font-semibold text-fg-brand">
              결제 대기 {summary.CREATED ?? 0}
            </span>
            <span className="rounded-r2 bg-bg-positive-weak px-3 py-1 text-xs font-semibold text-fg-positive">
              결제 완료 {summary.PAID ?? 0}
            </span>
            <span className="rounded-r2 bg-bg-warning-weak px-3 py-1 text-xs font-semibold text-fg-warning">
              환불 진행 {summary.REFUND_REQUESTED ?? 0}
            </span>
          </HStack>
        </VStack>
      </section>

      {orders.length === 0 ? (
        <section className="rounded-r3 border border-dashed border-stroke-neutral-weak bg-bg-layer-floating px-6 py-10 text-center">
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            주문 내역이 없습니다.
          </Text>
        </section>
      ) : (
        <VStack gap="x3">
          {orders.map((order) => {
            return (
              <article
                key={order.id}
                role="button"
                tabIndex={0}
                aria-label={`주문 ${order.id} 상세로 이동`}
                className="cursor-pointer rounded-r3 border border-stroke-neutral-muted bg-bg-layer-floating p-5 transition-colors hover:bg-bg-layer-default focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-stroke-brand-solid"
                onClick={(event) => {
                  if (isInteractiveTarget(event.target)) {
                    return
                  }
                  navigate(`/me/orders/${order.id}`)
                }}
                onKeyDown={(event) => {
                  if (isInteractiveTarget(event.target)) {
                    return
                  }
                  if (event.key !== 'Enter' && event.key !== ' ') {
                    return
                  }
                  event.preventDefault()
                  navigate(`/me/orders/${order.id}`)
                }}
              >
                <VStack gap="x3" align="flex-start">
                  <HStack justify="space-between" align="flex-start" className="w-full gap-2">
                    <VStack gap="x1" align="flex-start" className="min-w-0 flex-1">
                      <Text textStyle="t5Bold">{formatDateTime(order.createdAt)}</Text>
                      <HStack gap="x2" align="center" className="flex-wrap">
                        <Link to={`/me/orders/${order.id}`}>
                          <Text textStyle="t4Medium" className="underline">
                            주문번호 {order.id}
                          </Text>
                        </Link>
                        <Text textStyle="t3Regular" color="fg.neutralSubtle">
                          주문 상품 {order.items.length}개
                        </Text>
                      </HStack>
                    </VStack>
                    <StatusChip status={order.status} />
                  </HStack>

                  <VStack gap="x2" align="flex-start" className="w-full rounded-r2 bg-bg-layer-default px-3 py-3">
                    {order.items.map((item) => (
                      <OrderItemSummaryCard key={item.id} item={item} />
                    ))}
                  </VStack>

                  <div className="grid w-full grid-cols-2 gap-2 sm:grid-cols-3">
                    <ActionButton asChild variant="neutralWeak" className="w-full">
                      <Link to={`/me/deliveries?orderId=${order.id}`}>배송 조회</Link>
                    </ActionButton>
                    <ActionButton asChild variant="neutralWeak" className="w-full">
                      <Link to={`/me/refunds?orderId=${order.id}`}>환불 페이지</Link>
                    </ActionButton>
                    {order.status === 'CREATED' ? (
                      <ActionButton asChild variant="brandSolid" className="w-full">
                        <Link to={`/checkout/${order.id}`}>결제하기</Link>
                      </ActionButton>
                    ) : (
                      <ActionButton variant="neutralWeak" className="w-full" disabled aria-label="결제 완료 또는 결제 불가 상태">
                        결제 완료/불가
                      </ActionButton>
                    )}
                  </div>

                  {order.status === 'CREATED' ? (
                    <ActionButton
                      variant="criticalSolid"
                      loading={cancelMutation.isPending && cancelMutation.variables === order.id}
                      disabled={cancelMutation.isPending}
                      onClick={() => cancelMutation.mutate(order.id)}
                    >
                      주문 취소
                    </ActionButton>
                  ) : null}
                </VStack>
              </article>
            )
          })}
        </VStack>
      )}

      {cancelMutation.isError ? (
        <Text textStyle="t4Regular" color="fg.critical">
          {getErrorMessage(cancelMutation.error)}
        </Text>
      ) : null}
    </VStack>
  )
}

