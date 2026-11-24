import { Plus, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Badge } from '../components/ui/Badge'
import { Drawer } from '../components/ui/Drawer'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { ApiClientError } from '../lib/api'
import type { ApiClient } from '../lib/api'
import { maxUserId } from '../lib/jwt'
import type { OrderDetailResponse, OrderListQuery } from '../types/api'
import {
  allWarehouses, cell, codeText, currencyFormat, DataState, dateFormat, formText, MutationForm,
  numericCell, pageSize, Pagination, panel, rowAction, secondaryButton, Table, useResource,
} from './pageSupport'

const statuses = {
  PENDING: 'Chờ xác nhận', CONFIRMED: 'Đã xác nhận', PROCESSING: 'Đang đóng gói',
  SHIPPING: 'Đang giao', DELIVERED: 'Đã giao', CANCELLED: 'Đã hủy', RETURNED: 'Đã hoàn hàng',
}
const paymentMethods: Readonly<Record<string, string>> = {
  COD: 'Thu hộ (COD)', BANK_TRANSFER: 'Chuyển khoản ngân hàng', E_WALLET: 'Ví điện tử',
}
const labelStyle = 'min-w-0 text-sm font-medium text-slate-700'
const detailCard = 'grid gap-3 rounded-lg border border-slate-200 p-4'
const timeline = 'ml-2 grid gap-4 border-l-2 border-indigo-100 pl-5'
const timelinePoint = 'relative before:absolute before:top-1.5 before:-left-[27px] before:size-3 before:rounded-full before:border-2 before:border-white before:bg-indigo-500'

function useLoadErrorToast(error: Error | undefined, title: string) {
  const notify = useToast()
  useEffect(() => {
    if (error) notify({ title: error instanceof ApiClientError ? error.code : title,
      description: error.message, tone: 'error' })
  }, [error, notify, title])
}

async function loadCatalog(api: ApiClient, canManage: boolean) {
  const [first, warehouses] = await Promise.all([
    api.listProducts({ page: 1, limit: 100 }),
    canManage ? allWarehouses(api) : null,
  ])
  const products = [...first.data]
  for (let page = 2; page <= first.meta.total_pages; page += 1) {
    const next = await api.listProducts({ page, limit: 100 })
    products.push(...next.data)
  }
  return { products, warehouses: warehouses?.warehouses ?? [] }
}

function OrderFilters({ canManage, onApply }: { canManage: boolean; onApply: (query: OrderListQuery) => void }) {
  const [dateError, setDateError] = useState(false)

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const from_date = formText(form, 'from_date')
    const to_date = formText(form, 'to_date')
    const invalid = Boolean(from_date && to_date && from_date > to_date)
    setDateError(invalid)
    if (invalid) return
    onApply({
      status_code: formText(form, 'status_code'),
      order_code: formText(form, 'order_code'),
      custom_id: canManage ? Number(form.get('custom_id')) || undefined : undefined,
      from_date,
      to_date,
    })
  }

  return (
    <form className="grid gap-3 border-b border-slate-200 p-4" onSubmit={apply}
      onReset={() => { setDateError(false); onApply({}) }}>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <label className={labelStyle}>Mã đơn hàng<input className="field" name="order_code" placeholder="ORD-…" /></label>
        <label className={labelStyle}>Trạng thái đơn hàng
          <select className="field" name="status_code">
            <option value="">Tất cả trạng thái</option>
            {Object.entries(statuses).map(([code, name]) => <option key={code} value={code}>{name}</option>)}
          </select>
        </label>
        {canManage && <label className={labelStyle}>ID khách hàng
          <input className="field" name="custom_id" type="number" min={1} max={maxUserId} />
        </label>}
        <label className={labelStyle}>Từ ngày<input className="field" name="from_date" type="date" /></label>
        <label className={labelStyle}>Đến ngày<input className="field" name="to_date" type="date" /></label>
      </div>
      {dateError && <p role="alert" className="text-sm text-rose-700">Từ ngày phải trước hoặc bằng đến ngày.</p>}
      <div className="flex flex-wrap gap-2">
        <button className="primary-button" type="submit">Áp dụng</button>
        <button className={secondaryButton} type="reset">Xóa bộ lọc</button>
      </div>
    </form>
  )
}

interface OrderLine {
  key: number
  productId: string
  quantity: string
}

function CreateOrderDialog({ canManage, onClose, onSuccess }: {
  canManage: boolean; onClose: () => void; onSuccess: () => void
}) {
  const { api } = useAuth()
  const [revision, setRevision] = useState(0)
  const load = useCallback(() => loadCatalog(api, canManage), [api, canManage, revision])
  const catalog = useResource(load)
  useLoadErrorToast(catalog.error, 'Không thể tải danh mục')
  const [lines, setLines] = useState<OrderLine[]>([{ key: 0, productId: '', quantity: '1' }])
  const nextLine = useRef(1)
  const products = catalog.data?.products ?? []
  const selected = new Set(lines.map((line) => line.productId))
  const total = lines.reduce((amount, line) => {
    const product = products.find((entry) => entry.product_id === Number(line.productId))
    return amount + (product?.unit_price ?? 0) * Number(line.quantity)
  }, 0)

  function updateLine(key: number, update: Partial<Omit<OrderLine, 'key'>>) {
    setLines((current) => current.map((line) => line.key === key ? { ...line, ...update } : line))
  }

  function save(form: FormData) {
    const items = lines.map((line) => ({ product_id: Number(line.productId), quantity: Number(line.quantity) }))
    const warehouse_id = Number(form.get('warehouse_id'))
    if (!warehouse_id || items.some((item) => !item.product_id || !Number.isInteger(item.quantity) || item.quantity < 1)) {
      throw new Error('Chọn kho, sản phẩm và số lượng hợp lệ.')
    }
    const custom_id = canManage ? Number(form.get('custom_id')) || undefined : undefined
    return api.createOrder({ warehouse_id, payment_method: formText(form, 'payment_method'), items, custom_id })
  }

  return (
    <MutationForm title="Tạo đơn hàng" save={save} onClose={onClose} onSuccess={onSuccess}>
      {(catalog.loading || catalog.error) &&
        <DataState {...catalog} empty="" retry={() => setRevision((value) => value + 1)} />}
      {catalog.data && (products.length === 0 || (canManage && catalog.data.warehouses.length === 0)) &&
        <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">Cần có kho và sản phẩm để tạo đơn hàng.</p>}
      <div className="grid gap-3 sm:grid-cols-2">
        {canManage ? (
          <>
            <label className={labelStyle}>Kho xuất hàng
              <select name="warehouse_id" className="field" required>
                <option value="">Chọn kho</option>
                {catalog.data?.warehouses.map((warehouse) => <option key={warehouse.warehouse_id}
                  value={warehouse.warehouse_id}>{warehouse.warehouse_name} · #{warehouse.warehouse_id}</option>)}
              </select>
            </label>
            <div>
              <label className={labelStyle}>ID khách hàng
                <input className="field" name="custom_id" type="number" min={1} max={maxUserId}
                  aria-describedby="order-customer-hint" />
              </label>
              <p id="order-customer-hint" className="mt-1 text-xs text-slate-600">API hiện tại yêu cầu ID khách hàng khi tạo đơn tại quầy.</p>
            </div>
          </>
        ) : <label className={labelStyle}>ID kho
          <input name="warehouse_id" className="field" type="number" min={1} max={maxUserId} required />
        </label>}
      </div>
      {lines.map((line, index) => (
        <div key={line.key} className="grid grid-cols-[minmax(0,1fr)_5.5rem_auto] items-end gap-2 rounded-lg border border-slate-200 p-3">
          <label className={labelStyle}>Sản phẩm {index + 1}
            <select className="field" required value={line.productId}
              onChange={(event) => updateLine(line.key, { productId: event.target.value })}>
              <option value="">Chọn sản phẩm</option>
              {products.map((product) => <option key={product.product_id} value={product.product_id}
                disabled={String(product.product_id) !== line.productId && selected.has(String(product.product_id))}>
                {product.product_code} · {product.product_name} · {currencyFormat.format(product.unit_price)}
              </option>)}
            </select>
          </label>
          <label className={labelStyle}>Số lượng {index + 1}
            <input className="field" type="number" min={1} max={maxUserId} required value={line.quantity}
              onChange={(event) => updateLine(line.key, { quantity: event.target.value })} />
          </label>
          {lines.length > 1 && <button type="button" aria-label={'Xóa dòng ' + (index + 1)}
            className="icon-button mb-0.5 text-rose-700"
            onClick={() => setLines((current) => current.filter((item) => item.key !== line.key))}>
            <Trash2 size={16} aria-hidden="true" />
          </button>}
        </div>
      ))}
      <button type="button" className={secondaryButton + ' justify-self-start'} disabled={lines.length >= products.length}
        onClick={() => {
          const key = nextLine.current++
          setLines((current) => [...current, { key, productId: '', quantity: '1' }])
        }}>
        <Plus size={15} aria-hidden="true" /> Thêm dòng sản phẩm
      </button>
      <label className={labelStyle}>Phương thức thanh toán
        <select name="payment_method" className="field" defaultValue="COD">
          {Object.entries(paymentMethods).map(([code, name]) => <option value={code} key={code}>{name}</option>)}
        </select>
      </label>
      <div className="flex flex-wrap items-baseline justify-between gap-2 rounded-lg bg-indigo-50 p-3 text-sm font-semibold text-indigo-900">
        <span>Tổng tiền dự kiến</span>
        <output className="text-lg tabular-nums">{currencyFormat.format(total)}</output>
      </div>
    </MutationForm>
  )
}

interface OrderAction {
  title: string
  save: (form: FormData) => Promise<{ message: string }>
  children: ReactNode
  danger?: boolean
}

function OrderDetail({ order, canManage, onAction }: {
  order: OrderDetailResponse; canManage: boolean; onAction: (action: OrderAction) => void
}) {
  const { api } = useAuth()
  const { order_id, status, payment } = order
  const nextStatus = status.status_code === 'PENDING' ? 'CONFIRMED' : status.status_code === 'CONFIRMED' ? 'PROCESSING' : null
  const transitionLabel = nextStatus === 'CONFIRMED' ? 'Xác nhận đơn' : 'Đóng gói'
  const cancel = () => onAction({
    title: 'Hủy đơn hàng',
    danger: true,
    save: () => api.cancelOrder(order_id),
    children: <div className="grid gap-2 text-sm text-slate-700">
      <p>Hủy đơn {order.order_code} và giải phóng số lượng hàng đang được giữ chỗ?</p>
      {payment.payment_status === 'PAID' && <p className="rounded-lg bg-amber-50 p-3 font-medium text-amber-900">
        Đơn đã thanh toán. Hệ thống sẽ tự động tạo yêu cầu hoàn tiền {currencyFormat.format(payment.amount)}.
      </p>}
    </div>,
  })
  const confirmPayment = () => onAction({
    title: 'Xác nhận thanh toán',
    save: (form) => {
      const transaction_code = formText(form, 'transaction_code')
      if (!transaction_code) throw new Error('Nhập mã giao dịch để xác nhận thanh toán.')
      return api.confirmPayment(order_id, { transaction_code })
    },
    children: <label className={labelStyle}>Mã giao dịch
      <input name="transaction_code" className="field" required maxLength={255} />
    </label>,
  })
  const refundAction = (id: number, complete: boolean) => onAction({
    title: complete ? 'Duyệt và hoàn tiền' : 'Từ chối hoàn tiền',
    save: () => api.updateRefundStatus(id, { refund_status: complete ? 'COMPLETED' : 'REJECTED' }),
    danger: !complete,
    children: <p className="text-sm text-slate-700">
      {complete ? 'Xác nhận đã hoàn tiền cho khách hàng?' : 'Xác nhận từ chối yêu cầu hoàn tiền này?'}
    </p>,
  })

  return (
    <div className="grid gap-5">
      <section className={detailCard} aria-label="Thông tin đơn hàng">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <Badge status={status.status_code} label={status.status_name} />
          <strong className="tabular-nums text-slate-900">{currencyFormat.format(order.total_amount)}</strong>
        </div>
        <dl className="grid gap-2 text-sm text-slate-700 sm:grid-cols-3">
          <div><dt className="text-slate-500">Khách hàng</dt><dd>#{order.custom_id}</dd></div>
          <div><dt className="text-slate-500">Kho xuất hàng</dt><dd>#{order.warehouse_id}</dd></div>
          <div><dt className="text-slate-500">Ngày tạo</dt><dd>{dateFormat.format(new Date(order.created_at))}</dd></div>
        </dl>
        {nextStatus && <div className="flex flex-wrap gap-2">
          {canManage && <button type="button" className="primary-button" onClick={() => onAction({
            title: transitionLabel,
            save: () => api.updateOrderStatus(order_id, { new_status_code: nextStatus }),
            children: <p className="text-sm text-slate-700">{transitionLabel} cho đơn {order.order_code}?</p>,
          })}>{transitionLabel}</button>}
          <button type="button" className={secondaryButton + ' text-rose-700'} onClick={cancel}>Hủy đơn hàng</button>
        </div>}
      </section>
      <section className={panel} aria-label="Sản phẩm trong đơn hàng">
        <h3 className="px-4 py-3 text-sm font-semibold">Sản phẩm</h3>
        <Table label="Sản phẩm trong đơn" headers={['Sản phẩm', { label: 'SL', numeric: true },
          { label: 'Đơn giá', numeric: true }, { label: 'Thành tiền', numeric: true }]}>
          {order.items.map((item) => <tr key={item.order_item_id}>
            <td className={cell}>{item.item_name} <span className="text-xs text-slate-500">#{item.product_id}</span></td>
            <td className={numericCell}>{item.quantity}</td>
            <td className={numericCell}>{currencyFormat.format(item.unit_price)}</td>
            <td className={numericCell}>{currencyFormat.format(item.quantity * item.unit_price)}</td>
          </tr>)}
        </Table>
      </section>
      <section className={detailCard} aria-label="Thanh toán">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-sm font-semibold">Thanh toán</h3><Badge status={payment.payment_status} />
        </div>
        <p className="text-sm text-slate-700">
          {paymentMethods[payment.payment_method] ?? payment.payment_method} · {currencyFormat.format(payment.amount)}
        </p>
        {payment.transaction_code && <p className="text-sm text-slate-700">Mã giao dịch <span className={codeText}>{payment.transaction_code}</span></p>}
        {payment.paid_at && <p className="text-sm text-slate-600">Thanh toán lúc {dateFormat.format(new Date(payment.paid_at))}</p>}
        {canManage && payment.payment_status === 'PENDING' && !['CANCELLED', 'RETURNED'].includes(status.status_code) &&
          <button className={secondaryButton + ' justify-self-start'} type="button" onClick={confirmPayment}>Xác nhận thanh toán</button>}
      </section>
      <section className={detailCard} aria-label="Hoàn tiền">
        <h3 className="text-sm font-semibold">Hoàn tiền</h3>
        {order.refunds.length === 0 && <p className="text-sm text-slate-600">Chưa có yêu cầu hoàn tiền.</p>}
        {order.refunds.map((refund) => (
          <div key={refund.refund_id} className="grid gap-2 border-t border-slate-100 pt-3">
            <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-slate-700">
              <span>#{refund.refund_id} · {currencyFormat.format(refund.refund_amount)}</span>
              <Badge status={refund.refund_status} label={refund.refund_status === 'PENDING' ? 'Chờ hoàn tiền' : undefined} />
            </div>
            <p className="text-xs text-slate-600">Ngày tạo {dateFormat.format(new Date(refund.created_at))}</p>
            {canManage && refund.refund_status === 'PENDING' && <div className="flex flex-wrap gap-2">
              <button type="button" className={secondaryButton} onClick={() => refundAction(refund.refund_id, true)}>Duyệt và hoàn tiền</button>
              <button type="button" className={secondaryButton + ' text-rose-700'} onClick={() => refundAction(refund.refund_id, false)}>Từ chối hoàn tiền</button>
            </div>}
          </div>
        ))}
      </section>
      {order.shipments.length > 0 && <section className={detailCard}>
        <h3 className="text-sm font-semibold">Vận đơn</h3>
        {order.shipments.map((shipment) => <div key={shipment.shipment_id} className="flex flex-wrap items-center gap-2">
          <span className={codeText}>{shipment.tracking_code}</span>
          <span className="text-xs text-slate-600">ID vận đơn #{shipment.shipment_id}</span>
          <Badge status={shipment.status.status_code} label={shipment.status.status_name} />
        </div>)}
      </section>}
      <section className={detailCard}>
        <h3 className="text-sm font-semibold">Lịch sử trạng thái</h3>
        {order.status_history.length === 0 ? <p className="text-sm text-slate-600">Chưa có lịch sử trạng thái.</p> :
          <ol aria-label="Lịch sử trạng thái" className={timeline}>
            {order.status_history.map((event) => <li key={event.history_id} className={timelinePoint}>
              <p className="text-sm font-semibold text-slate-800">
                {event.old_status && event.old_status.status_name + ' → '}{event.new_status.status_name}
              </p>
              <p className="text-xs text-slate-600">{dateFormat.format(new Date(event.change_at))} · Người cập nhật #{event.changed_by}</p>
            </li>)}
          </ol>}
      </section>
    </div>
  )
}

export function OrdersPage() {
  const { api, claims } = useAuth()
  const canManage = claims?.role === 'STAFF' || claims?.role === 'ADMIN'
  const [filters, setFilters] = useState<OrderListQuery>({ page: 1 })
  const [listRevision, setListRevision] = useState(0)
  const [detailRevision, setDetailRevision] = useState(0)
  const [create, setCreate] = useState(false)
  const [selected, setSelected] = useState<{ id: number; code: string }>()
  const [action, setAction] = useState<OrderAction>()

  const loadOrders = useCallback(() => api.listOrders({ ...filters, limit: pageSize }), [api, filters, listRevision])
  const loadDetail = useCallback(async () => {
    if (!selected) throw new Error('Chọn đơn hàng để xem chi tiết.')
    const id = selected.id
    const [order, history] = await Promise.all([api.getOrderDetail(id), api.getOrderStatusHistory(id)])
    return { ...order.data, status_history: history.data }
  }, [api, selected, detailRevision])
  const orders = useResource(loadOrders)
  const detail = useResource(selected ? loadDetail : null)
  useLoadErrorToast(orders.error, 'Không thể tải danh sách đơn')
  useLoadErrorToast(detail.error, 'Không thể tải chi tiết đơn')
  const totalPages = orders.data?.meta.total_pages
  useEffect(() => {
    if (totalPages === undefined) return
    const lastPage = Math.max(1, totalPages)
    if ((filters.page ?? 1) > lastPage) setFilters((query) => ({ ...query, page: lastPage }))
  }, [filters.page, totalPages])
  const refreshOrders = () => setListRevision((value) => value + 1)
  const refreshDetail = () => setDetailRevision((value) => value + 1)

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold text-slate-900">Đơn hàng</h1>
        <button className="primary-button inline-flex items-center gap-2" type="button" onClick={() => setCreate(true)}>
          <Plus size={17} aria-hidden="true" /> Tạo đơn hàng
        </button>
      </div>
      <section className={panel} aria-label="Danh sách đơn hàng">
        <OrderFilters canManage={canManage} onApply={(query) => setFilters({ ...query, page: 1 })} />
        <DataState {...orders} empty="Không tìm thấy đơn hàng." retry={refreshOrders}>
          {orders.data && orders.data.data.length > 0 && (
            <Table label="Đơn hàng" headers={['Mã đơn', 'Trạng thái', 'Kho', 'Ngày tạo', { label: 'Tổng tiền', numeric: true }]}>
              {orders.data.data.map((order) => <tr key={order.order_id}>
                <td className={cell}><button type="button" className={rowAction + ' font-mono'}
                  onClick={() => setSelected({ id: order.order_id, code: order.order_code })}>{order.order_code}</button></td>
                <td className={cell}><Badge status={order.status.status_code} label={order.status.status_name} /></td>
                <td className={cell}>#{order.warehouse_id}</td>
                <td className={cell + ' whitespace-nowrap'}>{dateFormat.format(new Date(order.created_at))}</td>
                <td className={numericCell + ' whitespace-nowrap'}>{currencyFormat.format(order.total_amount)}</td>
              </tr>)}
            </Table>
          )}
        </DataState>
        <Pagination meta={orders.data?.meta} onPage={(page) => setFilters((query) => ({ ...query, page }))} />
      </section>
      {create && <CreateOrderDialog canManage={canManage} onClose={() => setCreate(false)}
        onSuccess={() => { setCreate(false); refreshOrders() }} />}
      {selected && <Drawer title={'Đơn hàng ' + selected.code} open onClose={() => setSelected(undefined)}>
        <DataState {...detail} empty="Không tìm thấy chi tiết đơn hàng." retry={refreshDetail}>
          {detail.data && <OrderDetail order={detail.data} canManage={canManage} onAction={setAction} />}
        </DataState>
      </Drawer>}
      {action && <MutationForm {...action} onClose={() => setAction(undefined)}
        onSuccess={() => { setAction(undefined); refreshOrders(); refreshDetail() }} />}
    </div>
  )
}
