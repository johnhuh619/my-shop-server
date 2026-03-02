import { ActionButton, HStack, Text, VStack } from '@seed-design/react'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { deliveryApi } from '@/features/delivery/api/deliveryApi'
import type { DeliveryStatus } from '@/shared/types/domain'
import { ErrorView } from '@/shared/ui/ErrorView'
import { LoadingView } from '@/shared/ui/LoadingView'
import { StatusChip } from '@/shared/ui/StatusChip'
import { formatDateTime } from '@/shared/utils/date'
import { getErrorMessage } from '@/shared/utils/errors'

type DeliveryFilter = 'ALL' | DeliveryStatus

const deliveryFilters: { key: DeliveryFilter; label: string }[] = [
  { key: 'ALL', label: '전체' },
  { key: 'PREPARING', label: '준비중' },
  { key: 'SHIPPED', label: '발송됨' },
  { key: 'IN_TRANSIT', label: '배송중' },
  { key: 'DELIVERED', label: '배송완료' },
  { key: 'CANCELED', label: '취소' },
]

export const DeliveryListPage = () => {
  const [filter, setFilter] = useState<DeliveryFilter>('ALL')
  const [searchParams, setSearchParams] = useSearchParams()

  const orderIdParam = searchParams.get('orderId')
  const parsedOrderId = orderIdParam ? Number(orderIdParam) : null
  const hasOrderFilter = orderIdParam !== null && Number.isFinite(parsedOrderId)
  const hasInvalidOrderFilter = orderIdParam !== null && !Number.isFinite(parsedOrderId)

  const deliveriesQuery = useQuery({
    queryKey: ['deliveries'],
    queryFn: deliveryApi.getDeliveries,
    refetchInterval: 15_000,
    enabled: !hasOrderFilter && !hasInvalidOrderFilter,
  })

  const deliveryByOrderQuery = useQuery({
    queryKey: ['deliveryByOrder', parsedOrderId],
    queryFn: () => deliveryApi.getDeliveryByOrder(parsedOrderId!),
    enabled: hasOrderFilter,
    refetchInterval: 15_000,
    retry: false,
  })

  if (hasInvalidOrderFilter) {
    return <ErrorView message="잘못된 주문 필터입니다. (orderId)" />
  }

  if (hasOrderFilter && deliveryByOrderQuery.isLoading) {
    return <LoadingView message="배송 정보를 불러오는 중..." />
  }

  if (!hasOrderFilter && deliveriesQuery.isLoading) {
    return <LoadingView message="배송 정보를 불러오는 중..." />
  }

  if (hasOrderFilter && deliveryByOrderQuery.isError) {
    return <ErrorView message={getErrorMessage(deliveryByOrderQuery.error)} onRetry={() => void deliveryByOrderQuery.refetch()} />
  }

  if (!hasOrderFilter && deliveriesQuery.isError) {
    return <ErrorView message={getErrorMessage(deliveriesQuery.error)} onRetry={() => void deliveriesQuery.refetch()} />
  }

  const deliveries = hasOrderFilter
    ? deliveryByOrderQuery.data
      ? [deliveryByOrderQuery.data]
      : []
    : deliveriesQuery.data ?? []

  const filteredDeliveries =
    filter === 'ALL' ? deliveries : deliveries.filter((delivery) => delivery.status === filter)

  return (
    <VStack gap="x5">
      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating px-5 py-6">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t7Bold">배송 조회</Text>
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            배송 상태는 15초 간격으로 자동 갱신됩니다.
          </Text>
          <HStack gap="x2" className="flex-wrap">
            {deliveryFilters.map((item) => (
              <button
                key={item.key}
                type="button"
                aria-pressed={filter === item.key}
                className={
                  filter === item.key
                    ? 'rounded-r2 border border-stroke-brand-solid bg-bg-brand-weak px-3 py-1 text-xs font-semibold text-fg-brand'
                    : 'rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default px-3 py-1 text-xs font-medium text-fg-neutral-subtle'
                }
                onClick={() => setFilter(item.key)}
              >
                {item.label}
              </button>
            ))}
            <ActionButton
              variant="neutralWeak"
              size="xsmall"
              onClick={() => {
                if (hasOrderFilter) {
                  void deliveryByOrderQuery.refetch()
                } else {
                  void deliveriesQuery.refetch()
                }
              }}
            >
              수동 새로고침
            </ActionButton>
          </HStack>

          {hasOrderFilter ? (
            <HStack gap="x2" className="flex-wrap">
              <span className="rounded-r2 bg-bg-brand-weak px-3 py-1 text-xs font-semibold text-fg-brand">
                주문번호 {parsedOrderId} 기준
              </span>
              <ActionButton
                variant="neutralWeak"
                size="xsmall"
                onClick={() => {
                  const next = new URLSearchParams(searchParams)
                  next.delete('orderId')
                  setSearchParams(next)
                }}
              >
                필터 해제
              </ActionButton>
            </HStack>
          ) : null}
        </VStack>
      </section>

      {filteredDeliveries.length === 0 ? (
        <section className="rounded-r3 border border-dashed border-stroke-neutral-weak bg-bg-layer-floating px-6 py-10 text-center">
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            배송 정보가 없습니다.
          </Text>
        </section>
      ) : (
        <VStack gap="x3">
          {filteredDeliveries.map((delivery) => (
            <article
              key={delivery.id}
              className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating p-5"
            >
              <VStack gap="x3" align="flex-start">
                <HStack justify="space-between" align="center" className="w-full flex-wrap gap-2">
                  <Text textStyle="t5Bold">배송 #{delivery.id}</Text>
                  <StatusChip status={delivery.status} />
                </HStack>

                <VStack gap="x1" align="flex-start">
                  <Text textStyle="t4Regular" color="fg.neutralSubtle">
                    주문 ID: {delivery.orderId}
                  </Text>
                  <Text textStyle="t4Regular">수령인: {delivery.recipientName}</Text>
                  <Text textStyle="t4Regular">연락처: {delivery.recipientPhone}</Text>
                  <Text textStyle="t4Regular">
                    주소: {delivery.address} {delivery.addressDetail}
                  </Text>
                  <Text textStyle="t4Regular">우편번호: {delivery.zipCode}</Text>
                </VStack>

                <VStack gap="x1" align="flex-start">
                  <Text textStyle="t4Regular">택배사: {delivery.carrier ?? '-'}</Text>
                  <Text textStyle="t4Regular">운송장: {delivery.trackingNumber ?? '-'}</Text>
                  <Text textStyle="t4Regular" color="fg.neutralSubtle">
                    출고: {formatDateTime(delivery.shippedAt)}
                  </Text>
                  <Text textStyle="t4Regular" color="fg.neutralSubtle">
                    배송완료: {formatDateTime(delivery.deliveredAt)}
                  </Text>
                </VStack>

                {(delivery.status === 'PREPARING' || delivery.status === 'SHIPPED' || delivery.status === 'IN_TRANSIT') ? (
                  <Text textStyle="t4Regular" color="fg.warning">
                    배송 상태는 비동기 처리될 수 있으니 최신 상태를 주기적으로 확인하세요.
                  </Text>
                ) : null}

                <HStack gap="x2" className="flex-wrap">
                  <ActionButton asChild variant="neutralWeak">
                    <Link to={`/me/orders/${delivery.orderId}`}>주문 상세</Link>
                  </ActionButton>
                  <ActionButton asChild variant="neutralWeak">
                    <Link to={`/me/refunds?orderId=${delivery.orderId}`}>환불 내역</Link>
                  </ActionButton>
                </HStack>
              </VStack>
            </article>
          ))}
        </VStack>
      )}
    </VStack>
  )
}

