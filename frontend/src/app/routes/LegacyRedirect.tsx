import { Navigate, useLocation, useParams } from 'react-router-dom'

type LegacyRedirectProps =
  | {
      to: string
    }
  | {
      buildTo: (args: { params: Readonly<Record<string, string | undefined>> }) => string
    }

export const LegacyRedirect = (props: LegacyRedirectProps) => {
  const location = useLocation()
  const params = useParams()

  const baseTo = 'to' in props ? props.to : props.buildTo({ params })
  const nextTo =
    baseTo.includes('?') || baseTo.includes('#') ? baseTo : `${baseTo}${location.search}${location.hash}`

  return <Navigate to={nextTo} replace />
}
