import { Text, VStack } from '@seed-design/react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'

type MySection = {
  to: string
  label: string
  description: string
}

const sections: MySection[] = [
  {
    to: 'account',
    label: '계정정보',
    description: '계정 상태와 기본 프로필을 확인하고 관리합니다.',
  },
  {
    to: 'orders',
    label: '주문',
    description: '주문 상세에서 결제/환불/배송 흐름까지 이어서 처리합니다.',
  },
  {
    to: 'deliveries',
    label: '배송',
    description: '배송 상태를 주기적으로 확인하고 주문 상세로 연결합니다.',
  },
]

const navClassName = ({ isActive }: { isActive: boolean }) =>
  isActive
    ? 'shrink-0 rounded-r2 border border-stroke-brand-solid bg-bg-brand-weak px-x3 py-x2 text-sm font-semibold text-fg-brand'
    : 'shrink-0 rounded-r2 border border-stroke-neutral-subtle bg-bg-layer-default px-x3 py-x2 text-sm font-medium text-fg-neutral-subtle hover:border-stroke-neutral-weak'

export const MyPageLayout = () => {
  const location = useLocation()

  const orderSection = sections.find((section) => section.to === 'orders') ?? sections[0]
  const currentSection =
    sections.find(
      (section) =>
        location.pathname === `/me/${section.to}` || location.pathname.startsWith(`/me/${section.to}/`),
    ) ??
    (location.pathname.startsWith('/me/payments') || location.pathname.startsWith('/me/refunds')
      ? orderSection
      : sections[0])

  return (
    <VStack gap="x5">
      <section className="rounded-r3 border border-stroke-neutral-subtle bg-bg-layer-floating px-5 py-6">
        <VStack gap="x3" align="flex-start">
          <VStack gap="x1" align="flex-start">
            <Text textStyle="t7Bold">내정보</Text>
            <Text textStyle="t4Regular" color="fg.neutralSubtle">
              주문 중심으로 결제/환불/배송/계정정보를 관리합니다.
            </Text>
          </VStack>

          <nav className="w-full overflow-x-auto" aria-label="내정보 섹션">
            <div className="flex min-w-max gap-2">
              {sections.map((section) => (
                <NavLink key={section.to} to={section.to} className={navClassName}>
                  {section.label}
                </NavLink>
              ))}
            </div>
          </nav>

          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            현재 섹션: {currentSection.label} · {currentSection.description}
          </Text>
        </VStack>
      </section>

      <Outlet />
    </VStack>
  )
}

