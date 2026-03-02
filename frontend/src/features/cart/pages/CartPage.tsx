import { ActionButton, HStack, Text, VStack } from '@seed-design/react'
import { useMutation } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useCart } from '@/features/cart/hooks/useCart'
import { orderApi } from '@/features/order/api/orderApi'
import { useAuth } from '@/shared/auth/useAuth'
import { getErrorMessage } from '@/shared/utils/errors'
import { formatCurrency } from '@/shared/utils/format'

const isPurchasable = (item: { status: string; quantityAvailable: number | null }) =>
  item.status === 'ACTIVE' && (item.quantityAvailable === null || item.quantityAvailable > 0)

export const CartPage = () => {
  const navigate = useNavigate()
  const auth = useAuth()
  const cart = useCart()
  const [selectedProductIds, setSelectedProductIds] = useState<number[]>(() =>
    cart.items.filter((item) => isPurchasable(item)).map((item) => item.productId),
  )

  const normalizedSelectedProductIds = useMemo(
    () =>
      selectedProductIds.filter((id) => cart.items.some((item) => item.productId === id && isPurchasable(item))),
    [cart.items, selectedProductIds],
  )

  const selectedItems = useMemo(
    () =>
      cart.items.filter(
        (item) => normalizedSelectedProductIds.includes(item.productId) && isPurchasable(item),
      ),
    [cart.items, normalizedSelectedProductIds],
  )

  const allPurchasableIds = useMemo(
    () => cart.items.filter((item) => isPurchasable(item)).map((item) => item.productId),
    [cart.items],
  )

  const isAllSelected =
    allPurchasableIds.length > 0 && allPurchasableIds.every((id) => normalizedSelectedProductIds.includes(id))

  const selectedTotalAmount = selectedItems.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0)
  const cartTotalAmount = cart.items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0)

  const checkoutMutation = useMutation({
    mutationFn: () =>
      orderApi.createOrder({
        items: selectedItems.map((item) => ({
          productId: item.productId,
          quantity: item.quantity,
        })),
      }),
    onSuccess: (order) => {
      cart.removeItems(selectedItems.map((item) => item.productId))
      setSelectedProductIds((prev) => prev.filter((id) => !selectedItems.some((item) => item.productId === id)))
      navigate(`/checkout/${order.id}`)
    },
  })

  const toggleSelect = (productId: number) => {
    setSelectedProductIds((prev) =>
      prev.includes(productId) ? prev.filter((id) => id !== productId) : [...prev, productId],
    )
  }

  const toggleSelectAll = () => {
    if (isAllSelected) {
      setSelectedProductIds([])
      return
    }
    setSelectedProductIds(allPurchasableIds)
  }

  const handleCheckoutSelected = () => {
    if (selectedItems.length < 1 || checkoutMutation.isPending) {
      return
    }

    if (!auth.isAuthenticated) {
      navigate('/login', { state: { redirectTo: '/cart' } })
      return
    }

    checkoutMutation.mutate()
  }

  if (cart.items.length < 1) {
    return (
      <VStack gap="x4">
        <section className="rounded-r3 border border-dashed border-stroke-neutral-weak bg-bg-layer-floating px-6 py-10 text-center">
          <VStack gap="x2" align="center">
            <Text textStyle="t6Bold">장바구니가 비어 있습니다.</Text>
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              상품을 담은 뒤 선택 결제로 체크아웃할 수 있습니다.
            </Text>
            <Link to="/products">
              <ActionButton>상품 보러가기</ActionButton>
            </Link>
          </VStack>
        </section>
      </VStack>
    )
  }

  return (
    <VStack gap="x5">
      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating px-5 py-6">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t7Bold">장바구니</Text>
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            항목별 수량을 조절하고, 체크박스로 선택한 상품만 결제할 수 있습니다.
          </Text>
          <HStack gap="x2" className="flex-wrap">
            <span className="rounded-r2 bg-bg-neutral-weak px-3 py-1 text-xs font-semibold text-fg-neutral-subtle">
              총 {cart.items.length}개 상품
            </span>
            <span className="rounded-r2 bg-bg-brand-weak px-3 py-1 text-xs font-semibold text-fg-brand">
              선택 {selectedItems.length}개
            </span>
          </HStack>
        </VStack>
      </section>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[2fr_1fr]">
        <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating p-5">
          <VStack gap="x3" align="stretch">
            <HStack justify="space-between" align="center" className="flex-wrap gap-2">
              <label className="inline-flex items-center gap-2 text-sm text-fg-neutral-subtle">
                <input
                  type="checkbox"
                  checked={isAllSelected}
                  onChange={toggleSelectAll}
                  aria-label="전체 선택"
                />
                구매 가능 항목 전체 선택
              </label>
              <HStack gap="x2">
                <ActionButton
                  variant="neutralWeak"
                  size="small"
                  onClick={() => {
                    if (window.confirm('장바구니 항목을 모두 삭제할까요?')) {
                      cart.clear()
                      setSelectedProductIds([])
                    }
                  }}
                >
                  전체 비우기
                </ActionButton>
                <Link to="/products">
                  <ActionButton variant="neutralWeak" size="small">
                    상품 더 보기
                  </ActionButton>
                </Link>
              </HStack>
            </HStack>

            <VStack gap="x3">
              {cart.items.map((item) => {
                const selectable = isPurchasable(item)
                const isChecked = normalizedSelectedProductIds.includes(item.productId)
                const maxQuantity = item.quantityAvailable ?? 99

                return (
                  <article
                    key={item.productId}
                    className="rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default p-4"
                  >
                    <VStack gap="x3" align="stretch">
                      <HStack justify="space-between" align="center" className="gap-3">
                        <label className="inline-flex min-w-0 flex-1 items-center gap-2">
                          <input
                            type="checkbox"
                            checked={isChecked}
                            onChange={() => toggleSelect(item.productId)}
                            disabled={!selectable}
                            aria-label={`${item.name} 선택`}
                          />
                          <VStack gap="x1" align="flex-start" className="min-w-0">
                            <Text textStyle="t5Bold" className="line-clamp-1">
                              {item.name}
                            </Text>
                            <Text textStyle="t4Regular" color="fg.neutralSubtle" className="line-clamp-1">
                              {item.description}
                            </Text>
                          </VStack>
                        </label>
                        <ActionButton
                          variant="neutralWeak"
                          size="small"
                          onClick={() => {
                            if (window.confirm('이 상품을 장바구니에서 제거할까요?')) {
                              cart.removeItem(item.productId)
                              setSelectedProductIds((prev) => prev.filter((id) => id !== item.productId))
                            }
                          }}
                        >
                          삭제
                        </ActionButton>
                      </HStack>

                      <HStack justify="space-between" align="center" className="flex-wrap gap-2">
                        <VStack gap="x1" align="flex-start">
                          <Text textStyle="t4Regular" color="fg.neutralSubtle">
                            단가 {formatCurrency(item.unitPrice)}
                          </Text>
                          <Text textStyle="t4Regular" color={selectable ? 'fg.neutralSubtle' : 'fg.critical'}>
                            {selectable
                              ? item.quantityAvailable === null
                                ? '재고 확인 필요'
                                : `재고 ${item.quantityAvailable}개`
                              : '현재 구매할 수 없는 상품'}
                          </Text>
                        </VStack>

                        <HStack gap="x2" align="center">
                          <ActionButton
                            variant="neutralWeak"
                            size="small"
                            disabled={item.quantity <= 1}
                            onClick={() => cart.updateItemQuantity(item.productId, item.quantity - 1)}
                          >
                            -
                          </ActionButton>
                          <input
                            className="w-[72px] rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default px-x2 py-x1 text-center"
                            type="number"
                            min={1}
                            max={maxQuantity}
                            value={item.quantity}
                            onChange={(event) => cart.updateItemQuantity(item.productId, Number(event.target.value) || 1)}
                            aria-label={`${item.name} 수량`}
                          />
                          <ActionButton
                            variant="neutralWeak"
                            size="small"
                            disabled={item.quantity >= maxQuantity}
                            onClick={() => cart.updateItemQuantity(item.productId, item.quantity + 1)}
                          >
                            +
                          </ActionButton>
                        </HStack>
                      </HStack>
                    </VStack>
                  </article>
                )
              })}
            </VStack>
          </VStack>
        </section>

        <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating p-5">
          <VStack gap="x3" align="stretch">
            <Text textStyle="t5Bold">예상 결제 금액</Text>
            <VStack gap="x1" align="stretch" className="rounded-r2 bg-bg-neutral-weak px-4 py-3">
              <HStack justify="space-between" align="center">
                <Text textStyle="t4Regular" color="fg.neutralSubtle">
                  선택 상품 합계
                </Text>
                <Text textStyle="t5Bold">{formatCurrency(selectedTotalAmount)}</Text>
              </HStack>
              <HStack justify="space-between" align="center">
                <Text textStyle="t4Regular" color="fg.neutralSubtle">
                  장바구니 전체 합계
                </Text>
                <Text textStyle="t4Regular">{formatCurrency(cartTotalAmount)}</Text>
              </HStack>
            </VStack>

            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              선택 항목으로 주문을 생성한 뒤 체크아웃으로 이동합니다.
            </Text>

            <ActionButton
              loading={checkoutMutation.isPending}
              disabled={selectedItems.length < 1 || checkoutMutation.isPending}
              onClick={handleCheckoutSelected}
            >
              선택 상품 결제하기
            </ActionButton>

            {!auth.isAuthenticated ? (
              <Text textStyle="t4Regular" color="fg.neutralSubtle">
                결제를 진행하려면 로그인이 필요합니다.
              </Text>
            ) : null}
          </VStack>
        </section>
      </div>

      {checkoutMutation.isError ? (
        <Text textStyle="t4Regular" color="fg.critical">
          {getErrorMessage(checkoutMutation.error)}
        </Text>
      ) : null}
    </VStack>
  )
}

