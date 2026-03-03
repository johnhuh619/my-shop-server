import type { ProductStatus } from '@/shared/types/domain'

const CART_STORAGE_KEY = 'minishop.cart.v1'

export interface CartItem {
  productId: number
  name: string
  description: string
  unitPrice: number
  status: ProductStatus
  quantityAvailable: number | null
  quantity: number
}

interface CartState {
  items: CartItem[]
}

type CartListener = (state: CartState) => void

const clampQuantity = (quantity: number, quantityAvailable: number | null) => {
  const normalized = Number.isFinite(quantity) ? Math.trunc(quantity) : 1
  const lowerBounded = Math.max(1, normalized)

  if (quantityAvailable === null) {
    return lowerBounded
  }

  return Math.min(lowerBounded, Math.max(1, quantityAvailable))
}

const parseCartState = (raw: string | null): CartState => {
  if (!raw) {
    return { items: [] }
  }

  try {
    const parsed = JSON.parse(raw) as CartState
    if (!parsed || !Array.isArray(parsed.items)) {
      return { items: [] }
    }

    const items = parsed.items
      .filter((item) => typeof item?.productId === 'number')
      .map((item) => ({
        ...item,
        quantity: clampQuantity(item.quantity, item.quantityAvailable),
      }))

    return { items }
  } catch {
    return { items: [] }
  }
}

const loadInitialState = (): CartState => {
  if (typeof window === 'undefined') {
    return { items: [] }
  }

  return parseCartState(localStorage.getItem(CART_STORAGE_KEY))
}

let cartState: CartState = loadInitialState()
const listeners = new Set<CartListener>()

const persist = () => {
  if (typeof window === 'undefined') {
    return
  }

  localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(cartState))
}

const notify = () => {
  listeners.forEach((listener) => listener(cartState))
}

const setState = (nextState: CartState) => {
  cartState = nextState
  persist()
  notify()
}

const addItem = (payload: Omit<CartItem, 'quantity'> & { quantity: number }) => {
  const normalizedQuantity = clampQuantity(payload.quantity, payload.quantityAvailable)
  const existingIndex = cartState.items.findIndex((item) => item.productId === payload.productId)

  if (existingIndex === -1) {
    setState({
      items: [...cartState.items, { ...payload, quantity: normalizedQuantity }],
    })
    return
  }

  const existing = cartState.items[existingIndex]
  const mergedQuantity = clampQuantity(existing.quantity + normalizedQuantity, payload.quantityAvailable)
  const nextItems = [...cartState.items]
  nextItems[existingIndex] = {
    ...existing,
    ...payload,
    quantity: mergedQuantity,
  }
  setState({ items: nextItems })
}

const updateItemQuantity = (productId: number, quantity: number) => {
  const nextItems = cartState.items.map((item) => {
    if (item.productId !== productId) {
      return item
    }

    return {
      ...item,
      quantity: clampQuantity(quantity, item.quantityAvailable),
    }
  })
  setState({ items: nextItems })
}

const removeItem = (productId: number) => {
  setState({
    items: cartState.items.filter((item) => item.productId !== productId),
  })
}

const removeItems = (productIds: number[]) => {
  const idSet = new Set(productIds)
  setState({
    items: cartState.items.filter((item) => !idSet.has(item.productId)),
  })
}

const clear = () => {
  setState({ items: [] })
}

const getState = () => cartState

const subscribe = (listener: CartListener) => {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export const cartStore = {
  addItem,
  clear,
  getState,
  removeItem,
  removeItems,
  subscribe,
  updateItemQuantity,
}
