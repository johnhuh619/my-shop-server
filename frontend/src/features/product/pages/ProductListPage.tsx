import { ActionButton, HStack, Text, VStack } from '@seed-design/react'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useCart } from '@/features/cart/hooks/useCart'
import { productApi } from '@/features/product/api/productApi'
import { useAuth } from '@/shared/auth/useAuth'
import { ErrorView } from '@/shared/ui/ErrorView'
import { LoadingView } from '@/shared/ui/LoadingView'
import { getErrorMessage } from '@/shared/utils/errors'
import { formatCurrency } from '@/shared/utils/format'

const PAGE_SIZE = 12

const getStockMeta = (quantity: number | null) => {
  if (quantity === null) {
    return {
      label: '재고 확인 필요',
      className: 'bg-bg-warning-weak text-fg-warning',
    }
  }

  if (quantity < 1) {
    return {
      label: '품절',
      className: 'bg-bg-critical-weak text-fg-critical',
    }
  }

  if (quantity < 5) {
    return {
      label: `재고 ${quantity}개`,
      className: 'bg-bg-warning-weak text-fg-warning',
    }
  }

  return {
    label: `재고 ${quantity}개`,
    className: 'bg-bg-positive-weak text-fg-positive',
  }
}

export const ProductListPage = () => {
  const auth = useAuth()
  const cart = useCart()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const page = Number(searchParams.get('page') || '0')
  const keyword = searchParams.get('keyword') || ''
  const [keywordInput, setKeywordInput] = useState(keyword)

  const productsQuery = useQuery({
    queryKey: ['products', page, keyword],
    queryFn: () => productApi.getProducts({ page, size: PAGE_SIZE, keyword: keyword || undefined }),
  })

  const applySearch = () => {
    const next = new URLSearchParams(searchParams)
    if (keywordInput) {
      next.set('keyword', keywordInput)
    } else {
      next.delete('keyword')
    }
    next.set('page', '0')
    setSearchParams(next)
  }

  if (productsQuery.isLoading) {
    return <LoadingView message="상품 목록을 불러오는 중..." />
  }

  if (productsQuery.isError) {
    return <ErrorView message={getErrorMessage(productsQuery.error)} onRetry={() => void productsQuery.refetch()} />
  }

  const productPage = productsQuery.data
  if (!productPage) {
    return <LoadingView message="상품 목록을 불러오는 중..." />
  }

  return (
    <VStack gap="x5">
      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating px-5 py-6 sm:px-7">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t7Bold">큐레이션 상품</Text>
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            빠르게 비교하고 장바구니에 담아 결제할 수 있게 가격과 재고를 우선 배치했습니다.
          </Text>
          <HStack gap="x2" align="center">
            <span className="rounded-r2 bg-bg-neutral-weak px-3 py-1 text-xs font-semibold text-fg-neutral-subtle">
              총 {productPage.totalElements}개 상품
            </span>
            <span className="text-xs text-fg-neutral-subtle">
              페이지 {productPage.page + 1} / {Math.max(productPage.totalPages, 1)}
            </span>
          </HStack>
        </VStack>
      </section>

      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating px-5 py-4">
        <HStack gap="x2">
          <input
            className="w-full rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default px-x3 py-x2"
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
            placeholder="상품명 검색"
          />
          <ActionButton variant="neutralSolid" onClick={applySearch}>
            검색
          </ActionButton>
        </HStack>
      </section>

      {productPage.content.length === 0 ? (
        <section className="rounded-r3 border border-dashed border-stroke-neutral-weak bg-bg-layer-floating px-6 py-10 text-center">
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            조건에 맞는 상품이 없습니다.
          </Text>
        </section>
      ) : (
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-4">
          {productPage.content.map((product) => {
            const stockMeta = getStockMeta(product.quantityAvailable)
            const isOutOfStock = product.quantityAvailable !== null && product.quantityAvailable < 1

            return (
              <article
                key={product.id}
                role="link"
                tabIndex={0}
                onClick={() => navigate(`/products/${product.id}`)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    navigate(`/products/${product.id}`)
                  }
                }}
                className="group cursor-pointer overflow-hidden rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating transition-colors hover:border-stroke-neutral-solid"
              >
                <div className="relative w-full overflow-hidden bg-bg-neutral-weak" style={{ aspectRatio: '4 / 4.2' }}>
                  <div className="absolute left-2 top-2 z-10 flex flex-wrap items-center gap-1">
                    <span className={`rounded-r2 px-2 py-1 text-xs font-semibold ${stockMeta.className}`}>{stockMeta.label}</span>
                    <span className="rounded-r2 bg-bg-layer-default px-2 py-1 text-xs font-semibold text-fg-neutral-subtle">#{product.id}</span>
                  </div>

                  <div className="flex h-full w-full items-center justify-center text-fg-neutral-subtle">
                    <Text textStyle="t3Regular" color="fg.neutralSubtle">
                      PRODUCT IMAGE
                    </Text>
                  </div>

                  <span className="absolute bottom-2 right-2 z-10 rounded-full border border-stroke-neutral-subtle bg-bg-layer-default/90 px-2 py-1 text-sm text-fg-neutral-subtle">
                    ♡
                  </span>

                  {auth.isAuthenticated ? (
                    <div className="pointer-events-none absolute inset-x-0 bottom-3 z-20 flex justify-center opacity-0 transition-opacity duration-200 group-hover:opacity-100 group-hover:pointer-events-auto group-focus-within:opacity-100 group-focus-within:pointer-events-auto">
                      <ActionButton
                        size="small"
                        disabled={isOutOfStock}
                        onClick={(event) => {
                          event.stopPropagation()
                          cart.addItem({
                            productId: product.id,
                            name: product.name,
                            description: product.description,
                            unitPrice: product.unitPrice,
                            status: product.status,
                            quantityAvailable: product.quantityAvailable,
                            quantity: 1,
                          })
                          navigate('/cart')
                        }}
                      >
                        장바구니 담기
                      </ActionButton>
                    </div>
                  ) : (
                    <div className="pointer-events-none absolute inset-x-0 bottom-3 z-20 flex justify-center opacity-0 transition-opacity duration-200 group-hover:opacity-100 group-hover:pointer-events-auto group-focus-within:opacity-100 group-focus-within:pointer-events-auto">
                      <ActionButton
                        size="small"
                        variant="brandOutline"
                        onClick={(event) => {
                          event.stopPropagation()
                          navigate('/login')
                        }}
                      >
                        로그인 후 주문
                      </ActionButton>
                    </div>
                  )}
                </div>

                <VStack gap="x1" align="flex-start" className="w-full px-3 pb-3 pt-2">
                  <Text textStyle="t5Bold" className="line-clamp-2 min-h-[46px] text-fg-neutral group-hover:text-fg-brand">
                    {product.name}
                  </Text>
                  <Text textStyle="t7Bold" color="fg.neutral">
                    {formatCurrency(product.unitPrice)}
                  </Text>
                  <Text textStyle="t3Regular" color="fg.neutralSubtle">
                    1개 기준 가격
                  </Text>
                </VStack>
              </article>
            )
          })}
        </div>
      )}

      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating px-5 py-4">
        <HStack justify="space-between" align="center">
          <Text textStyle="t3Regular" color="fg.neutralSubtle">
            다음 페이지로 이동해 더 많은 상품을 확인하세요.
          </Text>
          <HStack gap="x2">
            <ActionButton
              variant="neutralWeak"
              disabled={productPage.page === 0}
              onClick={() => {
                const next = new URLSearchParams(searchParams)
                next.set('page', String(Math.max(productPage.page - 1, 0)))
                setSearchParams(next)
              }}
            >
              이전
            </ActionButton>
            <ActionButton
              variant="neutralWeak"
              disabled={!productPage.hasNext}
              onClick={() => {
                const next = new URLSearchParams(searchParams)
                next.set('page', String(productPage.page + 1))
                setSearchParams(next)
              }}
            >
              다음
            </ActionButton>
          </HStack>
        </HStack>
      </section>
    </VStack>
  )
}
