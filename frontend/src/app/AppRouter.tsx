import { Navigate, Route, Routes, useSearchParams } from 'react-router-dom'
import { AppLayout } from '@/app/layouts/AppLayout'
import { LegacyRedirect } from '@/app/routes/LegacyRedirect'
import { RequireAdmin } from '@/app/routes/RequireAdmin'
import { RequireAuth } from '@/app/routes/RequireAuth'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { RegisterPage } from '@/features/auth/pages/RegisterPage'
import { AdminPlaceholderPage } from '@/features/admin/pages/AdminPlaceholderPage'
import { CartPage } from '@/features/cart/pages/CartPage'
import { DeliveryListPage } from '@/features/delivery/pages/DeliveryListPage'
import { OrderDetailPage } from '@/features/order/pages/OrderDetailPage'
import { OrderListPage } from '@/features/order/pages/OrderListPage'
import { CheckoutPage } from '@/features/order/pages/CheckoutPage'
import { ProductDetailPage } from '@/features/product/pages/ProductDetailPage'
import { ProductListPage } from '@/features/product/pages/ProductListPage'
import { RefundListPage } from '@/features/refund/pages/RefundListPage'
import { MyPage } from '@/features/user/pages/MyPage'
import { MyPageLayout } from '@/features/user/pages/MyPageLayout'

const OrderScopedRefundPage = () => {
  const [searchParams] = useSearchParams()
  const orderIdParam = searchParams.get('orderId')
  const parsedOrderId = orderIdParam ? Number(orderIdParam) : NaN

  if (!orderIdParam || !Number.isFinite(parsedOrderId)) {
    return <Navigate to="/me/orders" replace />
  }

  return <RefundListPage />
}

export const AppRouter = () => {
  return (
    <AppLayout>
      <Routes>
        <Route path="/" element={<Navigate to="/products" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        <Route path="/products" element={<ProductListPage />} />
        <Route path="/products/:productId" element={<ProductDetailPage />} />
        <Route path="/cart" element={<CartPage />} />

        <Route
          path="/orders"
          element={
            <RequireAuth>
              <LegacyRedirect to="/me/orders" />
            </RequireAuth>
          }
        />
        <Route
          path="/orders/:orderId"
          element={
            <RequireAuth>
              <LegacyRedirect buildTo={({ params }) => `/me/orders/${params.orderId ?? ''}`} />
            </RequireAuth>
          }
        />
        <Route
          path="/checkout/:orderId"
          element={
            <RequireAuth>
              <CheckoutPage />
            </RequireAuth>
          }
        />
        <Route
          path="/me"
          element={
            <RequireAuth>
              <MyPageLayout />
            </RequireAuth>
          }
        >
          <Route index element={<Navigate to="account" replace />} />
          <Route path="account" element={<MyPage />} />
          <Route path="orders" element={<OrderListPage />} />
          <Route path="orders/:orderId" element={<OrderDetailPage />} />
          <Route path="payments" element={<Navigate to="/me/orders" replace />} />
          <Route path="refunds" element={<OrderScopedRefundPage />} />
          <Route path="deliveries" element={<DeliveryListPage />} />
        </Route>
        <Route
          path="/payments"
          element={
            <RequireAuth>
              <LegacyRedirect to="/me/orders" />
            </RequireAuth>
          }
        />
        <Route
          path="/refunds"
          element={
            <RequireAuth>
              <LegacyRedirect to="/me/refunds" />
            </RequireAuth>
          }
        />
        <Route
          path="/deliveries"
          element={
            <RequireAuth>
              <LegacyRedirect to="/me/deliveries" />
            </RequireAuth>
          }
        />

        <Route
          path="/admin"
          element={
            <RequireAdmin>
              <AdminPlaceholderPage />
            </RequireAdmin>
          }
        />

        <Route path="*" element={<Navigate to="/products" replace />} />
      </Routes>
    </AppLayout>
  )
}
