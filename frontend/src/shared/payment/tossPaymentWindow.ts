const TOSS_SDK_URL = 'https://js.tosspayments.com/v2/standard'

type TossPaymentRequest = {
  method: 'CARD'
  amount: {
    currency: 'KRW'
    value: number
  }
  orderId: string
  orderName: string
  successUrl: string
  failUrl: string
  customerEmail?: string
  customerName?: string
}

type TossPaymentClient = {
  requestPayment: (request: TossPaymentRequest) => Promise<void> | void
}

type TossPaymentsSdk = {
  payment: (options: { customerKey: string }) => TossPaymentClient
}

declare global {
  interface Window {
    TossPayments?: (clientKey: string) => TossPaymentsSdk
  }
}

let tossScriptLoadingPromise: Promise<void> | null = null

const loadTossScript = () => {
  if (typeof window === 'undefined') {
    throw new Error('Payment can only be started in the browser.')
  }

  if (window.TossPayments) {
    return Promise.resolve()
  }

  if (tossScriptLoadingPromise) {
    return tossScriptLoadingPromise
  }

  tossScriptLoadingPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = TOSS_SDK_URL
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Failed to load Toss SDK. Check your network connection.'))
    document.head.appendChild(script)
  })

  return tossScriptLoadingPromise
}

export const openTossPaymentWindow = async ({
  clientKey,
  customerKey,
  request,
}: {
  clientKey: string
  customerKey: string
  request: TossPaymentRequest
}) => {
  if (!clientKey) {
    throw new Error('VITE_TOSS_CLIENT_KEY is not configured.')
  }

  await loadTossScript()

  const tossPaymentsInitializer = window.TossPayments
  if (!tossPaymentsInitializer) {
    throw new Error('Failed to initialize Toss SDK.')
  }

  const tossPayments = tossPaymentsInitializer(clientKey)
  const payment = tossPayments.payment({ customerKey })
  await payment.requestPayment(request)
}
