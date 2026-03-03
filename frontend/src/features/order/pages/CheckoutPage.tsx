import { ActionButton, HStack, Text, VStack } from '@seed-design/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { orderApi } from '@/features/order/api/orderApi'
import { paymentApi } from '@/features/payment/api/paymentApi'
import { useAuth } from '@/shared/auth/useAuth'
import { env } from '@/shared/config/env'
import { openTossPaymentWindow } from '@/shared/payment/tossPaymentWindow'
import type { ConfirmPaymentRequest, OrderStatus } from '@/shared/types/domain'
import { ErrorView } from '@/shared/ui/ErrorView'
import { LoadingView } from '@/shared/ui/LoadingView'
import { StatusChip } from '@/shared/ui/StatusChip'
import { getErrorMessage } from '@/shared/utils/errors'
import { formatCurrency } from '@/shared/utils/format'

const createIdempotencyKey = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }

  return `checkout-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

const getOrCreateIdempotencyKey = (orderId: number): string => {
  const storageKey = `idempotency-key-${orderId}`
  const existing = sessionStorage.getItem(storageKey)
  if (existing) return existing

  const key = createIdempotencyKey()
  sessionStorage.setItem(storageKey, key)
  return key
}

const getOrCreateTossCustomerKey = () => {
  const storageKey = 'toss-customer-key'
  const existing = sessionStorage.getItem(storageKey)
  if (existing) return existing

  const key = `customer-${createIdempotencyKey()}`
  sessionStorage.setItem(storageKey, key)
  return key
}

const getNonPayableStatusMessage = (status: OrderStatus) => {
  switch (status) {
    case 'EXPIRED':
      return '만료된 주문입니다. 새로운 주문을 생성한 뒤 다시 결제를 진행해주세요.'
    case 'CANCELED':
      return '취소된 주문입니다. 이 주문에서는 결제를 진행할 수 없습니다.'
    case 'REFUND_REQUESTED':
      return '환불 요청이 접수된 주문입니다. 결제 재진행은 지원되지 않습니다.'
    case 'REFUNDED':
      return '이미 환불 처리된 주문입니다. 결제를 다시 진행할 수 없습니다.'
    default:
      return '현재 주문 상태에서는 결제를 진행할 수 없습니다.'
  }
}

const stripPaymentRedirectParams = (searchParams: URLSearchParams) => {
  const next = new URLSearchParams(searchParams)
  next.delete('paymentKey')
  next.delete('orderId')
  next.delete('amount')
  next.delete('code')
  next.delete('message')
  return next
}

const buildFailureMessage = (code: string, message: string) => {
  if (code && message) {
    return `결제가 취소되었거나 실패했습니다. [${code}] ${message}`
  }

  if (code) {
    return `결제가 취소되었거나 실패했습니다. [${code}]`
  }

  return message
}

const POST_CONFIRM_POLL_INTERVAL_MS = 1500
const POST_CONFIRM_POLL_TIMEOUT_MS = 30000

export const CheckoutPage = () => {
  const { orderId } = useParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const auth = useAuth()
  const parsedOrderId = Number(orderId)
  const queryClient = useQueryClient()
  const [shouldPollOrder, setShouldPollOrder] = useState(false)
  const [pollTimedOut, setPollTimedOut] = useState(false)

  const redirectedPaymentKey = searchParams.get('paymentKey')?.trim() ?? ''
  const redirectedOrderId = searchParams.get('orderId')?.trim() ?? ''
  const redirectedAmountRaw = searchParams.get('amount')?.trim() ?? ''
  const redirectedAmount = Number(redirectedAmountRaw)

  const redirectConfirmPayload = useMemo<ConfirmPaymentRequest | null>(() => {
    if (!redirectedPaymentKey && !redirectedOrderId && !redirectedAmountRaw) {
      return null
    }

    if (!redirectedPaymentKey || !redirectedOrderId || Number.isNaN(redirectedAmount) || redirectedAmount <= 0) {
      return null
    }

    return {
      paymentKey: redirectedPaymentKey,
      orderId: redirectedOrderId,
      amount: redirectedAmount,
    }
  }, [redirectedAmount, redirectedAmountRaw, redirectedOrderId, redirectedPaymentKey])

  const redirectFailureCode = searchParams.get('code')?.trim() ?? ''
  const redirectFailureMessageRaw = searchParams.get('message')?.trim() ?? ''
  const redirectFailureMessage =
    redirectFailureCode || redirectFailureMessageRaw
      ? buildFailureMessage(redirectFailureCode, redirectFailureMessageRaw)
      : null

  const hasRedirectPaymentParams = !!(redirectedPaymentKey || redirectedOrderId || redirectedAmountRaw)
  const hasInvalidRedirectPaymentParams = hasRedirectPaymentParams && !redirectConfirmPayload

  const orderQuery = useQuery({
    queryKey: ['order', parsedOrderId],
    queryFn: () => orderApi.getOrder(parsedOrderId),
    enabled: Number.isFinite(parsedOrderId),
    refetchInterval: (query) => {
      const status = (query.state.data as { status?: OrderStatus } | undefined)?.status
      if (!shouldPollOrder || status !== 'CREATED') {
        return false
      }
      return POST_CONFIRM_POLL_INTERVAL_MS
    },
  })

  const prepareAndOpenMutation = useMutation({
    mutationFn: async () => {
      const prepared = await paymentApi.preparePayment(
        { orderId: parsedOrderId },
        getOrCreateIdempotencyKey(parsedOrderId),
      )

      const redirectUrl = `${window.location.origin}/checkout/${parsedOrderId}`

      await openTossPaymentWindow({
        clientKey: env.tossClientKey,
        customerKey: getOrCreateTossCustomerKey(),
        request: {
          method: 'CARD',
          amount: {
            currency: 'KRW',
            value: prepared.amount,
          },
          orderId: prepared.tossOrderId,
          orderName: prepared.orderName,
          successUrl: redirectUrl,
          failUrl: redirectUrl,
          customerEmail: auth.email || undefined,
          customerName: auth.name || undefined,
        },
      })

      return prepared
    },
  })

  const confirmMutation = useMutation({
    mutationFn: (payload: ConfirmPaymentRequest) => paymentApi.confirmPayment(payload),
    onSuccess: async () => {
      sessionStorage.removeItem(`idempotency-key-${parsedOrderId}`)
      setSearchParams(stripPaymentRedirectParams(searchParams))
      setPollTimedOut(false)
      setShouldPollOrder(true)

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['order', parsedOrderId] }),
        queryClient.invalidateQueries({ queryKey: ['orders'] }),
        queryClient.invalidateQueries({ queryKey: ['payments'] }),
      ])
    },
  })

  const autoConfirmAttemptRef = useRef<string>('')

  useEffect(() => {
    autoConfirmAttemptRef.current = ''
  }, [redirectedAmountRaw, redirectedOrderId, redirectedPaymentKey])

  useEffect(() => {
    if (!shouldPollOrder || orderQuery.data?.status !== 'CREATED') {
      return
    }

    const timeoutId = window.setTimeout(() => {
      setShouldPollOrder(false)
      setPollTimedOut(true)
    }, POST_CONFIRM_POLL_TIMEOUT_MS)

    return () => window.clearTimeout(timeoutId)
  }, [orderQuery.data?.status, shouldPollOrder])

  const order = orderQuery.data
  const isOrderCreatable = order?.status === 'CREATED'
  const isOrderAlreadyPaid = order?.status === 'PAID' || order?.status === 'COMPLETED'
  const isOrderNonPayable = !!order && !isOrderCreatable && !isOrderAlreadyPaid
  const isPostConfirmSyncing = !!order && shouldPollOrder && order.status === 'CREATED'
  const isPostConfirmSynced = !!order && !!confirmMutation.data && (order.status === 'PAID' || order.status === 'COMPLETED')

  useEffect(() => {
    if (!isOrderCreatable || !redirectConfirmPayload) {
      return
    }

    if (autoConfirmAttemptRef.current === redirectConfirmPayload.paymentKey) {
      return
    }

    autoConfirmAttemptRef.current = redirectConfirmPayload.paymentKey
    confirmMutation.mutate(redirectConfirmPayload)
  }, [confirmMutation, isOrderCreatable, redirectConfirmPayload])

  if (!Number.isFinite(parsedOrderId)) {
    return <ErrorView message="잘못된 주문 경로입니다." />
  }

  if (orderQuery.isLoading) {
    return <LoadingView message="체크아웃 정보를 불러오는 중..." />
  }

  if (orderQuery.isError) {
    return <ErrorView message={getErrorMessage(orderQuery.error)} onRetry={() => void orderQuery.refetch()} />
  }

  if (!order) {
    return <LoadingView message="체크아웃 정보를 불러오는 중..." />
  }

  return (
    <VStack gap="x5">
      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating px-5 py-6">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t7Bold">체크아웃</Text>
          <HStack gap="x2" align="center">
            <Text textStyle="t4Regular">주문 #{order.id}</Text>
            <StatusChip status={order.status} />
          </HStack>
          <Text textStyle="t6Bold">결제 예정 금액: {formatCurrency(order.totalAmount)}</Text>
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            결제하기 버튼을 누르면 결제 준비 후 토스 결제창으로 이동하고, 리다이렉트 시 자동 승인됩니다.
          </Text>
        </VStack>
      </section>

      <section className="rounded-r3 border border-stroke-informative-weak bg-bg-informative-weak px-5 py-4">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t5Bold" color="fg.informative">
            결제 후 주문/배송 반영은 비동기 처리됩니다.
          </Text>
          <Text textStyle="t4Regular" color="fg.informative">
            결제 승인 직후에도 주문 상태가 잠시 CREATED로 보일 수 있습니다. 이 화면은 자동 새로고침으로 후속 반영을 확인합니다.
          </Text>
        </VStack>
      </section>

      {isOrderNonPayable ? (
        <section className="rounded-r3 border border-stroke-critical-weak bg-bg-critical-weak px-5 py-5">
          <VStack gap="x2" align="flex-start">
            <Text textStyle="t5Bold" color="fg.critical">
              결제를 진행할 수 없는 주문 상태입니다.
            </Text>
            <Text textStyle="t4Regular" color="fg.critical">
              {getNonPayableStatusMessage(order.status)}
            </Text>
            <HStack gap="x2">
              <Link to={`/me/orders/${order.id}`}>
                <ActionButton variant="neutralWeak">주문 상세</ActionButton>
              </Link>
              <Link to="/products">
                <ActionButton variant="neutralWeak">상품으로 이동</ActionButton>
              </Link>
            </HStack>
          </VStack>
        </section>
      ) : null}

      {isOrderAlreadyPaid ? (
        <section className="rounded-r3 border border-stroke-positive-weak bg-bg-positive-weak px-5 py-5">
          <VStack gap="x2" align="flex-start">
            <Text textStyle="t5Bold" color="fg.positive">
              결제가 이미 완료된 주문입니다.
            </Text>
            <HStack gap="x2">
              <Link to={`/me/orders/${order.id}`}>
                <ActionButton variant="neutralWeak">주문 상세</ActionButton>
              </Link>
              <Link to="/me/orders">
                <ActionButton variant="neutralWeak">주문 목록</ActionButton>
              </Link>
            </HStack>
          </VStack>
        </section>
      ) : null}

      {isPostConfirmSyncing ? (
        <section className="rounded-r3 border border-stroke-warning-weak bg-bg-warning-weak px-5 py-5">
          <VStack gap="x2" align="flex-start">
            <Text textStyle="t5Bold" color="fg.warning">
              결제는 승인되었고, 주문/배송 반영을 확인하는 중입니다.
            </Text>
            <Text textStyle="t4Regular" color="fg.warning">
              백엔드 비동기 후속 처리(markAsPaid, delivery 생성)가 진행 중일 수 있습니다.
            </Text>
            <ActionButton variant="neutralWeak" loading={orderQuery.isFetching} onClick={() => void orderQuery.refetch()}>
              상태 새로고침
            </ActionButton>
          </VStack>
        </section>
      ) : null}

      {pollTimedOut && !isPostConfirmSynced ? (
        <section className="rounded-r3 border border-stroke-critical-weak bg-bg-critical-weak px-5 py-5">
          <VStack gap="x2" align="flex-start">
            <Text textStyle="t5Bold" color="fg.critical">
              후속 반영이 지연되고 있습니다.
            </Text>
            <Text textStyle="t4Regular" color="fg.critical">
              잠시 후 다시 새로고침하거나 주문 상세 화면에서 상태를 확인해주세요.
            </Text>
            <ActionButton variant="neutralWeak" loading={orderQuery.isFetching} onClick={() => void orderQuery.refetch()}>
              지금 새로고침
            </ActionButton>
          </VStack>
        </section>
      ) : null}

      {isOrderCreatable ? (
        <>
          <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating p-5">
            <VStack gap="x3" align="flex-start">
              <Text textStyle="t5Bold">결제수단</Text>
              <VStack
                gap="x1"
                align="flex-start"
                className="w-full rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default px-4 py-3"
              >
                <Text textStyle="t5Bold">토스페이먼츠 결제창</Text>
                <Text textStyle="t4Regular" color="fg.neutralSubtle">
                  카드/간편결제 수단 선택은 토스 결제창에서 진행됩니다.
                </Text>
              </VStack>
            </VStack>
          </section>

          <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating p-5">
            <VStack gap="x3" align="flex-start">
              <Text textStyle="t5Bold">결제 진행</Text>
              <Text textStyle="t4Regular" color="fg.neutralSubtle">
                결제하기를 누르면 내부적으로 결제 준비를 수행한 뒤 결제창을 엽니다.
              </Text>

              <ActionButton
                loading={prepareAndOpenMutation.isPending}
                disabled={prepareAndOpenMutation.isPending || confirmMutation.isPending || !!redirectConfirmPayload}
                onClick={() => {
                  setPollTimedOut(false)
                  setShouldPollOrder(false)
                  prepareAndOpenMutation.mutate()
                }}
              >
                결제하기
              </ActionButton>

              {prepareAndOpenMutation.isPending ? (
                <Text textStyle="t4Regular" color="fg.neutralSubtle">
                  결제창을 준비하는 중입니다...
                </Text>
              ) : null}

              {redirectConfirmPayload ? (
                <Text textStyle="t4Regular" color="fg.neutralSubtle">
                  리다이렉트 결제 정보를 확인했습니다. 결제 승인을 처리합니다.
                </Text>
              ) : null}

              {confirmMutation.isPending ? (
                <Text textStyle="t4Regular" color="fg.neutralSubtle">
                  결제 승인 처리 중입니다...
                </Text>
              ) : null}

              {confirmMutation.isError && redirectConfirmPayload ? (
                <ActionButton
                  variant="neutralWeak"
                  loading={confirmMutation.isPending}
                  disabled={confirmMutation.isPending}
                  onClick={() => confirmMutation.mutate(redirectConfirmPayload)}
                >
                  승인 재시도
                </ActionButton>
              ) : null}
            </VStack>
          </section>
        </>
      ) : null}

      {hasInvalidRedirectPaymentParams ? (
        <Text textStyle="t4Regular" color="fg.critical">
          리다이렉트 결제 파라미터가 올바르지 않습니다. 다시 결제를 시도해주세요.
        </Text>
      ) : null}

      {redirectFailureMessage ? (
        <Text textStyle="t4Regular" color="fg.critical">
          {redirectFailureMessage}
        </Text>
      ) : null}

      {prepareAndOpenMutation.isError ? (
        <Text textStyle="t4Regular" color="fg.critical">
          {getErrorMessage(prepareAndOpenMutation.error)}
        </Text>
      ) : null}

      {confirmMutation.isError ? (
        <Text textStyle="t4Regular" color="fg.critical">
          {getErrorMessage(confirmMutation.error)}
        </Text>
      ) : null}

      {confirmMutation.data ? (
        <section className="rounded-r3 border border-stroke-positive-weak bg-bg-positive-weak px-5 py-5">
          <VStack gap="x2" align="flex-start">
            <Text textStyle="t5Bold" color="fg.positive">
              결제 승인이 완료되었습니다.
            </Text>
            <Text textStyle="t4Regular">paymentId: {confirmMutation.data.id}</Text>
            <StatusChip status={confirmMutation.data.status} />
            {isPostConfirmSynced ? (
              <Text textStyle="t4Regular" color="fg.positive">
                주문 상태 반영이 확인되었습니다.
              </Text>
            ) : (
              <Text textStyle="t4Regular" color="fg.neutralSubtle">
                주문/배송 반영은 비동기 처리입니다. 상태가 늦게 반영될 수 있습니다.
              </Text>
            )}
            <HStack gap="x2">
              <Link to={`/me/orders/${order.id}`}>
                <ActionButton variant="neutralWeak">주문 상세</ActionButton>
              </Link>
              <Link to="/me/orders">
                <ActionButton variant="neutralWeak">주문 목록</ActionButton>
              </Link>
            </HStack>
          </VStack>
        </section>
      ) : null}
    </VStack>
  )
}

