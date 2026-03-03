import { ActionButton, HStack, Text, VStack } from '@seed-design/react'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { paymentApi } from '@/features/payment/api/paymentApi'
import type { PaymentStatus } from '@/shared/types/domain'
import { ErrorView } from '@/shared/ui/ErrorView'
import { LoadingView } from '@/shared/ui/LoadingView'
import { StatusChip } from '@/shared/ui/StatusChip'
import { formatCurrency } from '@/shared/utils/format'
import { formatDateTime } from '@/shared/utils/date'
import { getErrorMessage } from '@/shared/utils/errors'

type PaymentFilter = 'ALL' | PaymentStatus

const paymentFilters: { key: PaymentFilter; label: string }[] = [
  { key: 'ALL', label: '전체' },
  { key: 'REQUESTED', label: '요청됨' },
  { key: 'PROCESSING', label: '처리중' },
  { key: 'COMPLETED', label: '완료' },
  { key: 'FAILED', label: '실패' },
]

export const PaymentListPage = () => {
  const [filter, setFilter] = useState<PaymentFilter>('ALL')

  const paymentsQuery = useQuery({
    queryKey: ['payments'],
    queryFn: paymentApi.getPayments,
  })

  if (paymentsQuery.isLoading) {
    return <LoadingView message="결제 내역을 불러오는 중..." />
  }

  if (paymentsQuery.isError) {
    return <ErrorView message={getErrorMessage(paymentsQuery.error)} onRetry={() => void paymentsQuery.refetch()} />
  }

  const payments = paymentsQuery.data ?? []
  const filteredPayments =
    filter === 'ALL' ? payments : payments.filter((payment) => payment.status === filter)

  return (
    <VStack gap="x5">
      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating px-5 py-6">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t7Bold">내 결제 내역</Text>
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            비동기 처리 상태를 포함해 결제 진행 상황을 확인할 수 있습니다.
          </Text>
          <HStack gap="x2" className="flex-wrap">
            {paymentFilters.map((item) => (
              <button
                key={item.key}
                type="button"
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
          </HStack>
        </VStack>
      </section>

      {filteredPayments.length === 0 ? (
        <section className="rounded-r3 border border-dashed border-stroke-neutral-weak bg-bg-layer-floating px-6 py-10 text-center">
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            {filter === 'ALL' ? '결제 내역이 없습니다.' : '해당 상태의 결제 내역이 없습니다.'}
          </Text>
        </section>
      ) : (
        <VStack gap="x3">
          {filteredPayments.map((payment) => (
            <article
              key={payment.id}
              className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating p-5"
            >
              <VStack gap="x3" align="flex-start">
                <HStack justify="space-between" align="center" className="w-full">
                  <Text textStyle="t5Bold">결제 #{payment.id}</Text>
                  <StatusChip status={payment.status} />
                </HStack>

                <Text textStyle="t4Regular">금액: {formatCurrency(payment.amount)}</Text>

                <VStack gap="x1" align="flex-start">
                  <Text textStyle="t4Regular" color="fg.neutralSubtle">
                    주문 ID: {payment.orderId}
                  </Text>
                  <Text textStyle="t4Regular" color="fg.neutralSubtle">
                    tossOrderId: {payment.tossOrderId}
                  </Text>
                  <Text textStyle="t4Regular" color="fg.neutralSubtle">
                    {formatDateTime(payment.createdAt)}
                  </Text>
                </VStack>

                {payment.status === 'REQUESTED' || payment.status === 'PROCESSING' ? (
                  <Text textStyle="t4Regular" color="fg.warning">
                    결제가 처리 중일 수 있습니다. 잠시 후 새로고침하거나 주문 상태를 다시 확인하세요.
                  </Text>
                ) : null}

                <HStack gap="x2" className="flex-wrap">
                  <Link to={`/me/orders/${payment.orderId}`}>
                    <ActionButton variant="neutralWeak">주문 상세</ActionButton>
                  </Link>
                  <Link to="/me/refunds">
                    <ActionButton variant="neutralWeak">환불 내역</ActionButton>
                  </Link>
                </HStack>
              </VStack>
            </article>
          ))}
        </VStack>
      )}
    </VStack>
  )
}

