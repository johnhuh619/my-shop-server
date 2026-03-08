import { ActionButton, HStack, Text, VStack } from '@seed-design/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { adminApi } from '@/features/admin/api/adminApi'
import type { DeliveryResponse, DeliveryStatus, ProductResponse, RefundStatus } from '@/shared/types/domain'
import { ErrorView } from '@/shared/ui/ErrorView'
import { LoadingView } from '@/shared/ui/LoadingView'
import { StatusChip } from '@/shared/ui/StatusChip'
import { formatDateTime } from '@/shared/utils/date'
import { getErrorMessage } from '@/shared/utils/errors'
import { formatCurrency } from '@/shared/utils/format'

type AdminPanel = 'refunds' | 'deliveries' | 'operations'
type RefundFilter = 'ALL' | RefundStatus
type DeliveryFilter = 'ACTION_REQUIRED' | 'ALL' | DeliveryStatus
type ProductStatusFilter = 'ACTIVE' | 'INACTIVE' | 'ALL'

const panelTabs: { key: AdminPanel; title: string; description: string }[] = [
  {
    key: 'refunds',
    title: '환불 관리',
    description: '요청건 검수, 코멘트 기록, 승인/거절 처리',
  },
  {
    key: 'deliveries',
    title: '배송 관리',
    description: '출고부터 배송 완료까지 상태 전환 관리',
  },
  {
    key: 'operations',
    title: '운영 도구',
    description: '주문 완료, 재고 보정, 상품 등록 작업',
  },
]

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

const productStatusFilters: { key: ProductStatusFilter; label: string }[] = [
  { key: 'ACTIVE', label: '활성' },
  { key: 'INACTIVE', label: '비활성' },
  { key: 'ALL', label: '전체' },
]

const refundStatusPriority: Record<RefundStatus, number> = {
  REQUESTED: 0,
  APPROVED: 1,
  FAILED: 2,
  REJECTED: 3,
  COMPLETED: 4,
}

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

const getChipButtonClass = (selected: boolean) =>
  selected
    ? 'inline-flex h-8 items-center justify-center rounded-r2 border border-stroke-brand-solid bg-bg-brand-weak px-3 text-xs font-semibold text-fg-brand'
    : 'inline-flex h-8 items-center justify-center rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default px-3 text-xs font-medium text-fg-neutral-subtle'

const getPanelTabClass = (selected: boolean) =>
  selected
    ? 'flex min-h-[108px] w-full items-start rounded-r2 border border-stroke-brand-solid bg-bg-brand-weak p-4 text-left'
    : 'flex min-h-[108px] w-full items-start rounded-r2 border border-stroke-neutral-muted bg-bg-layer-default p-4 text-left'

const renderSummaryCard = ({
  label,
  value,
  highlighted = false,
}: {
  label: string
  value: string
  highlighted?: boolean
}) => (
  <VStack
    gap="x1"
    align="flex-start"
    className={`min-h-[88px] rounded-r2 border px-3 py-3 ${
      highlighted ? 'border-stroke-warning-weak bg-bg-warning-weak' : 'border-stroke-neutral-muted bg-bg-layer-floating'
    }`}
  >
    <Text textStyle="t3Regular" color={highlighted ? 'fg.warning' : 'fg.neutralSubtle'} className="leading-tight">
      {label}
    </Text>
    <Text textStyle="t5Bold" className="leading-tight">
      {value}
    </Text>
  </VStack>
)

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
  const [productSearchInput, setProductSearchInput] = useState('')
  const [productSearchKeyword, setProductSearchKeyword] = useState('')
  const [productStatusFilter, setProductStatusFilter] = useState<ProductStatusFilter>('ACTIVE')
  const [productSearchPage, setProductSearchPage] = useState(0)
  const [selectedProductId, setSelectedProductId] = useState('')
  const [selectedProductSnapshot, setSelectedProductSnapshot] = useState<ProductResponse | null>(null)
  const [addStockQuantityInput, setAddStockQuantityInput] = useState('')
  const [editProductNameInput, setEditProductNameInput] = useState('')
  const [editProductDescriptionInput, setEditProductDescriptionInput] = useState('')
  const [editProductUnitPriceInput, setEditProductUnitPriceInput] = useState('')
  const [productNameInput, setProductNameInput] = useState('')
  const [productDescriptionInput, setProductDescriptionInput] = useState('')
  const [productUnitPriceInput, setProductUnitPriceInput] = useState('')

  const operationProductsQuery = useQuery({
    queryKey: ['admin', 'products', productSearchKeyword, productStatusFilter, productSearchPage],
    queryFn: () =>
      adminApi.getProducts({
        page: productSearchPage,
        size: 6,
        keyword: productSearchKeyword || undefined,
        status: productStatusFilter,
      }),
    enabled: activePanel === 'operations',
  })

  const operationProductPage = operationProductsQuery.data
  const operationProducts = operationProductPage?.content ?? []
  const selectedProduct =
    operationProducts.find((product) => String(product.id) === selectedProductId) ??
    (selectedProductSnapshot && String(selectedProductSnapshot.id) === selectedProductId ? selectedProductSnapshot : null)
  const canLookupInventory = !!selectedProduct

  const selectProduct = (product: ProductResponse) => {
    setSelectedProductId(String(product.id))
    setSelectedProductSnapshot(product)
    setEditProductNameInput(product.name)
    setEditProductDescriptionInput(product.description)
    setEditProductUnitPriceInput(String(product.unitPrice))
  }

  const inventoryQuery = useQuery({
    queryKey: ['admin', 'inventory', selectedProduct?.id],
    queryFn: () => adminApi.getInventory(selectedProduct!.id),
    enabled: activePanel === 'operations' && canLookupInventory,
  })

  const adminRefundsQuery = useQuery({
    queryKey: ['admin', 'refunds'],
    queryFn: () => adminApi.getRefunds(),
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
      if (
        !Number.isFinite(orderId) ||
        !createDeliveryRecipientName ||
        !createDeliveryRecipientPhone ||
        !createDeliveryAddress ||
        !createDeliveryZipCode
      ) {
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
      const quantity = Number(addStockQuantityInput)
      if (!selectedProduct || !Number.isFinite(quantity) || quantity <= 0) {
        throw new Error('???? ??????.')
      }
      return adminApi.addStock(selectedProduct.id, { quantity })
    },
    onSuccess: async (inventory) => {
      setAddStockQuantityInput('')
      if (selectedProduct) {
        setSelectedProductSnapshot({
          ...selectedProduct,
          quantityAvailable: inventory.quantityAvailable,
        })
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin', 'inventory'] }),
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['admin', 'products'] }),
      ])
    },
  })

  const updateProductMutation = useMutation({
    mutationFn: () => {
      const unitPrice = Number(editProductUnitPriceInput)
      if (!selectedProduct || !editProductNameInput || !editProductDescriptionInput || !Number.isFinite(unitPrice) || unitPrice <= 0) {
        throw new Error('?? ??? ??????.')
      }
      return adminApi.updateProduct(selectedProduct.id, {
        name: editProductNameInput,
        description: editProductDescriptionInput,
        unitPrice,
      })
    },
    onSuccess: async (product) => {
      setSelectedProductSnapshot(product)
      setEditProductNameInput(product.name)
      setEditProductDescriptionInput(product.description)
      setEditProductUnitPriceInput(String(product.unitPrice))
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['admin', 'products'] }),
      ])
    },
  })

  const deactivateProductMutation = useMutation({
    mutationFn: () => {
      if (!selectedProduct) {
        throw new Error('???? ???? ??????.')
      }
      return adminApi.deactivateProduct(selectedProduct.id)
    },
    onSuccess: async (product) => {
      setSelectedProductSnapshot(product)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['admin', 'products'] }),
      ])
    },
  })

  const activateProductMutation = useMutation({
    mutationFn: () => {
      if (!selectedProduct) {
        throw new Error('활성화할 상품을 선택해주세요.')
      }
      return adminApi.activateProduct(selectedProduct.id)
    },
    onSuccess: async (product) => {
      setSelectedProductSnapshot(product)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['admin', 'products'] }),
      ])
    },
  })

  const createProductMutation = useMutation({
    mutationFn: () => {
      const unitPrice = Number(productUnitPriceInput)
      if (!productNameInput || !productDescriptionInput || !Number.isFinite(unitPrice) || unitPrice <= 0) {
        throw new Error('???? ??????.')
      }
      return adminApi.createProduct({
        name: productNameInput,
        description: productDescriptionInput,
        unitPrice,
      })
    },
    onSuccess: async (product) => {
      setProductNameInput('')
      setProductDescriptionInput('')
      setProductUnitPriceInput('')
      selectProduct(product)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['admin', 'products'] }),
      ])
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
      updateProductMutation.error,
      deactivateProductMutation.error,
      activateProductMutation.error,
      createProductMutation.error,
    ]
    const latest = candidates.find((error) => !!error)
    return latest ? getErrorMessage(latest) : null
  }, [
    addStockMutation.error,
    updateProductMutation.error,
    deactivateProductMutation.error,
    activateProductMutation.error,
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
    const refundCounts = refunds.reduce<Record<RefundStatus, number>>(
      (acc, refund) => {
        acc[refund.status] += 1
        return acc
      },
      {
        REQUESTED: 0,
        APPROVED: 0,
        REJECTED: 0,
        COMPLETED: 0,
        FAILED: 0,
      },
    )

    const filteredRefunds = refunds
      .filter((refund) => (refundFilter === 'ALL' ? true : refund.status === refundFilter))
      .sort((a, b) => {
        const priorityDiff = refundStatusPriority[a.status] - refundStatusPriority[b.status]
        if (priorityDiff !== 0) {
          return priorityDiff
        }
        const updatedDiff = new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
        if (updatedDiff !== 0) {
          return updatedDiff
        }
        return b.id - a.id
      })

    const getFilterCount = (filter: RefundFilter) => {
      if (filter === 'ALL') {
        return refunds.length
      }
      return refundCounts[filter]
    }

    return (
      <VStack gap="x3" className="w-full">
        <section className="rounded-r2 border border-stroke-neutral-muted bg-bg-layer-default p-4">
          <VStack gap="x2" align="flex-start">
            <Text textStyle="t6Bold">환불 큐 요약</Text>
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              요청건을 우선 검수하고, 사유/금액 확인 후 승인 또는 거절을 처리하세요.
            </Text>
            <div className="grid w-full gap-2 sm:grid-cols-2 lg:grid-cols-5">
              {renderSummaryCard({ label: '처리 필요', value: `${refundCounts.REQUESTED}건`, highlighted: true })}
              {renderSummaryCard({ label: '승인', value: `${refundCounts.APPROVED}건` })}
              {renderSummaryCard({ label: '완료', value: `${refundCounts.COMPLETED}건` })}
              {renderSummaryCard({ label: '거절', value: `${refundCounts.REJECTED}건` })}
              {renderSummaryCard({ label: '실패', value: `${refundCounts.FAILED}건` })}
            </div>
          </VStack>
        </section>

        <section className="rounded-r2 border border-stroke-neutral-muted bg-bg-layer-default p-4">
          <VStack gap="x2" align="flex-start">
            <HStack justify="space-between" align="center" className="w-full flex-wrap gap-2">
              <Text textStyle="t5Bold">환불 필터</Text>
              <ActionButton
                variant="neutralWeak"
                size="xsmall"
                loading={adminRefundsQuery.isFetching}
                disabled={adminRefundsQuery.isFetching}
                onClick={() => void adminRefundsQuery.refetch()}
              >
                새로고침
              </ActionButton>
            </HStack>
            <div className="flex w-full flex-wrap gap-2">
              {refundFilters.map((item) => (
                <button
                  key={item.key}
                  type="button"
                  aria-pressed={refundFilter === item.key}
                  className={getChipButtonClass(refundFilter === item.key)}
                  onClick={() => setRefundFilter(item.key)}
                >
                  {item.label} ({getFilterCount(item.key)})
                </button>
              ))}
            </div>
            {adminRefundsQuery.isFetching ? (
              <Text textStyle="t3Regular" color="fg.neutralSubtle">
                최신 환불 상태를 동기화 중...
              </Text>
            ) : null}
          </VStack>
        </section>

        <section className="rounded-r2 border border-stroke-neutral-muted bg-bg-layer-default">
          {filteredRefunds.length === 0 ? (
            <div className="p-4">
              <Text textStyle="t4Regular" color="fg.neutralSubtle">
                선택한 조건의 환불 건이 없습니다.
              </Text>
            </div>
          ) : (
            <ul>
              {filteredRefunds.map((refund) => (
                <li key={refund.id} className="border-t border-stroke-neutral-muted p-4 first:border-t-0">
                  <div className="grid gap-4 xl:grid-cols-[1.3fr_1fr_1.8fr_1.2fr] xl:items-start">
                    <VStack gap="x1" align="flex-start">
                      <Text textStyle="t5Bold">환불 #{refund.id}</Text>
                      <Text textStyle="t3Regular" color="fg.neutralSubtle">
                        주문 #{refund.orderId} / 결제 #{refund.paymentId}
                      </Text>
                      <Text textStyle="t3Regular" color="fg.neutralSubtle">
                        요청: {formatDateTime(refund.createdAt)}
                      </Text>
                      <StatusChip status={refund.status} />
                    </VStack>

                    <VStack gap="x1" align="flex-start">
                      <Text textStyle="t5Bold">{formatCurrency(refund.amount)}</Text>
                      <Text textStyle="t3Regular" color="fg.neutralSubtle">
                        품목 {refund.items.length}개
                      </Text>
                      <Text textStyle="t3Regular" color="fg.neutralSubtle">
                        사유: {refund.reason ?? '-'}
                      </Text>
                    </VStack>

                    <VStack gap="x2" align="flex-start" className="w-full">
                      <label className="flex w-full flex-col gap-1">
                        <span className="text-xs text-fg-neutral-subtle">처리 코멘트</span>
                        <input
                          className="w-full rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
                          value={refundCommentDrafts[refund.id] ?? ''}
                          onChange={(event) =>
                            setRefundCommentDrafts((prev) => ({
                              ...prev,
                              [refund.id]: event.target.value,
                            }))
                          }
                        />
                      </label>
                      <Text textStyle="t3Regular" color="fg.neutralSubtle">
                        기존 코멘트: {refund.adminComment ?? '-'}
                      </Text>
                    </VStack>

                    <VStack gap="x2" align="flex-start" className="w-full">
                      {refund.status === 'REQUESTED' ? (
                        <>
                          <ActionButton
                            className="w-full justify-center"
                            loading={approveRefundMutation.isPending && approveRefundMutation.variables === refund.id}
                            disabled={approveRefundMutation.isPending || rejectRefundMutation.isPending}
                            onClick={() => approveRefundMutation.mutate(refund.id)}
                          >
                            승인
                          </ActionButton>
                          <ActionButton
                            className="w-full justify-center"
                            variant="criticalSolid"
                            loading={rejectRefundMutation.isPending && rejectRefundMutation.variables === refund.id}
                            disabled={approveRefundMutation.isPending || rejectRefundMutation.isPending}
                            onClick={() => rejectRefundMutation.mutate(refund.id)}
                          >
                            거절
                          </ActionButton>
                        </>
                      ) : (
                        <Text textStyle="t3Regular" color="fg.neutralSubtle">
                          처리 완료 건은 상태 변경이 불가합니다.
                        </Text>
                      )}
                    </VStack>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
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
    const staleCount = deliveries.reduce((count, delivery) => {
      if (getDeliveryStaleMessage(delivery, staleReferenceTime)) {
        return count + 1
      }
      return count
    }, 0)

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
        <section className="rounded-r2 border border-stroke-neutral-muted bg-bg-layer-default p-4">
          <VStack gap="x2" align="flex-start">
            <Text textStyle="t6Bold">배송 처리 보드</Text>
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              출고 단계의 병목과 장기 정체 건을 먼저 처리하세요.
            </Text>
            <div className="grid w-full gap-2 sm:grid-cols-2 lg:grid-cols-4">
              {renderSummaryCard({ label: '처리 필요', value: `${actionRequiredCount}건`, highlighted: true })}
              {renderSummaryCard({ label: '지연 의심', value: `${staleCount}건` })}
              {renderSummaryCard({ label: '배송 완료', value: `${deliveryCounts.DELIVERED}건` })}
              {renderSummaryCard({ label: '취소', value: `${deliveryCounts.CANCELED}건` })}
            </div>
            {actionRequiredCount > 0 ? (
              <div className="w-full rounded-r2 border border-stroke-warning-weak bg-bg-warning-weak px-3 py-2">
                <Text textStyle="t3Regular" color="fg.warning">
                  처리 필요 건이 남아 있습니다. 준비중 → 발송됨 → 배송중 → 완료 순으로 처리하세요.
                </Text>
              </div>
            ) : null}
          </VStack>
        </section>

        <section className="rounded-r2 border border-stroke-neutral-muted bg-bg-layer-default p-4">
          <VStack gap="x2" align="flex-start">
            <HStack justify="space-between" align="center" className="w-full flex-wrap gap-2">
              <Text textStyle="t5Bold">배송 필터</Text>
              <ActionButton
                variant="neutralWeak"
                size="xsmall"
                loading={adminDeliveriesQuery.isFetching}
                disabled={adminDeliveriesQuery.isFetching}
                onClick={() => void adminDeliveriesQuery.refetch()}
              >
                새로고침
              </ActionButton>
            </HStack>
            <div className="flex w-full flex-wrap gap-2">
              {deliveryFilters.map((item) => (
                <button
                  key={item.key}
                  type="button"
                  aria-pressed={deliveryFilter === item.key}
                  className={getChipButtonClass(deliveryFilter === item.key)}
                  onClick={() => setDeliveryFilter(item.key)}
                >
                  {item.label} ({getFilterCount(item.key)})
                </button>
              ))}
            </div>
            {adminDeliveriesQuery.isFetching ? (
              <Text textStyle="t3Regular" color="fg.neutralSubtle">
                배송 상태를 동기화 중...
              </Text>
            ) : null}
          </VStack>
        </section>

        {filteredDeliveries.length === 0 ? (
          <section className="rounded-r2 border border-stroke-neutral-muted bg-bg-layer-default p-4">
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              선택한 조건의 배송 데이터가 없습니다.
            </Text>
          </section>
        ) : (
          filteredDeliveries.map((delivery) => {
            const shipDraft = shipDrafts[delivery.id] ?? { carrier: '', trackingNumber: '' }
            const staleMessage = getDeliveryStaleMessage(delivery, staleReferenceTime)

            return (
              <section
                key={delivery.id}
                className={`rounded-r2 border bg-bg-layer-default p-4 ${
                  isActionRequiredDelivery(delivery.status)
                    ? 'border-stroke-warning-weak'
                    : 'border-stroke-neutral-muted'
                }`}
              >
                <div className="grid gap-4 xl:grid-cols-[1.7fr_1fr]">
                  <VStack gap="x2" align="flex-start" className="w-full">
                    <HStack justify="space-between" align="center" className="w-full flex-wrap gap-2">
                      <VStack gap="x1" align="flex-start">
                        <Text textStyle="t5Bold">배송 #{delivery.id}</Text>
                        <Text textStyle="t3Regular" color="fg.neutralSubtle">
                          주문 #{delivery.orderId} / 생성 {formatDateTime(delivery.createdAt)}
                        </Text>
                      </VStack>
                      <StatusChip status={delivery.status} />
                    </HStack>

                    <div className="grid w-full gap-2 md:grid-cols-2">
                      <Text textStyle="t4Regular" color="fg.neutralSubtle">
                        수령인: {delivery.recipientName} ({delivery.recipientPhone})
                      </Text>
                      <Text textStyle="t4Regular" color="fg.neutralSubtle">
                        우편번호: {delivery.zipCode}
                      </Text>
                    </div>

                    <Text textStyle="t4Regular" color="fg.neutralSubtle" className="break-words">
                      주소: {delivery.address} {delivery.addressDetail}
                    </Text>

                    <div className="grid w-full gap-2 md:grid-cols-3">
                      <Text textStyle="t3Regular" color="fg.neutralSubtle">
                        발송: {delivery.shippedAt ? formatDateTime(delivery.shippedAt) : '-'}
                      </Text>
                      <Text textStyle="t3Regular" color="fg.neutralSubtle">
                        배송완료: {delivery.deliveredAt ? formatDateTime(delivery.deliveredAt) : '-'}
                      </Text>
                      <Text textStyle="t3Regular" color="fg.neutralSubtle" className="break-words">
                        택배사/송장: {delivery.carrier ?? '-'} / {delivery.trackingNumber ?? '-'}
                      </Text>
                    </div>

                    {staleMessage ? (
                      <div className="w-full rounded-r2 border border-stroke-warning-weak bg-bg-warning-weak px-3 py-2">
                        <Text textStyle="t3Regular" color="fg.warning">
                          {staleMessage}
                        </Text>
                      </div>
                    ) : null}
                  </VStack>

                  <VStack gap="x2" align="flex-start" className="w-full rounded-r2 bg-bg-layer-floating p-3">
                    <Text textStyle="t4Medium">상태 전환 액션</Text>

                    {delivery.status === 'PREPARING' ? (
                      <>
                        <label className="flex w-full flex-col gap-1">
                          <span className="text-xs text-fg-neutral-subtle">carrier</span>
                          <input
                            className="w-full rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default px-x3 py-x2"
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
                          <span className="text-xs text-fg-neutral-subtle">trackingNumber</span>
                          <input
                            className="w-full rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default px-x3 py-x2"
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
                        <ActionButton
                          className="w-full justify-center"
                          loading={shipDeliveryMutation.isPending && shipDeliveryMutation.variables === delivery.id}
                          disabled={shipDeliveryMutation.isPending}
                          onClick={() => shipDeliveryMutation.mutate(delivery.id)}
                        >
                          발송 처리
                        </ActionButton>
                      </>
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

                    {delivery.status === 'DELIVERED' || delivery.status === 'CANCELED' ? (
                      <Text textStyle="t3Regular" color="fg.neutralSubtle">
                        종료 상태입니다. 추가 상태 전환은 지원하지 않습니다.
                      </Text>
                    ) : (
                      <Text textStyle="t3Regular" color="fg.warning">
                        취소 전 고객 안내/환불 후속 작업 여부를 반드시 확인하세요.
                      </Text>
                    )}
                  </VStack>
                </div>
              </section>
            )
          })
        )}

        <section className="rounded-r2 border border-stroke-neutral-muted bg-bg-layer-default p-4">
          <VStack gap="x2" align="flex-start">
            <Text textStyle="t6Bold">신규 배송 등록</Text>
            <Text textStyle="t3Regular" color="fg.neutralSubtle">
              주문 배송정보를 입력하면 처리 큐에 즉시 반영됩니다.
            </Text>

            <div className="grid w-full gap-2 md:grid-cols-3">
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
            </div>

            <div className="grid w-full gap-2 md:grid-cols-2">
              <label className="flex w-full flex-col gap-1">
                <span className="text-sm text-fg-neutral-subtle">zipCode</span>
                <input
                  className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
                  value={createDeliveryZipCode}
                  onChange={(event) => setCreateDeliveryZipCode(event.target.value)}
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
            </div>

            <label className="flex w-full flex-col gap-1">
              <span className="text-sm text-fg-neutral-subtle">address</span>
              <input
                className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
                value={createDeliveryAddress}
                onChange={(event) => setCreateDeliveryAddress(event.target.value)}
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
      <section className="rounded-r2 border border-stroke-neutral-muted bg-bg-layer-default p-4">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t6Bold">주문 상태 보정</Text>
          <Text textStyle="t3Regular" color="fg.neutralSubtle">
            결제 및 배송 후속 처리가 확인된 주문을 완료 상태로 전환합니다.
          </Text>
          <div className="w-full rounded-r2 border border-stroke-warning-weak bg-bg-warning-weak px-3 py-2">
            <Text textStyle="t3Regular" color="fg.warning">
              이 작업은 고객 주문 화면에 즉시 반영됩니다.
            </Text>
          </div>
          <label className="flex w-full flex-col gap-1">
            <span className="text-sm text-fg-neutral-subtle">주문 ID</span>
            <input className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2" type="number" inputMode="numeric" value={completeOrderIdInput} onChange={(event) => setCompleteOrderIdInput(event.target.value)} placeholder="예: 10024" />
          </label>
          <ActionButton loading={completeOrderMutation.isPending} disabled={completeOrderMutation.isPending} onClick={() => completeOrderMutation.mutate()}>주문 완료 처리</ActionButton>
        </VStack>
      </section>

      <div className="grid gap-3 xl:grid-cols-[minmax(0,1.2fr)_minmax(360px,0.8fr)] xl:items-start">
        <section className="rounded-r2 border border-stroke-neutral-muted bg-bg-layer-default p-4">
          <VStack gap="x3" align="flex-start">
            <VStack gap="x1" align="flex-start">
              <Text textStyle="t6Bold">상품 찾기</Text>
              <Text textStyle="t3Regular" color="fg.neutralSubtle">검색 결과는 짧은 목록으로 유지하고, 실제 작업은 오른쪽 고정 패널에서 처리합니다.</Text>
            </VStack>

            <label className="flex w-full flex-col gap-1">
              <span className="text-sm text-fg-neutral-subtle">검색어</span>
              <div className="flex w-full flex-col gap-2 sm:flex-row sm:items-end">
                <input
                  className="min-w-0 flex-1 rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2"
                  value={productSearchInput}
                  onChange={(event) => setProductSearchInput(event.target.value)}
                  placeholder="상품명, 키워드"
                />
                <ActionButton
                  variant="neutralWeak"
                  size="small"
                  onClick={() => {
                    setProductSearchPage(0)
                    setProductSearchKeyword(productSearchInput.trim())
                  }}
                >
                  검색
                </ActionButton>
              </div>
            </label>

            <VStack gap="x1" align="flex-start" className="w-full">
              <Text textStyle="t3Regular" color="fg.neutralSubtle">상태 필터</Text>
              <HStack gap="x2" className="w-full flex-wrap">
                {productStatusFilters.map((filter) => (
                  <button
                    key={filter.key}
                    type="button"
                    className={getChipButtonClass(productStatusFilter === filter.key)}
                    onClick={() => {
                      setProductStatusFilter(filter.key)
                      setProductSearchPage(0)
                      setSelectedProductId('')
                      setSelectedProductSnapshot(null)
                    }}
                  >
                    {filter.label}
                  </button>
                ))}
              </HStack>
            </VStack>

            <div className="flex w-full items-center justify-between gap-2 rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-3 py-2">
              <Text textStyle="t3Regular" color="fg.neutralSubtle">총 {operationProductPage?.totalElements ?? 0}개, 페이지 {operationProductPage ? operationProductPage.page + 1 : 1} · 필터 {productStatusFilter}</Text>
              <HStack gap="x2">
                <ActionButton variant="neutralWeak" size="xsmall" disabled={!operationProductPage || operationProductPage.page === 0} onClick={() => setProductSearchPage((prev) => Math.max(prev - 1, 0))}>이전</ActionButton>
                <ActionButton variant="neutralWeak" size="xsmall" disabled={!operationProductPage?.hasNext} onClick={() => setProductSearchPage((prev) => prev + 1)}>다음</ActionButton>
              </HStack>
            </div>

            {operationProductsQuery.isLoading ? <Text textStyle="t3Regular">상품 목록을 불러오는 중...</Text> : null}
            {operationProductsQuery.isError ? <Text textStyle="t3Regular" color="fg.critical">{getErrorMessage(operationProductsQuery.error)}</Text> : null}

            {!operationProductsQuery.isLoading && operationProducts.length === 0 ? (
              <div className="w-full rounded-r2 border border-dashed border-stroke-neutral-weak bg-bg-layer-floating px-4 py-5">
                <Text textStyle="t3Regular" color="fg.neutralSubtle">조건에 맞는 상품이 없습니다.</Text>
              </div>
            ) : null}

            {operationProducts.length > 0 ? (
              <div className="max-h-[520px] w-full overflow-y-auto rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating p-2">
                <VStack gap="x2" align="stretch">
                  {operationProducts.map((product) => {
                    const isSelected = String(product.id) === selectedProductId
                    return (
                      <button key={product.id} type="button" className={`rounded-r2 border px-4 py-3 text-left transition-colors ${isSelected ? 'border-stroke-brand-solid bg-bg-brand-weak' : 'border-stroke-neutral-subtle bg-bg-layer-default hover:border-stroke-neutral-solid'}`} onClick={() => selectProduct(product)}>
                        <VStack gap="x1" align="flex-start">
                          <HStack justify="space-between" align="center" className="w-full gap-2">
                            <Text textStyle="t5Bold" className="truncate">{product.name}</Text>
                            <span className="rounded-r2 bg-bg-neutral-weak px-2 py-1 text-xs font-semibold text-fg-neutral-subtle">#{product.id}</span>
                          </HStack>
                          <Text textStyle="t3Regular" color="fg.neutralSubtle">{formatCurrency(product.unitPrice)} · 상태 {product.status}</Text>
                          <Text textStyle="t3Regular" color="fg.neutralSubtle">가용 재고 {product.quantityAvailable ?? '확인 필요'}</Text>
                        </VStack>
                      </button>
                    )
                  })}
                </VStack>
              </div>
            ) : null}
          </VStack>
        </section>

        <VStack gap="x3" className="xl:sticky xl:top-4">
          <section className="rounded-r2 border border-stroke-neutral-muted bg-bg-layer-default p-4">
            <VStack gap="x3" align="flex-start">
              <Text textStyle="t6Bold">선택 상품 작업</Text>
              <Text textStyle="t3Regular" color="fg.neutralSubtle">선택한 상품의 재고 보정, 상품 정보 수정, 상태 전환을 이 패널에서 처리합니다.</Text>

              {selectedProduct ? (
                <div className="w-full rounded-r2 border border-stroke-brand-solid bg-bg-brand-weak px-4 py-3">
                  <VStack gap="x1" align="flex-start">
                    <Text textStyle="t5Bold">{selectedProduct.name}</Text>
                    <Text textStyle="t3Regular" color="fg.neutralSubtle">ID {selectedProduct.id} | {formatCurrency(selectedProduct.unitPrice)} · 상태 {selectedProduct.status}</Text>
                  </VStack>
                </div>
              ) : (
                <div className="w-full rounded-r2 border border-dashed border-stroke-neutral-weak bg-bg-layer-floating px-4 py-5">
                  <Text textStyle="t3Regular" color="fg.neutralSubtle">왼쪽 목록에서 상품을 선택하면 여기에서 작업할 수 있습니다.</Text>
                </div>
              )}

              {inventoryQuery.isLoading ? <Text textStyle="t3Regular">재고 정보를 불러오는 중...</Text> : null}
              {inventoryQuery.isError ? <Text textStyle="t3Regular" color="fg.critical">{getErrorMessage(inventoryQuery.error)}</Text> : null}

              {inventoryQuery.data ? (
                <div className="grid w-full gap-2 sm:grid-cols-3 xl:grid-cols-1">
                  <div className="rounded-r2 bg-bg-layer-floating px-3 py-3">
                    <Text textStyle="t3Regular" color="fg.neutralSubtle">가용 재고</Text>
                    <Text textStyle="t5Bold">{inventoryQuery.data.quantityAvailable}</Text>
                  </div>
                  <div className="rounded-r2 bg-bg-layer-floating px-3 py-3">
                    <Text textStyle="t3Regular" color="fg.neutralSubtle">예약 재고</Text>
                    <Text textStyle="t5Bold">{inventoryQuery.data.quantityReserved}</Text>
                  </div>
                  <div className="rounded-r2 bg-bg-layer-floating px-3 py-3">
                    <Text textStyle="t3Regular" color="fg.neutralSubtle">최근 갱신</Text>
                    <Text textStyle="t4Regular">{formatDateTime(inventoryQuery.data.updatedAt)}</Text>
                  </div>
                </div>
              ) : null}

              <label className="flex w-full flex-col gap-1">
                <span className="text-sm text-fg-neutral-subtle">추가 입고 수량</span>
                <input className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2" type="number" inputMode="numeric" value={addStockQuantityInput} onChange={(event) => setAddStockQuantityInput(event.target.value)} placeholder="예: 20" disabled={!selectedProduct} />
              </label>
              <ActionButton loading={addStockMutation.isPending} disabled={addStockMutation.isPending || !selectedProduct} onClick={() => addStockMutation.mutate()}>재고 추가</ActionButton>

              <div className="h-px w-full bg-stroke-neutral-muted" />

              <label className="flex w-full flex-col gap-1">
                <span className="text-sm text-fg-neutral-subtle">상품명</span>
                <input className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2" value={editProductNameInput} onChange={(event) => setEditProductNameInput(event.target.value)} placeholder="상품명을 입력하세요" disabled={!selectedProduct} />
              </label>
              <label className="flex w-full flex-col gap-1">
                <span className="text-sm text-fg-neutral-subtle">상품 설명</span>
                <input className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2" value={editProductDescriptionInput} onChange={(event) => setEditProductDescriptionInput(event.target.value)} placeholder="상품 설명을 입력하세요" disabled={!selectedProduct} />
              </label>
              <label className="flex w-full flex-col gap-1">
                <span className="text-sm text-fg-neutral-subtle">판매가</span>
                <input className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2" type="number" inputMode="numeric" value={editProductUnitPriceInput} onChange={(event) => setEditProductUnitPriceInput(event.target.value)} placeholder="예: 19900" disabled={!selectedProduct} />
              </label>

              <HStack gap="x2" className="w-full flex-wrap">
                <ActionButton loading={updateProductMutation.isPending} disabled={updateProductMutation.isPending || !selectedProduct} onClick={() => updateProductMutation.mutate()}>상품 수정</ActionButton>
                {selectedProduct?.status === 'INACTIVE' ? (
                  <ActionButton variant="brandSolid" loading={activateProductMutation.isPending} disabled={activateProductMutation.isPending || !selectedProduct} onClick={() => { if (!selectedProduct || !window.confirm(`'${selectedProduct.name}' 상품을 다시 활성화하시겠습니까?`)) { return } activateProductMutation.mutate() }}>상품 활성화</ActionButton>
                ) : (
                  <ActionButton variant="criticalSolid" loading={deactivateProductMutation.isPending} disabled={deactivateProductMutation.isPending || !selectedProduct || selectedProduct.status !== 'ACTIVE'} onClick={() => { if (!selectedProduct || !window.confirm(`'${selectedProduct.name}' 상품을 비활성화하시겠습니까?`)) { return } deactivateProductMutation.mutate() }}>상품 비활성화</ActionButton>
                )}
              </HStack>
            </VStack>
          </section>

          <section className="rounded-r2 border border-stroke-neutral-muted bg-bg-layer-default p-4">
            <VStack gap="x3" align="flex-start">
              <Text textStyle="t6Bold">상품 등록</Text>
              <Text textStyle="t3Regular" color="fg.neutralSubtle">신규 상품을 등록한 뒤 바로 위 패널에서 재고를 보정할 수 있습니다.</Text>
              <label className="flex w-full flex-col gap-1">
                <span className="text-sm text-fg-neutral-subtle">상품명</span>
                <input className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2" value={productNameInput} onChange={(event) => setProductNameInput(event.target.value)} placeholder="예: 스테인리스 텀블러" />
              </label>
              <label className="flex w-full flex-col gap-1">
                <span className="text-sm text-fg-neutral-subtle">상품 설명</span>
                <input className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2" value={productDescriptionInput} onChange={(event) => setProductDescriptionInput(event.target.value)} placeholder="핵심 판매 포인트를 입력하세요" />
              </label>
              <label className="flex w-full flex-col gap-1">
                <span className="text-sm text-fg-neutral-subtle">판매가</span>
                <input className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-floating px-x3 py-x2" type="number" inputMode="numeric" value={productUnitPriceInput} onChange={(event) => setProductUnitPriceInput(event.target.value)} placeholder="예: 25900" />
              </label>
              <ActionButton loading={createProductMutation.isPending} disabled={createProductMutation.isPending} onClick={() => createProductMutation.mutate()}>상품 등록</ActionButton>
            </VStack>
          </section>
        </VStack>
      </div>
    </VStack>
  )


  return (
    <VStack gap="x5" className="w-full">
      <section className="rounded-r3 border border-stroke-neutral-muted bg-bg-layer-floating px-5 py-6">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t7Bold">관리자 운영 센터</Text>
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            환불, 배송, 주문 상태와 운영 작업을 한 화면에서 확인하고 처리할 수 있습니다.
          </Text>

          <div className="mt-2 grid w-full gap-2 md:grid-cols-3">
            {panelTabs.map((panel) => (
              <button
                key={panel.key}
                type="button"
                aria-pressed={activePanel === panel.key}
                className={getPanelTabClass(activePanel === panel.key)}
                onClick={() => setActivePanel(panel.key)}
              >
                <VStack gap="x1" align="flex-start" className="w-full">
                  <Text textStyle="t5Bold" className="leading-tight">
                    {panel.title}
                  </Text>
                  <Text textStyle="t3Regular" color="fg.neutralSubtle" className="leading-tight">
                    {panel.description}
                  </Text>
                </VStack>
              </button>
            ))}
          </div>
        </VStack>
      </section>

      {actionErrorMessage ? (
        <section className="rounded-r2 border border-stroke-critical-weak bg-bg-critical-weak px-4 py-3">
          <Text textStyle="t4Regular" color="fg.critical">
            {actionErrorMessage}
          </Text>
        </section>
      ) : null}

      {activePanel === 'refunds' ? renderRefundPanel() : null}
      {activePanel === 'deliveries' ? renderDeliveryPanel() : null}
      {activePanel === 'operations' ? renderOperationsPanel() : null}
    </VStack>
  )
}
