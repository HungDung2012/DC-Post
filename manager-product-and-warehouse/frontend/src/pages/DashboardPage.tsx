import {
  ArrowRight, Boxes, CircleAlert, ClipboardList, Factory, Package, RefreshCw, Truck,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useCallback, useState } from 'react'
import { Badge } from '../components/ui/Badge'
import { StatCard } from '../components/ui/StatCard'
import { useAuth } from '../context/AuthContext'
import { ApiClientError } from '../lib/api'
import type { ApiClient } from '../lib/api'
import type { Warehouse } from '../types/api'
import {
  allWarehouses, cell, codeText, currencyFormat, DataState, dateFormat, numberFormat, numericCell,
  panel, secondaryButton, Table, useResource,
} from './pageSupport'

const activeOrders = ['PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPING']

async function countLowStock(api: ApiClient, warehouses: Warehouse[]) {
  let total = 0
  let accessible = 0
  let failed = 0
  for (let offset = 0; offset < warehouses.length; offset += 6) {
    const results = await Promise.allSettled(warehouses.slice(offset, offset + 6).map(
      ({ warehouse_id }) => api.listWarehouseInventory(warehouse_id, { page: 1, limit: 1, low_stock: true }),
    ))
    for (const result of results) {
      if (result.status === 'fulfilled') {
        total += result.value.meta.total_items
        accessible += 1
      } else if (!(result.reason instanceof ApiClientError && result.reason.code === 'ERR_WAREHOUSE_ACCESS_DENIED')) {
        failed += 1
      }
    }
  }
  return { total, accessible, failed }
}

function count(value?: number) {
  return value === undefined ? '—' : numberFormat.format(value)
}

function QuickLink({ href, icon: Icon, children }: { href: string; icon: LucideIcon; children: string }) {
  return (
    <a href={href} className="flex items-center gap-3 rounded-lg border border-slate-200 p-2.5 text-sm font-semibold text-slate-800 hover:border-indigo-300 hover:bg-indigo-50/50">
      <span className="grid size-8 shrink-0 place-items-center rounded-md bg-indigo-50 text-indigo-700">
        <Icon size={16} aria-hidden="true" />
      </span>
      <span className="flex-1">{children}</span>
      <ArrowRight size={16} className="shrink-0 text-slate-400" aria-hidden="true" />
    </a>
  )
}

export function DashboardPage() {
  const { claims, api } = useAuth()
  const operator = claims?.role === 'ADMIN' || claims?.role === 'STAFF'
  const customer = claims?.role === 'CUSTOMER'
  const [revision, setRevision] = useState(0)
  const loadOrders = useCallback(() => api.listOrders({ page: 1, limit: 5 }), [api, revision])
  const loadActive = useCallback(async () => {
    const results = await Promise.all(activeOrders.map((status_code) => api.listOrders({ page: 1, limit: 1, status_code })))
    const totals = results.map(({ meta }) => meta.total_items)
    return { waiting: totals[0] + totals[1] + totals[2], shipping: totals[3] }
  }, [api, revision])
  const loadProducts = useCallback(() => api.listProducts({ page: 1, limit: 5 }), [api, revision])
  const loadWarehouses = useCallback(() => allWarehouses(api), [api, revision])
  const orders = useResource(loadOrders)
  const active = useResource(loadActive)
  const products = useResource(loadProducts)
  const warehouses = useResource(operator ? loadWarehouses : null)
  const warehouseList = warehouses.data?.warehouses
  const loadLowStock = useCallback(() => countLowStock(api, warehouseList ?? []), [api, warehouseList])
  const lowStock = useResource(warehouseList ? loadLowStock : null)
  const refresh = () => setRevision((value) => value + 1)
  const low = lowStock.data
  const loading = orders.loading || active.loading || products.loading || warehouses.loading || lowStock.loading

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-indigo-100 bg-indigo-50/60 px-4 py-3">
        <p className="text-sm font-medium text-indigo-950">{customer
          ? 'Theo dõi đơn hàng của bạn và xem danh mục sản phẩm.'
          : 'Theo dõi tình trạng đơn hàng, mạng lưới kho và hàng sắp hết.'}</p>
        <button type="button" className={secondaryButton} onClick={refresh} disabled={loading}>
          <RefreshCw size={15} aria-hidden="true" className={loading ? 'motion-safe:animate-spin' : undefined} /> Làm mới
        </button>
      </div>
      <div className={'grid gap-3 md:grid-cols-2 ' + (operator ? 'xl:grid-cols-4' : 'xl:grid-cols-3')}>
        <StatCard label={customer ? 'Tổng đơn hàng của bạn' : 'Tổng đơn hàng'} icon={ClipboardList}
          value={count(orders.data?.meta.total_items)} />
        <StatCard label="Đơn chờ xử lý / đang giao" icon={Truck}
          value={count(active.data && active.data.waiting + active.data.shipping)}
          detail={active.data && <div className="flex flex-wrap gap-1.5">
            <Badge status="PENDING" label={count(active.data.waiting) + ' chờ xử lý'} />
            <Badge status="SHIPPING" label={count(active.data.shipping) + ' đang giao'} />
          </div>} />
        {operator && <StatCard label="Tổng kho / bưu cục" icon={Factory} value={count(warehouses.data?.total)} />}
        <StatCard label="Tổng sản phẩm" icon={Package} value={count(products.data?.meta.total_items)}
          detail={operator && low && (
            <div className="grid gap-1">
              {(low.accessible > 0 || warehouseList?.length === 0) && (
                <span className={'flex items-center gap-1.5 font-medium ' + (low.total > 0 ? 'text-amber-800' : 'text-slate-600')}>
                  {low.total > 0 && <CircleAlert size={15} aria-hidden="true" />}
                  {count(low.total)} mặt hàng tồn thấp
                </span>
              )}
              {low.failed > 0 && <span className="text-rose-800">Không tải được cảnh báo của {count(low.failed)} kho.</span>}
              {low.accessible === 0 && low.failed === 0 && !!warehouseList?.length &&
                <span>Chưa có kho được phân công.</span>}
            </div>
          )} />
      </div>
      {(active.error || warehouses.error || (!customer && products.error)) && (
        <section className={panel}>
          <DataState loading={false} error={active.error ?? warehouses.error ?? products.error} empty="" retry={refresh} />
        </section>
      )}
      <div className="grid min-w-0 items-start gap-4 xl:grid-cols-[minmax(0,1fr)_17rem]">
        <section className={panel}>
          <h2 className="px-4 py-3 font-semibold text-slate-900">Đơn hàng gần đây</h2>
          <DataState {...orders} empty="Chưa có đơn hàng." retry={refresh}>
            {!!orders.data?.data.length && (
              <Table label="Đơn hàng gần đây" headers={['Mã đơn', 'Trạng thái', 'Thời gian tạo', { label: 'Tổng tiền', numeric: true }]}>
                {orders.data.data.map((order) => (
                  <tr key={order.order_id}>
                    <td className={cell}><span className={codeText}>{order.order_code}</span></td>
                    <td className={cell + ' whitespace-nowrap'}><Badge status={order.status.status_code} label={order.status.status_name} /></td>
                    <td className={cell + ' whitespace-nowrap'}>{dateFormat.format(new Date(order.created_at))}</td>
                    <td className={numericCell + ' whitespace-nowrap font-medium text-slate-900'}>{currencyFormat.format(order.total_amount)}</td>
                  </tr>
                ))}
              </Table>
            )}
          </DataState>
        </section>
        <section className={panel + ' grid gap-2 p-3'}>
          <h2 className="px-1 pb-1 text-sm font-semibold text-slate-900">Thao tác nhanh</h2>
          <QuickLink href="#don-hang" icon={ClipboardList}>Xem đơn hàng</QuickLink>
          {operator ? <>
            <QuickLink href="#ton-kho" icon={Boxes}>Quản lý tồn kho</QuickLink>
            <QuickLink href="#kho" icon={Factory}>Xem kho / bưu cục</QuickLink>
          </> : <QuickLink href="#san-pham" icon={Package}>Xem danh mục sản phẩm</QuickLink>}
        </section>
      </div>
      {customer && (
        <section className={panel}>
          <h2 className="px-4 py-3 font-semibold text-slate-900">Danh mục sản phẩm</h2>
          <DataState {...products} empty="Chưa có sản phẩm trong danh mục." retry={refresh}>
            {!!products.data?.data.length && (
              <Table label="Danh mục sản phẩm" headers={['Mã sản phẩm', 'Tên sản phẩm', { label: 'Đơn giá', numeric: true }]}>
                {products.data.data.map((product) => (
                  <tr key={product.product_id}>
                    <td className={cell}><span className={codeText}>{product.product_code}</span></td>
                    <td className={cell + ' font-medium text-slate-900'}>{product.product_name}</td>
                    <td className={numericCell + ' whitespace-nowrap'}>{currencyFormat.format(product.unit_price)}</td>
                  </tr>
                ))}
              </Table>
            )}
          </DataState>
        </section>
      )}
    </div>
  )
}

