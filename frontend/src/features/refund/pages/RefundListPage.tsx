import { ActionButton, HStack, Text, VStack } from '@seed-design/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { orderApi } from '@/features/order/api/orderApi'
import { paymentApi } from '@/features/payment/api/paymentApi'
import { refundApi } from '@/features/refund/api/refundApi'
import type { RefundStatus } from '@/shared/types/domain'
import { ErrorView } from '@/shared/ui/ErrorView'
import { LoadingView } from '@/shared/ui/LoadingView'
import { StatusChip } from '@/shared/ui/StatusChip'
import { formatCurrency } from '@/shared/utils/format'
import { formatDateTime } from '@/shared/utils/date'
import { getErrorMessage } from '@/shared/utils/errors'

type RefundFilter = 'ALL' | RefundStatus

const refundFilters: { key: RefundFilter; label: string }[] = [
  { key: 'ALL', label: '전체' },
  { key: 'REQUESTED', label: '요청됨' },
  { key: 'APPROVED', label: '승인됨' },
  { key: 'REJECTED', label: '거절됨' },
  { key: 'COMPLETED', label: '완료' },
  { key: 'FAILED', label: '실패' },
]

export const RefundListPage = () => {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [selectedPaymentId, setSelectedPaymentId] = useState<number | null>(null)
  const [selectedOrderItemId, setSelectedOrderItemId] = useState<number | null>(null)
  const [quantityInput, setQuantityInput] = useState('1')
  const [reasonInput, setReasonInput] = useState('단순 변심')
  const [refundFilter, setRefundFilter] = useState<RefundFilter>('ALL')

  const orderIdParam = searchParams.get('orderId')
  const orderIdFilter = orderIdParam ? Number(orderIdParam) : null
  const hasOrderFilter = orderIdParam !== null && Number.isFinite(orderIdFilter)
  const hasInvalidOrderFilter = orderIdParam !== null && !Number.isFinite(orderIdFilter)

  const refundsQuery = useQuery({
    queryKey: ['refunds'],
    queryFn: refundApi.getRefunds,
    refetchInterval: 20_000,
    enabled: !hasInvalidOrderFilter,
  })

  const paymentsQuery = useQuery({
    queryKey: ['payments'],
    queryFn: paymentApi.getPayments,
    enabled: !hasInvalidOrderFilter,
  })

  const selectedPayment = (paymentsQuery.data ?? []).find((payment) => payment.id === selectedPaymentId)

  const orderQuery = useQuery({
    queryKey: ['order', selectedPayment?.orderId],
    queryFn: () => orderApi.getOrder(selectedPayment!.orderId),
    enabled: !!selectedPayment?.orderId,
  })

  const createRefundMutation = useMutation({
    mutationFn: () => {
      const quantity = Number(quantityInput)
      if (!selectedPaymentId || !selectedOrderItemId || Number.isNaN(quantity) || quantity <= 0) {
        throw new Error('환불 요청 입력값을 확인해주세요.')
      }

      return refundApi.createRefund({
        paymentId: selectedPaymentId,
        items: [{ orderItemId: selectedOrderItemId, quantity }],
        reason: reasonInput,
      })
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['refunds'] }),
        queryClient.invalidateQueries({ queryKey: ['payments'] }),
        selectedPayment?.orderId
          ? queryClient.invalidateQueries({ queryKey: ['order', selectedPayment.orderId] })
          : Promise.resolve(),
      ])
    },
  })

  if (hasInvalidOrderFilter) {
    return <ErrorView message="잘못된 주문 필터입니다. (orderId)" />
  }

  if (refundsQuery.isLoading || paymentsQuery.isLoading) {
    return <LoadingView message="환불 정보를 불러오는 중..." />
  }

  if (refundsQuery.isError) {
    return <ErrorView message={getErrorMessage(refundsQuery.error)} onRetry={() => void refundsQuery.refetch()} />
  }

  if (paymentsQuery.isError) {
    return <ErrorView message={getErrorMessage(paymentsQuery.error)} onRetry={() => void paymentsQuery.refetch()} />
  }

  if (orderQuery.isError) {
    return <ErrorView message={getErrorMessage(orderQuery.error)} onRetry={() => void orderQuery.refetch()} />
  }

  const refunds = refundsQuery.data ?? []
  const payablePayments = (paymentsQuery.data ?? []).filter(
    (payment) => payment.status === 'COMPLETED' && (!hasOrderFilter || payment.orderId === orderIdFilter),
  )
  const selectedOrderItems = orderQuery.data?.items ?? []
  const selectedOrderItem = selectedOrderItems.find((item) => item.id === selectedOrderItemId)
  const orderScopedRefunds = hasOrderFilter ? refunds.filter((refund) => refund.orderId === orderIdFilter) : refunds
  const filteredRefunds =
    refundFilter === 'ALL' ? orderScopedRefunds : orderScopedRefunds.filter((refund) => refund.status === refundFilter)

  return (
    <VStack gap="x5">
      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating px-5 py-6">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t7Bold">환불 관리</Text>
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            결제 선택 → 주문 아이템 선택 → 수량/사유 입력 순서로 환불 요청을 생성합니다.
          </Text>
          {hasOrderFilter ? (
            <HStack gap="x2" className="flex-wrap">
              <span className="rounded-r2 bg-bg-brand-weak px-3 py-1 text-xs font-semibold text-fg-brand">
                주문번호 {orderIdFilter} 기준
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

      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating p-5">
        <VStack gap="x3" align="flex-start">
          <Text textStyle="t5Bold">1) 결제 선택</Text>
          {payablePayments.length === 0 ? (
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              환불 가능한 결제(COMPLETED)가 없습니다.
            </Text>
          ) : (
            <VStack gap="x2" className="w-full">
              {payablePayments.map((payment) => (
                <HStack
                  key={payment.id}
                  justify="space-between"
                  align="center"
                  className="w-full rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default px-4 py-3"
                >
                  <VStack gap="x1" align="flex-start">
                    <Text textStyle="t4Medium">paymentId: {payment.id}</Text>
                    <Text textStyle="t4Regular" color="fg.neutralSubtle">
                      orderId: {payment.orderId} / {formatCurrency(payment.amount)}
                    </Text>
                  </VStack>
                  <ActionButton
                    variant={selectedPaymentId === payment.id ? 'neutralSolid' : 'neutralWeak'}
                    onClick={() => {
                      setSelectedPaymentId(payment.id)
                      setSelectedOrderItemId(null)
                    }}
                  >
                    {selectedPaymentId === payment.id ? '선택됨' : '선택'}
                  </ActionButton>
                </HStack>
              ))}
            </VStack>
          )}
        </VStack>
      </section>

      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating p-5">
        <VStack gap="x3" align="flex-start">
          <Text textStyle="t5Bold">2) 주문 아이템 선택</Text>
          {!selectedPayment ? (
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              먼저 결제를 선택하세요.
            </Text>
          ) : orderQuery.isLoading ? (
            <LoadingView message="주문 아이템을 불러오는 중..." />
          ) : selectedOrderItems.length === 0 ? (
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              선택한 결제의 주문 아이템이 없습니다.
            </Text>
          ) : (
            <VStack gap="x2" className="w-full">
              {selectedOrderItems.map((item) => (
                <HStack
                  key={item.id}
                  justify="space-between"
                  align="center"
                  className="w-full rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default px-4 py-3"
                >
                  <VStack gap="x1" align="flex-start">
                    <Text textStyle="t4Medium">{item.productName}</Text>
                    <Text textStyle="t4Regular" color="fg.neutralSubtle">
                      orderItemId: {item.id} / 최대 {item.quantity}개 / {formatCurrency(item.subtotal)}
                    </Text>
                  </VStack>
                  <ActionButton
                    variant={selectedOrderItemId === item.id ? 'neutralSolid' : 'neutralWeak'}
                    onClick={() => {
                      setSelectedOrderItemId(item.id)
                      setQuantityInput('1')
                    }}
                  >
                    {selectedOrderItemId === item.id ? '선택됨' : '선택'}
                  </ActionButton>
                </HStack>
              ))}
            </VStack>
          )}
        </VStack>
      </section>

      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating p-5">
        <VStack gap="x3" align="flex-start">
          <Text textStyle="t5Bold">3) 환불 요청 생성</Text>

          <label className="flex w-full flex-col gap-1">
            <span className="text-sm text-fg-neutral-subtle">quantity</span>
            <input
              className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default px-x3 py-x2"
              type="number"
              min={1}
              max={selectedOrderItem?.quantity ?? undefined}
              value={quantityInput}
              onChange={(event) => setQuantityInput(event.target.value)}
            />
          </label>

          <label className="flex w-full flex-col gap-1">
            <span className="text-sm text-fg-neutral-subtle">reason</span>
            <input
              className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default px-x3 py-x2"
              value={reasonInput}
              onChange={(event) => setReasonInput(event.target.value)}
            />
          </label>

          <ActionButton
            loading={createRefundMutation.isPending}
            disabled={createRefundMutation.isPending || !selectedPaymentId || !selectedOrderItemId}
            onClick={() => createRefundMutation.mutate()}
          >
            환불 요청 생성
          </ActionButton>

          {createRefundMutation.isError ? (
            <Text textStyle="t4Regular" color="fg.critical">
              {getErrorMessage(createRefundMutation.error)}
            </Text>
          ) : null}
        </VStack>
      </section>

      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating p-5">
        <VStack gap="x3" align="flex-start" className="w-full">
          <HStack justify="space-between" align="center" className="w-full">
            <Text textStyle="t5Bold">환불 내역</Text>
            <HStack gap="x2" className="flex-wrap">
              {refundFilters.map((item) => (
                <button
                  key={item.key}
                  type="button"
                  aria-pressed={refundFilter === item.key}
                  className={
                    refundFilter === item.key
                      ? 'rounded-r2 border border-stroke-brand-solid bg-bg-brand-weak px-3 py-1 text-xs font-semibold text-fg-brand'
                      : 'rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default px-3 py-1 text-xs font-medium text-fg-neutral-subtle'
                  }
                  onClick={() => setRefundFilter(item.key)}
                >
                  {item.label}
                </button>
              ))}
            </HStack>
          </HStack>

          {filteredRefunds.length === 0 ? (
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              환불 요청 내역이 없습니다.
            </Text>
          ) : (
            <VStack gap="x3" className="w-full">
              {filteredRefunds.map((refund) => (
                <VStack
                  key={refund.id}
                  gap="x2"
                  className="w-full rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default px-4 py-4"
                >
                  <HStack justify="space-between" align="center" className="w-full flex-wrap gap-2">
                    <Text textStyle="t6Bold">환불 #{refund.id}</Text>
                    <StatusChip status={refund.status} />
                  </HStack>
                  <Text textStyle="t4Regular">paymentId: {refund.paymentId}</Text>
                  <Text textStyle="t4Regular">금액: {formatCurrency(refund.amount)}</Text>
                  <Text textStyle="t4Regular" color="fg.neutralSubtle">
                    사유: {refund.reason ?? '-'}
                  </Text>
                  <Text textStyle="t4Regular" color="fg.neutralSubtle">
                    관리자 코멘트: {refund.adminComment ?? '-'}
                  </Text>
                  <Text textStyle="t4Regular" color="fg.neutralSubtle">
                    요청시각: {formatDateTime(refund.createdAt)}
                  </Text>
                </VStack>
              ))}
            </VStack>
          )}
        </VStack>
      </section>
    </VStack>
  )
}

