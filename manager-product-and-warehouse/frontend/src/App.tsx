import { CircleUserRound } from 'lucide-react'
import { useSyncExternalStore } from 'react'
import { Layout } from './components/Layout'
import { EmptyState } from './components/ui/EmptyState'
import { AuthProvider, useAuth } from './context/AuthContext'
import { ToastProvider } from './context/ToastContext'
import { DashboardPage } from './pages/DashboardPage'
import { InventoryPage } from './pages/InventoryPage'
import { OrdersPage } from './pages/OrdersPage'
import { ProductsPage } from './pages/ProductsPage'
import { ShipmentsPage } from './pages/ShipmentsPage'
import { WarehousesPage } from './pages/WarehousesPage'

function subscribeToHash(onChange: () => void) {
  window.addEventListener('hashchange', onChange)
  return () => window.removeEventListener('hashchange', onChange)
}

function AppPage() {
  const route = useSyncExternalStore(subscribeToHash, () => window.location.hash)
  const { token, claims } = useAuth()

  if (!claims) return <EmptyState icon={CircleUserRound} title="Cần phiên truy cập hợp lệ"
    description="Chọn vai trò thử nghiệm hoặc dán Bearer token trong menu Phiên truy cập." />

  switch (route) {
    case '#don-hang': return <OrdersPage key={token} />
    case '#van-don': return <ShipmentsPage key={token} />
    case '#kho': return <WarehousesPage key={token} />
    case '#san-pham': return <ProductsPage key={token} />
    case '#ton-kho': return <InventoryPage key={token} />
    default: return <DashboardPage key={token} />
  }
}

export function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <Layout>
          <AppPage />
        </Layout>
      </AuthProvider>
    </ToastProvider>
  )
}
