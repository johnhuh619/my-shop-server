import { ActionButton, HStack, Text, VStack } from '@seed-design/react'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useCart } from '@/features/cart/hooks/useCart'
import { productApi } from '@/features/product/api/productApi'
import { useAuth } from '@/shared/auth/useAuth'
import { ErrorView } from '@/shared/ui/ErrorView'
import { LoadingView } from '@/shared/ui/LoadingView'
import { getErrorMessage } from '@/shared/utils/errors'
import { formatCurrency } from '@/shared/utils/format'

export const ProductDetailPage = () => {
  const { productId } = useParams()
  const id = Number(productId)
  const auth = useAuth()
  const cart = useCart()
  const navigate = useNavigate()
  const [quantity, setQuantity] = useState(1)
  const [isCartModalOpen, setIsCartModalOpen] = useState(false)

  const productQuery = useQuery({
    queryKey: ['product', id],
    queryFn: () => productApi.getProduct(id),
    enabled: Number.isFinite(id),
  })

  if (!Number.isFinite(id)) {
    return <ErrorView message="잘못된 상품 경로입니다." />
  }

  if (productQuery.isLoading) {
    return <LoadingView message="상품 상세를 불러오는 중..." />
  }

  if (productQuery.isError) {
    return <ErrorView message={getErrorMessage(productQuery.error)} onRetry={() => void productQuery.refetch()} />
  }

  const product = productQuery.data
  if (!product) {
    return <LoadingView message="상품 상세를 불러오는 중..." />
  }

  const maxQuantity = product.quantityAvailable ?? 99
  const isOutOfStock = product.quantityAvailable !== null && product.quantityAvailable < 1
  const estimatedAmount = product.unitPrice * quantity

  const updateQuantity = (value: number) => {
    const next = Math.max(1, value)
    if (product.quantityAvailable !== null) {
      setQuantity(Math.min(next, maxQuantity))
      return
    }
    setQuantity(next)
  }

  const handleBuyNow = () => {
    if (!auth.isAuthenticated) {
      navigate('/login', { state: { redirectTo: `/products/${id}` } })
      return
    }

    cart.addItem({
      productId: product.id,
      name: product.name,
      description: product.description,
      unitPrice: product.unitPrice,
      status: product.status,
      quantityAvailable: product.quantityAvailable,
      quantity,
    })
    navigate('/cart')
  }

  const handleAddCart = () => {
    cart.addItem({
      productId: product.id,
      name: product.name,
      description: product.description,
      unitPrice: product.unitPrice,
      status: product.status,
      quantityAvailable: product.quantityAvailable,
      quantity,
    })
    setIsCartModalOpen(true)
  }

  return (
    <>
      <VStack gap="x5">
        <HStack justify="space-between" align="center">
          <VStack gap="x1" align="flex-start">
            <Text textStyle="t7Bold">상품 상세</Text>
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              장바구니에 담은 뒤 배송 정보를 입력하고 결제를 진행하세요.
            </Text>
          </VStack>
          <Link to="/products" className="text-sm font-medium text-fg-neutral-subtle underline">
            목록으로
          </Link>
        </HStack>

        <div className="grid grid-cols-1 gap-4 lg:grid-cols-[1.1fr_1fr]">
          <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating p-6">
            <div className="flex h-full min-h-[360px] w-full items-center justify-center rounded-r2 bg-gradient-to-br from-bg-brand-weak to-bg-layer-default">
              <span className="text-sm font-semibold tracking-[0.16em] text-fg-neutral-subtle">PRODUCT IMAGE</span>
            </div>
          </section>

          <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating p-5">
            <VStack gap="x4" align="flex-start" className="w-full">
              <HStack justify="space-between" align="center" className="w-full">
                <Text textStyle="t3Regular" color="fg.neutralSubtle">
                  PRODUCT #{product.id}
                </Text>
                <span className="rounded-r2 bg-bg-neutral-weak px-2 py-1 text-xs font-semibold text-fg-neutral-subtle">
                  {product.status}
                </span>
              </HStack>

              <VStack gap="x2" align="flex-start" className="w-full">
                <Text textStyle="t6Bold">{product.name}</Text>
                <Text textStyle="t4Regular" color="fg.neutralSubtle">
                  {product.description}
                </Text>
              </VStack>

              <VStack gap="x1" align="flex-start" className="w-full rounded-r2 bg-bg-neutral-weak px-4 py-3">
                <Text textStyle="t4Regular" color="fg.neutralSubtle">
                  상품 가격
                </Text>
                <Text textStyle="t7Bold">{formatCurrency(product.unitPrice)}</Text>
                <Text textStyle="t4Regular" color={isOutOfStock ? 'fg.critical' : 'fg.positive'}>
                  {isOutOfStock
                    ? '품절'
                    : product.quantityAvailable === null
                      ? '재고 확인 필요'
                      : `재고 ${product.quantityAvailable}개`}
                </Text>
              </VStack>

              <VStack gap="x2" align="flex-start" className="w-full">
                <Text textStyle="t4Regular" color="fg.neutralSubtle">
                  수량 선택
                </Text>
                <HStack gap="x2" align="center" className="w-full">
                  <ActionButton variant="neutralWeak" disabled={quantity <= 1} onClick={() => updateQuantity(quantity - 1)}>
                    -
                  </ActionButton>
                  <input
                    id="quantity"
                    className="w-full rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default px-x3 py-x2 text-center"
                    type="number"
                    min={1}
                    max={maxQuantity}
                    value={quantity}
                    onChange={(event) => updateQuantity(Number(event.target.value) || 1)}
                    aria-label="구매 수량"
                  />
                  <ActionButton
                    variant="neutralWeak"
                    disabled={product.quantityAvailable !== null && quantity >= maxQuantity}
                    onClick={() => updateQuantity(quantity + 1)}
                  >
                    +
                  </ActionButton>
                </HStack>
              </VStack>

              <VStack gap="x1" align="flex-start" className="w-full rounded-r2 bg-bg-brand-weak px-4 py-3">
                <Text textStyle="t4Regular" color="fg.neutralSubtle">
                  예상 결제 금액
                </Text>
                <Text textStyle="t7Bold">{formatCurrency(estimatedAmount)}</Text>
              </VStack>

              <div className="grid w-full grid-cols-1 gap-2 sm:grid-cols-2">
                <ActionButton
                  className="w-full"
                  disabled={
                    isOutOfStock ||
                    (product.quantityAvailable !== null && product.quantityAvailable < quantity)
                  }
                  onClick={handleBuyNow}
                >
                  {auth.isAuthenticated ? '장바구니로 결제' : '로그인 후 결제'}
                </ActionButton>
                <ActionButton
                  className="w-full"
                  variant="neutralWeak"
                  disabled={isOutOfStock || (product.quantityAvailable !== null && product.quantityAvailable < quantity)}
                  onClick={handleAddCart}
                >
                  장바구니
                </ActionButton>
              </div>

              {isOutOfStock ? (
                <Text textStyle="t3Regular" color="fg.critical">
                  현재 품절 상태입니다. 재입고 후 주문할 수 있습니다.
                </Text>
              ) : null}

            </VStack>
          </section>
        </div>
      </VStack>

      {isCartModalOpen ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 px-4"
          role="dialog"
          aria-modal="true"
          aria-label="장바구니 담기 완료"
        >
          <section className="w-full max-w-[420px] rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating p-5 shadow-lg">
            <VStack gap="x3" align="flex-start">
              <Text textStyle="t6Bold">장바구니에 담았습니다.</Text>
              <Text textStyle="t4Regular" color="fg.neutralSubtle">
                장바구니 페이지로 이동하거나 계속 상품을 둘러볼 수 있습니다.
              </Text>
              <div className="grid w-full grid-cols-1 gap-2 sm:grid-cols-2">
                <ActionButton
                  className="w-full"
                  onClick={() => {
                    setIsCartModalOpen(false)
                    navigate('/cart')
                  }}
                >
                  장바구니 이동
                </ActionButton>
                <ActionButton
                  className="w-full"
                  variant="neutralWeak"
                  onClick={() => {
                    setIsCartModalOpen(false)
                    navigate('/products')
                  }}
                >
                  상품 페이지 이동
                </ActionButton>
              </div>
            </VStack>
          </section>
        </div>
      ) : null}
    </>
  )
}

