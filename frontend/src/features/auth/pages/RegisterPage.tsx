import { ActionButton, Text, VStack } from '@seed-design/react'
import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/shared/auth/useAuth'
import { getErrorMessage } from '@/shared/utils/errors'

export const RegisterPage = () => {
  const auth = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)

    try {
      await auth.register({ email, password, name })
      await auth.login({ email, password })
      navigate('/products', { replace: true })
    } catch (submitError) {
      setError(getErrorMessage(submitError))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="mx-auto w-full max-w-[420px]">
      <VStack
        as="section"
        gap="x4"
        width="full"
        p="x4"
        borderWidth={1}
        borderColor="stroke.neutralSubtle"
        borderRadius="r3"
      >
        <Text textStyle="t6Bold">회원가입</Text>
        <form className="flex flex-col gap-3" onSubmit={handleSubmit}>
        <label className="flex flex-col gap-1">
          <span className="text-sm text-fg-neutral-subtle">이름</span>
          <input
            className="rounded-r2 border border-stroke-neutral-subtle px-x3 py-x2"
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            required
          />
        </label>

        <label className="flex flex-col gap-1">
          <span className="text-sm text-fg-neutral-subtle">이메일</span>
          <input
            className="rounded-r2 border border-stroke-neutral-subtle px-x3 py-x2"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </label>

        <label className="flex flex-col gap-1">
          <span className="text-sm text-fg-neutral-subtle">비밀번호</span>
          <input
            className="rounded-r2 border border-stroke-neutral-subtle px-x3 py-x2"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </label>

        {error ? <p className="text-sm text-fg-critical">{error}</p> : null}

        <ActionButton type="submit" loading={isSubmitting} disabled={isSubmitting}>
          가입하기
        </ActionButton>
        <ActionButton type="button" variant="neutralWeak" onClick={() => navigate('/login')}>
          로그인 화면으로
        </ActionButton>
        </form>
      </VStack>
    </div>
  )
}

