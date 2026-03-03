import { ActionButton, HStack, Text, VStack } from '@seed-design/react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { userApi } from '@/features/user/api/userApi'
import { useAuth } from '@/shared/auth/useAuth'
import { ErrorView } from '@/shared/ui/ErrorView'
import { LoadingView } from '@/shared/ui/LoadingView'
import { formatDateTime } from '@/shared/utils/date'
import { getErrorMessage } from '@/shared/utils/errors'

export const MyPage = () => {
  const auth = useAuth()
  const navigate = useNavigate()

  const meQuery = useQuery({
    queryKey: ['me'],
    queryFn: userApi.getMe,
    enabled: auth.isAuthenticated,
  })

  const deactivateMutation = useMutation({
    mutationFn: userApi.deactivate,
    onSuccess: async () => {
      await auth.logout()
      navigate('/login', { replace: true })
    },
  })

  const logoutMutation = useMutation({
    mutationFn: auth.logout,
    onSuccess: () => {
      navigate('/login', { replace: true })
    },
  })

  if (meQuery.isLoading) {
    return <LoadingView message="내 정보를 불러오는 중..." />
  }

  if (meQuery.isError) {
    return <ErrorView message={getErrorMessage(meQuery.error)} onRetry={() => void meQuery.refetch()} />
  }

  const me = meQuery.data
  if (!me) {
    return <LoadingView message="내 정보를 불러오는 중..." />
  }

  return (
    <VStack gap="x5">
      <section className="rounded-r3 border border-stroke-neutral-muted bg-bg-layer-floating px-5 py-6">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t7Bold">계정 정보</Text>
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            계정 상태와 기본 프로필 정보를 확인하고 갱신할 수 있습니다.
          </Text>
          <ActionButton variant="neutralWeak" size="small" onClick={() => void meQuery.refetch()}>
            정보 새로고침
          </ActionButton>
        </VStack>
      </section>

      <section className="rounded-r3 border border-stroke-neutral-muted bg-bg-layer-floating p-5">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t5Bold">프로필</Text>
          <Text textStyle="t4Regular">ID: {me.id}</Text>
          <Text textStyle="t4Regular">이메일: {me.email}</Text>
          <Text textStyle="t4Regular">이름: {me.name}</Text>
          <Text textStyle="t4Regular">상태: {me.status}</Text>
          <Text textStyle="t4Regular" color="fg.neutralSubtle">
            가입일: {formatDateTime(me.createdAt)}
          </Text>
        </VStack>
      </section>

      <section className="rounded-r3 border border-stroke-warning-weak bg-bg-warning-weak p-5">
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t5Bold" color="fg.warning">
            계정 관리
          </Text>
          <Text textStyle="t4Regular" color="fg.warning">
            회원 탈퇴(비활성화)는 되돌릴 수 없습니다. 필요한 주문/환불 내역을 먼저 확인하세요.
          </Text>

          <HStack gap="x2" className="flex-wrap">
            <ActionButton
              variant="criticalSolid"
              loading={deactivateMutation.isPending}
              disabled={deactivateMutation.isPending || logoutMutation.isPending}
              onClick={() => {
                if (!confirm('회원 탈퇴(비활성화)를 진행할까요?')) {
                  return
                }
                deactivateMutation.mutate()
              }}
            >
              회원 탈퇴(비활성화)
            </ActionButton>
            <ActionButton
              variant="neutralWeak"
              loading={logoutMutation.isPending}
              disabled={deactivateMutation.isPending || logoutMutation.isPending}
              onClick={() => {
                logoutMutation.mutate()
              }}
            >
              로그아웃
            </ActionButton>
          </HStack>
        </VStack>
      </section>

      {deactivateMutation.isError ? (
        <Text textStyle="t4Regular" color="fg.critical">
          {getErrorMessage(deactivateMutation.error)}
        </Text>
      ) : null}

      {logoutMutation.isError ? (
        <Text textStyle="t4Regular" color="fg.critical">
          {getErrorMessage(logoutMutation.error)}
        </Text>
      ) : null}
    </VStack>
  )
}


