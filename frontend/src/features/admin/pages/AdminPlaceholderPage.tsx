import { ActionButton, HStack, Text, VStack } from '@seed-design/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { adminApi } from '@/features/admin/api/adminApi'
import type { DeliveryResponse, DeliveryStatus, RefundStatus } from '@/shared/types/domain'
import { ErrorView } from '@/shared/ui/ErrorView'
import { LoadingView } from '@/shared/ui/LoadingView'
import { StatusChip } from '@/shared/ui/StatusChip'
import { formatCurrency } from '@/shared/utils/format'
import { formatDateTime } from '@/shared/utils/date'
import { getErrorMessage } from '@/shared/utils/errors'

type AdminPanel = 'refunds' | 'deliveries' | 'operations'
type RefundFilter = 'ALL' | RefundStatus
type DeliveryFilter = 'ACTION_REQUIRED' | 'ALL' | DeliveryStatus

const refundFilters: { key: RefundFilter; label: string }[] = [
  { key: 'ALL', label: '전체' },
  { key: 'REQUESTED', label: '요청됨' },
  { key: 'APPROVED', label: '승인됨' },
  { key: 'REJECTED', label: '거절됨' },
  { key: 'COMPLETED', label: '완료' },
  { key: 'FAILED', label: '실패' },
]

const deliveryFilters: { key: DeliveryFilter; label: string }[] = [
  { key: 'ACTION_REQUIRED', label: '처리 필요' },
  { key: 'ALL', label: '전체' },
  { key: 'PREPARING', label: '준비중' },
  { key: 'SHIPPED', label: '발송됨' },
  { key: 'IN_TRANSIT', label: '배송중' },
  { key: 'DELIVERED', label: '완료' },
  { key: 'CANCELED', label: '취소' },
]

const actionRequiredDeliveryStatuses: DeliveryStatus[] = ['PREPARING', 'SHIPPED', 'IN_TRANSIT']
const deliveryStatusPriority: Record<DeliveryStatus, number> = {
  PREPARING: 0,
  SHIPPED: 1,
  IN_TRANSIT: 2,
  DELIVERED: 3,
  CANCELED: 4,
}

const preparingStaleMs = 1000 * 60 * 60 * 24
const shippedStaleMs = 1000 * 60 * 60 * 72

const isActionRequiredDelivery = (status: DeliveryStatus) => actionRequiredDeliveryStatuses.includes(status)

const matchesDeliveryFilter = (status: DeliveryStatus, filter: DeliveryFilter) => {
  if (filter === 'ALL') {
    return true
  }
  if (filter === 'ACTION_REQUIRED') {
    return isActionRequiredDelivery(status)
  }
  return status === filter
}

const getDeliveryStaleMessage = (delivery: DeliveryResponse, referenceTime: number) => {
  if (referenceTime <= 0) {
    return null
  }

  const createdAt = new Date(delivery.createdAt).getTime()

  if (delivery.status === 'PREPARING' && Number.isFinite(createdAt) && referenceTime - createdAt >= preparingStaleMs) {
    return '24시간 이상 준비중입니다. 출고 지연 여부를 확인하세요.'
  }

  const shippedAt = delivery.shippedAt ? new Date(delivery.shippedAt).getTime() : NaN
  if (delivery.status === 'SHIPPED' && Number.isFinite(shippedAt) && referenceTime - shippedAt >= shippedStaleMs) {
    return '발송 후 72시간 이상 상태 변경이 없습니다. 배송사 추적이 필요합니다.'
  }

  return null
}

export const AdminPlaceholderPage = () => {
  const queryClient = useQueryClient()
  const [activePanel, setActivePanel] = useState<AdminPanel>('refunds')

  const [refundFilter, setRefundFilter] = useState<RefundFilter>('ALL')
  const [refundCommentDrafts, setRefundCommentDrafts] = useState<Record<number, string>>({})

  const [deliveryFilter, setDeliveryFilter] = useState<DeliveryFilter>('ACTION_REQUIRED')
  const [shipDrafts, setShipDrafts] = useState<Record<number, { carrier: string; trackingNumber: string }>>({})
  const [createDeliveryOrderId, setCreateDeliveryOrderId] = useState('')
  const [createDeliveryRecipientName, setCreateDeliveryRecipientName] = useState('')
  const [createDeliveryRecipientPhone, setCreateDeliveryRecipientPhone] = useState('')
  const [createDeliveryAddress, setCreateDeliveryAddress] = useState('')
  const [createDeliveryAddressDetail, setCreateDeliveryAddressDetail] = useState('')
  const [createDeliveryZipCode, setCreateDeliveryZipCode] = useState('')

  const [completeOrderIdInput, setCompleteOrderIdInput] = useState('')
  const [inventoryProductIdInput, setInventoryProductIdInput] = useState('')
  const [addStockQuantityInput, setAddStockQuantityInput] = useState('')
  const [productNameInput, setProductNameInput] = useState('')
  const [productDescriptionInput, setProductDescriptionInput] = useState('')
  const [productUnitPriceInput, setProductUnitPriceInput] = useState('')

  const inventoryProductId = Number(inventoryProductIdInput)
  const inventoryQuery = useQuery({
    queryKey: ['admin', 'inventory', inventoryProductId],
    queryFn: () => adminApi.getInventory(inventoryProductId),
    enabled: Number.isFinite(inventoryProductId) && inventoryProductId > 0,
  })

  const adminRefundsQuery = useQuery({
    queryKey: ['admin', 'refunds', refundFilter],
    queryFn: () => adminApi.getRefunds(refundFilter === 'ALL' ? undefined : refundFilter),
    enabled: activePanel === 'refunds',
  })

  const adminDeliveriesQuery = useQuery({
    queryKey: ['admin', 'deliveries'],
    queryFn: () => adminApi.getDeliveries(),
    enabled: activePanel === 'deliveries',
  })

  const approveRefundMutation = useMutation({
    mutationFn: (refundId: number) =>
      adminApi.approveRefund(refundId, {
        comment: refundCommentDrafts[refundId] || undefined,
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin', 'refunds'] }),
        queryClient.invalidateQueries({ queryKey: ['refunds'] }),
      ])
    },
  })

  const rejectRefundMutation = useMutation({
    mutationFn: (refundId: number) =>
      adminApi.rejectRefund(refundId, {
        comment: refundCommentDrafts[refundId] || undefined,
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin', 'refunds'] }),
        queryClient.invalidateQueries({ queryKey: ['refunds'] }),
      ])
    },
  })

  const createDeliveryMutation = useMutation({
    mutationFn: () => {
      const orderId = Number(createDeliveryOrderId)
      if (!Number.isFinite(orderId) || !createDeliveryRecipientName || !createDeliveryRecipientPhone || !createDeliveryAddress || !createDeliveryZipCode) {
        throw new Error('배송 생성 입력값을 확인해주세요.')
      }

      return adminApi.createDelivery({
        orderId,
        recipientName: createDeliveryRecipientName,
        recipientPhone: createDeliveryRecipientPhone,
        address: createDeliveryAddress,
        addressDetail: createDeliveryAddressDetail,
        zipCode: createDeliveryZipCode,
      })
    },
    onSuccess: async () => {
      setCreateDeliveryOrderId('')
      setCreateDeliveryRecipientName('')
      setCreateDeliveryRecipientPhone('')
      setCreateDeliveryAddress('')
      setCreateDeliveryAddressDetail('')
      setCreateDeliveryZipCode('')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin', 'deliveries'] }),
        queryClient.invalidateQueries({ queryKey: ['deliveries'] }),
      ])
    },
  })

  const shipDeliveryMutation = useMutation({
    mutationFn: (deliveryId: number) => {
      const draft = shipDrafts[deliveryId]
      if (!draft?.carrier || !draft?.trackingNumber) {
        throw new Error('택배사와 운송장을 입력해주세요.')
      }
      return adminApi.shipDelivery(deliveryId, draft)
    },
    onSuccess: async (_, deliveryId) => {
      setShipDrafts((prev) => {
        if (!prev[deliveryId]) {
          return prev
        }
        const next = { ...prev }
        delete next[deliveryId]
        return next
      })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin', 'deliveries'] }),
        queryClient.invalidateQueries({ queryKey: ['deliveries'] }),
      ])
    },
  })

  const markInTransitMutation = useMutation({
    mutationFn: (deliveryId: number) => adminApi.markDeliveryInTransit(deliveryId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin', 'deliveries'] }),
        queryClient.invalidateQueries({ queryKey: ['deliveries'] }),
      ])
    },
  })

  const markDeliveredMutation = useMutation({
    mutationFn: (deliveryId: number) => adminApi.markDeliveryDelivered(deliveryId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin', 'deliveries'] }),
        queryClient.invalidateQueries({ queryKey: ['deliveries'] }),
      ])
    },
  })

  const cancelDeliveryMutation = useMutation({
    mutationFn: (deliveryId: number) => adminApi.cancelDelivery(deliveryId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin', 'deliveries'] }),
        queryClient.invalidateQueries({ queryKey: ['deliveries'] }),
      ])
    },
  })

  const completeOrderMutation = useMutation({
    mutationFn: () => {
      const orderId = Number(completeOrderIdInput)
      if (!Number.isFinite(orderId)) {
        throw new Error('주문 ID를 확인해주세요.')
      }
      return adminApi.completeOrder(orderId)
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['orders'] })
    },
  })

  const addStockMutation = useMutation({
    mutationFn: () => {
      const productId = Number(inventoryProductIdInput)
      const quantity = Number(addStockQuantityInput)
      if (!Number.isFinite(productId) || !Number.isFinite(quantity) || quantity <= 0) {
        throw new Error('재고 입력값을 확인해주세요.')
      }
      return adminApi.addStock(productId, { quantity })
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin', 'inventory'] }),
        queryClient.invalidateQueries({ queryKey: ['products'] }),
      ])
    },
  })

  const createProductMutation = useMutation({
    mutationFn: () => {
      const unitPrice = Number(productUnitPriceInput)
      if (!productNameInput || !productDescriptionInput || !Number.isFinite(unitPrice) || unitPrice <= 0) {
        throw new Error('상품 입력값을 확인해주세요.')
      }
      return adminApi.createProduct({
        name: productNameInput,
        description: productDescriptionInput,
        unitPrice,
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['products'] })
    },
  })

  const actionErrorMessage = useMemo(() => {
    const candidates = [
      approveRefundMutation.error,
      rejectRefundMutation.error,
      createDeliveryMutation.error,
      shipDeliveryMutation.error,
      markInTransitMutation.error,
      markDeliveredMutation.error,
      cancelDeliveryMutation.error,
      completeOrderMutation.error,
      addStockMutation.error,
      createProductMutation.error,
    ]
    const latest = candidates.find((error) => !!error)
    return latest ? getErrorMessage(latest) : null
  }, [
    addStockMutation.error,
    approveRefundMutation.error,
    cancelDeliveryMutation.error,
    completeOrderMutation.error,
    createDeliveryMutation.error,
    createProductMutation.error,
    markDeliveredMutation.error,
    markInTransitMutation.error,
    rejectRefundMutation.error,
    shipDeliveryMutation.error,
  ])

  const renderRefundPanel = () => {
    if (adminRefundsQuery.isLoading) {
      return <LoadingView message="관리자 환불 목록을 불러오는 중..." />
    }

    if (adminRefundsQuery.isError) {
      return <ErrorView message={getErrorMessage(adminRefundsQuery.error)} onRetry={() => void adminRefundsQuery.refetch()} />
    }

    const refunds = adminRefundsQuery.data ?? []

    return (
      <VStack gap="x3" className="w-full">
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

        {refunds.length === 0 ? (
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            관리할 환불 요청이 없습니다.
          </Text>
        ) : (
          refunds.map((refund) => (
            <section key={refund.id} className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default p-4">
              <VStack gap="x2" align="flex-start">
                <HStack justify="space-between" align="center" className="w-full flex-wrap gap-2">
                  <Text textStyle="t6Bold">환불 #{refund.id}</Text>
                  <StatusChip status={refund.status} />
                </HStack>
                <Text textStyle="t4Regular">paymentId: {refund.paymentId}</Text>
                <Text textStyle="t4Regular">amount: {formatCurrency(refund.amount)}</Text>
                <Text textStyle="t4Regular" color="fg.neutralSubtle">
                  요청 사유: {refund.reason ?? '-'}
                </Text>

                <label className="flex w-full flex-col gap-1">
                  <span className="text-sm text-fg-neutral-subtle">처리 코멘트 (선택)</span>
                  <input
                    className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
                    value={refundCommentDrafts[refund.id] ?? ''}
                    onChange={(event) =>
                      setRefundCommentDrafts((prev) => ({
                        ...prev,
                        [refund.id]: event.target.value,
                      }))
                    }
                  />
                </label>

                {refund.status === 'REQUESTED' ? (
                  <HStack gap="x2" className="flex-wrap">
                    <ActionButton
                      loading={approveRefundMutation.isPending && approveRefundMutation.variables === refund.id}
                      disabled={approveRefundMutation.isPending || rejectRefundMutation.isPending}
                      onClick={() => approveRefundMutation.mutate(refund.id)}
                    >
                      승인
                    </ActionButton>
                    <ActionButton
                      variant="criticalSolid"
                      loading={rejectRefundMutation.isPending && rejectRefundMutation.variables === refund.id}
                      disabled={approveRefundMutation.isPending || rejectRefundMutation.isPending}
                      onClick={() => rejectRefundMutation.mutate(refund.id)}
                    >
                      거절
                    </ActionButton>
                  </HStack>
                ) : null}
              </VStack>
            </section>
          ))
        )}
      </VStack>
    )
  }

  const renderDeliveryPanel = () => {
    if (adminDeliveriesQuery.isLoading) {
      return <LoadingView message="관리자 배송 목록을 불러오는 중..." />
    }

    if (adminDeliveriesQuery.isError) {
      return <ErrorView message={getErrorMessage(adminDeliveriesQuery.error)} onRetry={() => void adminDeliveriesQuery.refetch()} />
    }

    const deliveries = adminDeliveriesQuery.data ?? []
    const staleReferenceTime = adminDeliveriesQuery.dataUpdatedAt
    const deliveryCounts = deliveries.reduce<Record<DeliveryStatus, number>>(
      (acc, delivery) => {
        acc[delivery.status] += 1
        return acc
      },
      {
        PREPARING: 0,
        SHIPPED: 0,
        IN_TRANSIT: 0,
        DELIVERED: 0,
        CANCELED: 0,
      },
    )
    const actionRequiredCount = deliveryCounts.PREPARING + deliveryCounts.SHIPPED + deliveryCounts.IN_TRANSIT

    const filteredDeliveries = deliveries
      .filter((delivery) => matchesDeliveryFilter(delivery.status, deliveryFilter))
      .sort((a, b) => {
        const priorityDiff = deliveryStatusPriority[a.status] - deliveryStatusPriority[b.status]
        if (priorityDiff !== 0) {
          return priorityDiff
        }
        const createdTimeDiff = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
        if (createdTimeDiff !== 0) {
          return createdTimeDiff
        }
        return a.id - b.id
      })

    const getFilterCount = (filter: DeliveryFilter) => {
      if (filter === 'ALL') {
        return deliveries.length
      }
      if (filter === 'ACTION_REQUIRED') {
        return actionRequiredCount
      }
      return deliveryCounts[filter]
    }

    return (
      <VStack gap="x3" className="w-full">
        <section className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default p-4">
          <VStack gap="x2" align="flex-start">
            <Text textStyle="t6Bold">배송 처리 큐</Text>
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              발송/배송중/완료 처리를 한 화면에서 빠르게 수행합니다.
            </Text>

            <div className="grid w-full gap-2 sm:grid-cols-3">
              <div className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-3 py-3">
                <Text textStyle="t3Regular" color="fg.neutralSubtle">
                  처리 필요
                </Text>
                <Text textStyle="t5Bold">{actionRequiredCount}건</Text>
              </div>
              <div className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-3 py-3">
                <Text textStyle="t3Regular" color="fg.neutralSubtle">
                  완료
                </Text>
                <Text textStyle="t5Bold">{deliveryCounts.DELIVERED}건</Text>
              </div>
              <div className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-3 py-3">
                <Text textStyle="t3Regular" color="fg.neutralSubtle">
                  취소
                </Text>
                <Text textStyle="t5Bold">{deliveryCounts.CANCELED}건</Text>
              </div>
            </div>

            <div className="flex w-full flex-wrap gap-2">
              {deliveryFilters.map((item) => (
                <button
                  key={item.key}
                  type="button"
                  aria-pressed={deliveryFilter === item.key}
                  className={
                    deliveryFilter === item.key
                      ? 'rounded-r2 border border-stroke-brand-solid bg-bg-brand-weak px-3 py-1 text-xs font-semibold text-fg-brand'
                      : 'rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default px-3 py-1 text-xs font-medium text-fg-neutral-subtle'
                  }
                  onClick={() => setDeliveryFilter(item.key)}
                >
                  {item.label} ({getFilterCount(item.key)})
                </button>
              ))}
              <ActionButton
                variant="neutralWeak"
                size="xsmall"
                loading={adminDeliveriesQuery.isFetching}
                disabled={adminDeliveriesQuery.isFetching}
                onClick={() => void adminDeliveriesQuery.refetch()}
              >
                새로고침
              </ActionButton>
            </div>

            {adminDeliveriesQuery.isFetching ? (
              <Text textStyle="t3Regular" color="fg.neutralSubtle">
                최신 상태로 동기화 중...
              </Text>
            ) : null}
          </VStack>
        </section>

        {filteredDeliveries.length === 0 ? (
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            선택한 조건의 배송 데이터가 없습니다.
          </Text>
        ) : (
          filteredDeliveries.map((delivery) => {
            const shipDraft = shipDrafts[delivery.id] ?? { carrier: '', trackingNumber: '' }
            const staleMessage = getDeliveryStaleMessage(delivery, staleReferenceTime)

            return (
              <section key={delivery.id} className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default p-4">
                <VStack gap="x2" align="flex-start">
                  <HStack justify="space-between" align="center" className="w-full flex-wrap gap-2">
                    <Text textStyle="t6Bold">배송 #{delivery.id}</Text>
                    <StatusChip status={delivery.status} />
                  </HStack>

                  <Text textStyle="t4Regular">orderId: {delivery.orderId}</Text>
                  <Text textStyle="t4Regular" color="fg.neutralSubtle">
                    {delivery.recipientName} / {delivery.recipientPhone}
                  </Text>
                  <Text textStyle="t4Regular" color="fg.neutralSubtle">
                    주소: {delivery.address} {delivery.addressDetail} ({delivery.zipCode})
                  </Text>
                  <Text textStyle="t4Regular" color="fg.neutralSubtle">
                    생성: {formatDateTime(delivery.createdAt)}
                  </Text>
                  {delivery.shippedAt ? (
                    <Text textStyle="t4Regular" color="fg.neutralSubtle">
                      발송: {formatDateTime(delivery.shippedAt)}
                    </Text>
                  ) : null}
                  {delivery.deliveredAt ? (
                    <Text textStyle="t4Regular" color="fg.neutralSubtle">
                      완료: {formatDateTime(delivery.deliveredAt)}
                    </Text>
                  ) : null}
                  {delivery.carrier || delivery.trackingNumber ? (
                    <Text textStyle="t4Regular" color="fg.neutralSubtle">
                      택배: {delivery.carrier ?? '-'} / {delivery.trackingNumber ?? '-'}
                    </Text>
                  ) : null}
                  {staleMessage ? (
                    <Text textStyle="t4Medium" color="fg.warning">
                      {staleMessage}
                    </Text>
                  ) : null}

                  {delivery.status === 'PREPARING' ? (
                    <div className="grid w-full gap-2 md:grid-cols-2">
                      <label className="flex w-full flex-col gap-1">
                        <span className="text-sm text-fg-neutral-subtle">carrier</span>
                        <input
                          className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
                          value={shipDraft.carrier}
                          onChange={(event) =>
                            setShipDrafts((prev) => ({
                              ...prev,
                              [delivery.id]: {
                                ...shipDraft,
                                carrier: event.target.value,
                              },
                            }))
                          }
                        />
                      </label>
                      <label className="flex w-full flex-col gap-1">
                        <span className="text-sm text-fg-neutral-subtle">trackingNumber</span>
                        <input
                          className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
                          value={shipDraft.trackingNumber}
                          onChange={(event) =>
                            setShipDrafts((prev) => ({
                              ...prev,
                              [delivery.id]: {
                                ...shipDraft,
                                trackingNumber: event.target.value,
                              },
                            }))
                          }
                        />
                      </label>
                    </div>
                  ) : null}

                  {delivery.status !== 'DELIVERED' && delivery.status !== 'CANCELED' ? (
                    <Text textStyle="t3Regular" color="fg.warning">
                      주의: 배송 취소는 환불/고객 커뮤니케이션 후속 작업이 필요합니다.
                    </Text>
                  ) : null}

                  <div className="grid w-full gap-2 md:grid-cols-2 lg:grid-cols-4">
                    {delivery.status === 'PREPARING' ? (
                      <ActionButton
                        className="w-full justify-center"
                        loading={shipDeliveryMutation.isPending && shipDeliveryMutation.variables === delivery.id}
                        disabled={shipDeliveryMutation.isPending}
                        onClick={() => shipDeliveryMutation.mutate(delivery.id)}
                      >
                        발송 처리
                      </ActionButton>
                    ) : null}

                    {delivery.status === 'SHIPPED' ? (
                      <ActionButton
                        className="w-full justify-center"
                        loading={markInTransitMutation.isPending && markInTransitMutation.variables === delivery.id}
                        disabled={markInTransitMutation.isPending}
                        onClick={() => markInTransitMutation.mutate(delivery.id)}
                      >
                        배송중 처리
                      </ActionButton>
                    ) : null}

                    {delivery.status === 'IN_TRANSIT' ? (
                      <ActionButton
                        className="w-full justify-center"
                        loading={markDeliveredMutation.isPending && markDeliveredMutation.variables === delivery.id}
                        disabled={markDeliveredMutation.isPending}
                        onClick={() => markDeliveredMutation.mutate(delivery.id)}
                      >
                        배송완료 처리
                      </ActionButton>
                    ) : null}

                    {delivery.status !== 'DELIVERED' && delivery.status !== 'CANCELED' ? (
                      <ActionButton
                        className="w-full justify-center"
                        variant="criticalSolid"
                        loading={cancelDeliveryMutation.isPending && cancelDeliveryMutation.variables === delivery.id}
                        disabled={cancelDeliveryMutation.isPending}
                        onClick={() => {
                          if (!window.confirm('배송을 취소하시겠습니까? 이 작업은 되돌릴 수 없습니다.')) {
                            return
                          }
                          cancelDeliveryMutation.mutate(delivery.id)
                        }}
                      >
                        배송 취소
                      </ActionButton>
                    ) : null}
                  </div>
                </VStack>
              </section>
            )
          })
        )}

        <section className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default p-4">
          <VStack gap="x2" align="flex-start">
            <Text textStyle="t6Bold">배송 생성</Text>
            <Text textStyle="t3Regular" color="fg.neutralSubtle">
              신규 주문 배송을 등록하면 즉시 처리 큐에 반영됩니다.
            </Text>

            <div className="grid w-full gap-2 md:grid-cols-2">
              <label className="flex w-full flex-col gap-1">
                <span className="text-sm text-fg-neutral-subtle">orderId</span>
                <input
                  className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
                  type="number"
                  inputMode="numeric"
                  value={createDeliveryOrderId}
                  onChange={(event) => setCreateDeliveryOrderId(event.target.value)}
                />
              </label>
              <label className="flex w-full flex-col gap-1">
                <span className="text-sm text-fg-neutral-subtle">recipientName</span>
                <input
                  className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
                  value={createDeliveryRecipientName}
                  onChange={(event) => setCreateDeliveryRecipientName(event.target.value)}
                />
              </label>
              <label className="flex w-full flex-col gap-1">
                <span className="text-sm text-fg-neutral-subtle">recipientPhone</span>
                <input
                  className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
                  value={createDeliveryRecipientPhone}
                  onChange={(event) => setCreateDeliveryRecipientPhone(event.target.value)}
                />
              </label>
              <label className="flex w-full flex-col gap-1">
                <span className="text-sm text-fg-neutral-subtle">zipCode</span>
                <input
                  className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
                  value={createDeliveryZipCode}
                  onChange={(event) => setCreateDeliveryZipCode(event.target.value)}
                />
              </label>
            </div>

            <label className="flex w-full flex-col gap-1">
              <span className="text-sm text-fg-neutral-subtle">address</span>
              <input
                className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
                value={createDeliveryAddress}
                onChange={(event) => setCreateDeliveryAddress(event.target.value)}
              />
            </label>
            <label className="flex w-full flex-col gap-1">
              <span className="text-sm text-fg-neutral-subtle">addressDetail</span>
              <input
                className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
                value={createDeliveryAddressDetail}
                onChange={(event) => setCreateDeliveryAddressDetail(event.target.value)}
              />
            </label>

            <ActionButton loading={createDeliveryMutation.isPending} disabled={createDeliveryMutation.isPending} onClick={() => createDeliveryMutation.mutate()}>
              배송 생성
            </ActionButton>
          </VStack>
        </section>
      </VStack>
    )
  }

  const renderOperationsPanel = () => (
    <VStack gap="x3" className="w-full">
      <section className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default p-4">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t6Bold">주문 완료 처리</Text>
          <label className="flex w-full flex-col gap-1">
            <span className="text-sm text-fg-neutral-subtle">orderId</span>
            <input
              className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
              type="number"
              inputMode="numeric"
              value={completeOrderIdInput}
              onChange={(event) => setCompleteOrderIdInput(event.target.value)}
            />
          </label>
          <ActionButton loading={completeOrderMutation.isPending} disabled={completeOrderMutation.isPending} onClick={() => completeOrderMutation.mutate()}>
            주문 완료 처리
          </ActionButton>
        </VStack>
      </section>

      <section className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default p-4">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t6Bold">재고 조회 / 추가</Text>
          <label className="flex w-full flex-col gap-1">
            <span className="text-sm text-fg-neutral-subtle">productId</span>
            <input
              className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
              type="number"
              inputMode="numeric"
              value={inventoryProductIdInput}
              onChange={(event) => setInventoryProductIdInput(event.target.value)}
            />
          </label>
          <ActionButton variant="neutralWeak" size="xsmall" onClick={() => void inventoryQuery.refetch()}>
            재고 조회
          </ActionButton>

          {inventoryQuery.data ? (
            <VStack gap="x1" align="flex-start" className="w-full rounded-r2 bg-bg-layer-floating px-3 py-3">
              <Text textStyle="t4Regular">available: {inventoryQuery.data.quantityAvailable}</Text>
              <Text textStyle="t4Regular">reserved: {inventoryQuery.data.quantityReserved}</Text>
              <Text textStyle="t4Regular" color="fg.neutralSubtle">
                updated: {formatDateTime(inventoryQuery.data.updatedAt)}
              </Text>
            </VStack>
          ) : null}

          <label className="flex w-full flex-col gap-1">
            <span className="text-sm text-fg-neutral-subtle">add quantity</span>
            <input
              className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
              type="number"
              inputMode="numeric"
              value={addStockQuantityInput}
              onChange={(event) => setAddStockQuantityInput(event.target.value)}
            />
          </label>
          <ActionButton loading={addStockMutation.isPending} disabled={addStockMutation.isPending} onClick={() => addStockMutation.mutate()}>
            재고 추가
          </ActionButton>
        </VStack>
      </section>

      <section className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default p-4">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t6Bold">상품 등록</Text>
          <label className="flex w-full flex-col gap-1">
            <span className="text-sm text-fg-neutral-subtle">name</span>
            <input
              className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
              value={productNameInput}
              onChange={(event) => setProductNameInput(event.target.value)}
            />
          </label>
          <label className="flex w-full flex-col gap-1">
            <span className="text-sm text-fg-neutral-subtle">description</span>
            <input
              className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
              value={productDescriptionInput}
              onChange={(event) => setProductDescriptionInput(event.target.value)}
            />
          </label>
          <label className="flex w-full flex-col gap-1">
            <span className="text-sm text-fg-neutral-subtle">unitPrice</span>
            <input
              className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
              type="number"
              value={productUnitPriceInput}
              onChange={(event) => setProductUnitPriceInput(event.target.value)}
            />
          </label>

          <ActionButton loading={createProductMutation.isPending} disabled={createProductMutation.isPending} onClick={() => createProductMutation.mutate()}>
            상품 등록
          </ActionButton>
        </VStack>
      </section>
    </VStack>
  )

  if (inventoryQuery.isError && activePanel === 'operations') {
    return <ErrorView message={getErrorMessage(inventoryQuery.error)} onRetry={() => void inventoryQuery.refetch()} />
  }

  return (
    <VStack gap="x5">
      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating px-5 py-6">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t7Bold">관리자 운영 센터</Text>
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            환불, 배송, 주문/재고/상품 작업을 한 곳에서 처리합니다.
          </Text>
          <HStack gap="x2" className="flex-wrap">
            <ActionButton
              aria-pressed={activePanel === 'refunds'}
              variant={activePanel === 'refunds' ? 'neutralSolid' : 'neutralWeak'}
              onClick={() => setActivePanel('refunds')}
            >
              환불 관리
            </ActionButton>
            <ActionButton
              aria-pressed={activePanel === 'deliveries'}
              variant={activePanel === 'deliveries' ? 'neutralSolid' : 'neutralWeak'}
              onClick={() => setActivePanel('deliveries')}
            >
              배송 관리
            </ActionButton>
            <ActionButton
              aria-pressed={activePanel === 'operations'}
              variant={activePanel === 'operations' ? 'neutralSolid' : 'neutralWeak'}
              onClick={() => setActivePanel('operations')}
            >
              운영 도구
            </ActionButton>
          </HStack>
        </VStack>
      </section>

      {actionErrorMessage ? (
        <Text textStyle="t4Regular" color="fg.critical">
          {actionErrorMessage}
        </Text>
      ) : null}

      {activePanel === 'refunds' ? renderRefundPanel() : null}
      {activePanel === 'deliveries' ? renderDeliveryPanel() : null}
      {activePanel === 'operations' ? renderOperationsPanel() : null}
    </VStack>
  )
}

