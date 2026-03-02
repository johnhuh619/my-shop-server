import { useMemo, useSyncExternalStore } from 'react'
import { cartStore } from '@/features/cart/model/cartStore'

export const useCart = () => {
  const state = useSyncExternalStore(cartStore.subscribe, cartStore.getState, cartStore.getState)

  return useMemo(
    () => ({
      items: state.items,
      totalQuantity: state.items.reduce((sum, item) => sum + item.quantity, 0),
      addItem: cartStore.addItem,
      updateItemQuantity: cartStore.updateItemQuantity,
      removeItem: cartStore.removeItem,
      removeItems: cartStore.removeItems,
      clear: cartStore.clear,
    }),
    [state.items],
  )
}
