import { ActionButton, HStack, Text, VStack } from '@seed-design/react'
import type { ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useCart } from '@/features/cart/hooks/useCart'
import { useAuth } from '@/shared/auth/useAuth'

const navLinkClassName = ({ isActive }: { isActive: boolean }) =>
  isActive
    ? 'shrink-0 rounded-r2 border border-stroke-neutral-weak bg-bg-layer-floating px-x3 py-x2 text-sm font-semibold text-fg-neutral'
    : 'shrink-0 rounded-r2 border border-transparent px-x3 py-x2 text-sm font-medium text-fg-neutral-subtle hover:border-stroke-neutral-subtle hover:bg-bg-neutral-weak'

const navigationItems = [
  { to: '/products', label: '상품' },
  { to: '/cart', label: '장바구니' },
  { to: '/me', label: '내정보' },
]

export const AppLayout = ({ children }: { children: ReactNode }) => {
  const auth = useAuth()
  const cart = useCart()
  const navigate = useNavigate()
  const navItems = auth.isAdmin ? [...navigationItems, { to: '/admin', label: '관리자' }] : navigationItems

  const handleLogout = async () => {
    try {
      await auth.logout()
    } finally {
      navigate('/login')
    }
  }

  return (
    <VStack minHeight="100dvh" bg="bg.layerDefault">
      <header className="border-b border-stroke-neutral-subtle bg-bg-layer-floating/95 backdrop-blur">
        <div className="mx-auto flex w-full max-w-[1280px] flex-col px-4 sm:px-6">
          <HStack justify="space-between" align="center" py="x3">
            <VStack gap="x1" align="flex-start">
              <Text textStyle="t6Bold" color="fg.neutral">
                Mini Shop
              </Text>
              <Text textStyle="t3Regular" color="fg.neutralSubtle">
                FASHION COMMERCE FRONT
              </Text>
            </VStack>

            {!auth.isAuthenticated ? (
              <HStack gap="x2" align="center">
                <ActionButton variant="neutralWeak" size="small" onClick={() => navigate('/cart')}>
                  장바구니 {cart.totalQuantity > 0 ? `(${cart.totalQuantity})` : ''}
                </ActionButton>
                <ActionButton variant="neutralWeak" size="small" onClick={() => navigate('/login')}>
                  로그인
                </ActionButton>
              </HStack>
            ) : (
              <HStack gap="x2" align="center">
                <ActionButton variant="neutralWeak" size="small" onClick={() => navigate('/cart')}>
                  장바구니 {cart.totalQuantity > 0 ? `(${cart.totalQuantity})` : ''}
                </ActionButton>
                <Text textStyle="t3Regular" color="fg.neutralSubtle">
                  {auth.name ?? auth.email ?? '사용자'}
                </Text>
                <ActionButton variant="neutralWeak" size="small" onClick={() => void handleLogout()}>
                  로그아웃
                </ActionButton>
              </HStack>
            )}
          </HStack>

          {auth.isAuthenticated ? (
            <nav className="flex gap-2 overflow-x-auto pb-3">
              {navItems.map((item) => (
                <NavLink key={item.to} to={item.to} className={navLinkClassName}>
                  {item.label}
                </NavLink>
              ))}
            </nav>
          ) : null}
        </div>
      </header>

      <div className="relative mx-auto w-full max-w-[1280px] flex-1 px-4 pb-10 pt-6 sm:px-6">
        <div className="pointer-events-none absolute inset-x-0 top-0 h-32 bg-gradient-to-b from-[#e8eef6] to-transparent" />
        <VStack as="main" gap="x4" className="relative">
          {children}
        </VStack>
      </div>
    </VStack>
  )
}

