import { ScanLine, Search, Truck } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Badge } from '../components/ui/Badge'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { ApiClientError } from '../lib/api'
import type { ApiClient } from '../lib/api'
import { maxUserId } from '../lib/jwt'
import type { OrderSummary, Shipment, ShipmentTrackingResponse, Warehouse } from '../types/api'
import { allWarehouses, codeText, DataState, dateFormat, MutationForm, panel, secondaryButton, useResource } from './pageSupport'

const scanStatuses = {
  IN_TRANSIT: 'Đang luân chuyển',
  OUT_FOR_DELIVERY: 'Đang giao hàng',
  DELIVERED: 'Đã giao',
  RETURNED: 'Đã hoàn hàng',
}
const labelStyle = 'min-w-0 text-sm font-medium text-slate-700'

function useLoadErrorToast(error: Error | undefined, title: string) {
  const notify = useToast()
  useEffect(() => {
    if (error) notify({ title: error instanceof ApiClientError ? error.code : title,
      description: error.message, tone: 'error' })
  }, [error, notify, title])
}

async function eligibleOrders(api: ApiClient) {
  async function loadStatus(status_code: string) {
    const first = await api.listOrders({ status_code, page: 1, limit: 100 })
    const orders: OrderSummary[] = [...first.data]
    for (let page = 2; page <= first.meta.total_pages; page += 1) {
      const next = await api.listOrders({ status_code, page, limit: 100 })
      orders.push(...next.data)
    }
    return orders.filter((order) => order.status.status_code === status_code)
  }

  const groups = await Promise.all(['CONFIRMED', 'PROCESSING'].map(loadStatus))
  return [...new Map(groups.flat().map((order) => [order.order_id, order])).values()]
}

function DispatchDialog({ onClose, onDispatch }: { onClose: () => void; onDispatch: (shipment: Shipment) => void }) {
  const { api } = useAuth()
  const [revision, setRevision] = useState(0)
  const load = useCallback(() => eligibleOrders(api), [api, revision])
  const orders = useResource(load)
  useLoadErrorToast(orders.error, 'Không thể tải đơn chờ xuất kho')

  return (
    <MutationForm title="Tạo vận đơn và xuất kho" onClose={onClose} onSuccess={onClose}
      save={async (form) => {
        const order_id = Number(form.get('order_id'))
        if (!orders.data?.some((order) => order.order_id === order_id)) throw new Error('Chọn đơn hàng đủ điều kiện xuất kho.')
        const result = await api.createShipment({ order_id, shipper_id: Number(form.get('shipper_id')) || undefined })
        onDispatch(result.data)
        return result
      }}>
      <p className="text-sm text-slate-600">Xuất toàn bộ sản phẩm trong đơn vào một vận đơn.</p>
      {!orders.data?.length && <DataState {...orders} empty="Không có đơn đã xác nhận hoặc đang đóng gói."
        retry={() => setRevision((value) => value + 1)} />}
      <label className={labelStyle}>Đơn hàng
        <select className="field" name="order_id" required>
          <option value="">Chọn đơn hàng</option>
          {orders.data?.map((order) => <option key={order.order_id} value={order.order_id}>
            {order.order_code} · {order.status.status_name} · Kho #{order.warehouse_id}
          </option>)}
        </select>
      </label>
      <label className={labelStyle}>ID người giao hàng
        <input className="field" name="shipper_id" type="number" min={1} max={maxUserId} placeholder="Không bắt buộc" />
      </label>
    </MutationForm>
  )
}

function WarehouseOptions({ warehouses }: { warehouses: Warehouse[] }) {
  return <>
    <option value="">Không chọn kho</option>
    {warehouses.map((warehouse) => <option key={warehouse.warehouse_id} value={warehouse.warehouse_id}>
      {warehouse.warehouse_name} · #{warehouse.warehouse_id}
    </option>)}
  </>
}

interface ScanTarget {
  code: string
  shipmentId?: number
}

function ScanDialog({ target, onClose, onSuccess }: {
  target: ScanTarget; onClose: () => void; onSuccess: () => void
}) {
  const { api } = useAuth()
  const [status, setStatus] = useState<keyof typeof scanStatuses>('IN_TRANSIT')
  const [revision, setRevision] = useState(0)
  const load = useCallback(() => allWarehouses(api), [api, revision])
  const warehouses = useResource(load)
  useLoadErrorToast(warehouses.error, 'Không thể tải danh sách kho')
  const options = warehouses.data?.warehouses ?? []
  const requiresDestination = status === 'RETURNED' || target.shipmentId === undefined

  return (
    <MutationForm title="Quét mã cập nhật hành trình" onClose={onClose} onSuccess={onSuccess}
      save={(form) => {
        const shipmentId = target.shipmentId ?? Number(form.get('shipment_id'))
        if (!Number.isInteger(shipmentId) || shipmentId < 1 || shipmentId > maxUserId) throw new Error('Nhập ID vận đơn hợp lệ.')
        const warehouse_id = Number(form.get('warehouse_id')) || undefined
        if (requiresDestination && !warehouse_id) throw new Error('Chọn kho nhận hàng.')
        return api.scanTrackingEvent(shipmentId, {
          status_code: status, from_warehouse_id: Number(form.get('from_warehouse_id')) || undefined, warehouse_id,
        })
      }}>
      <p className={codeText}>{target.code}</p>
      {target.shipmentId === undefined && <label className={labelStyle}>ID vận đơn
        <input className="field" type="number" name="shipment_id" min={1} max={maxUserId} required />
      </label>}
      <label className={labelStyle}>Trạng thái hành trình
        <select className="field" value={status} onChange={(event) => setStatus(event.target.value as keyof typeof scanStatuses)}>
          {Object.entries(scanStatuses).map(([code, name]) => <option key={code} value={code}>{name}</option>)}
        </select>
      </label>
      {!options.length && <DataState {...warehouses} empty="Không có kho để chọn."
        retry={() => setRevision((value) => value + 1)} />}
      <div className="grid gap-3 sm:grid-cols-2">
        <label className={labelStyle}>Kho đi
          <select className="field" name="from_warehouse_id"><WarehouseOptions warehouses={options} /></select>
        </label>
        <label className={labelStyle}>Kho đến
          <select className="field" name="warehouse_id" required={requiresDestination}><WarehouseOptions warehouses={options} /></select>
        </label>
      </div>
      {status === 'RETURNED' && <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
        Hàng hoàn sẽ được nhập lại vào kho đến.
      </p>}
    </MutationForm>
  )
}

function TrackingResult({ tracking, onScan }: { tracking: ShipmentTrackingResponse; onScan?: (target: ScanTarget) => void }) {
  const { current_warehouse, current_status, delivered_at, order_id, events, tracking_code, shipment_id } = tracking

  function warehouseLabel(id: number) {
    return id === current_warehouse?.warehouse_id ? current_warehouse.warehouse_name : 'Kho #' + id
  }

  return (
    <article className={panel}>
      <header className="grid gap-3 border-b border-slate-200 p-4 sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="break-all font-mono text-xl font-semibold text-slate-900">{tracking_code}</h2>
          <Badge status={current_status} />
        </div>
        <dl className="grid gap-3 text-sm text-slate-700 sm:grid-cols-3">
          <div><dt className="text-slate-500">Đơn hàng</dt><dd>#{order_id}</dd></div>
          <div><dt className="text-slate-500">Kho hiện tại</dt><dd>{current_warehouse?.warehouse_name ?? '—'}</dd></div>
          <div><dt className="text-slate-500">Thời gian giao</dt><dd>{delivered_at ? dateFormat.format(new Date(delivered_at)) : '—'}</dd></div>
        </dl>
      </header>
      <section className="grid gap-4 p-4 sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h3 className="text-base font-semibold text-slate-900">Lịch sử giao hàng</h3>
          {onScan && <button className={secondaryButton} type="button"
            onClick={() => onScan({ code: tracking_code, shipmentId: shipment_id })}>
            <ScanLine size={17} aria-hidden="true" /> Quét mã cập nhật hành trình
          </button>}
        </div>
        {events.length === 0 ? <p className="text-sm text-slate-600">Chưa có sự kiện hành trình.</p> :
          <ol aria-label="Lịch sử giao hàng" className="ml-2 grid gap-5 border-l-2 border-indigo-100 pl-6">
            {events.map((event) => <li key={event.event_id} className="relative grid gap-1 before:absolute before:top-1.5 before:-left-[31px] before:size-3 before:rounded-full before:border-2 before:border-white before:bg-indigo-500">
              <p className="text-sm font-semibold text-slate-900">{event.status_name}</p>
              {(event.from_warehouse_id !== null || event.warehouse_id !== null) && <p className="text-sm text-slate-700">
                {event.from_warehouse_id !== null && warehouseLabel(event.from_warehouse_id)}
                {event.from_warehouse_id !== null && event.warehouse_id !== null && ' → '}
                {event.warehouse_id !== null && warehouseLabel(event.warehouse_id)}
              </p>}
              <p className="text-xs text-slate-600">
                <time dateTime={event.scanned_at}>{dateFormat.format(new Date(event.scanned_at))}</time> · Người quét #{event.scanned_by}
              </p>
            </li>)}
          </ol>}
      </section>
    </article>
  )
}

export function ShipmentsPage() {
  const { api, claims } = useAuth()
  const canManage = claims?.role === 'STAFF' || claims?.role === 'ADMIN'
  const [input, setInput] = useState('')
  const [query, setQuery] = useState<{ code: string }>()
  const [dispatch, setDispatch] = useState(false)
  const [newShipment, setNewShipment] = useState<Shipment>()
  const [scan, setScan] = useState<ScanTarget>()
  const load = useCallback(async () => {
    if (!query) throw new Error('Nhập mã vận đơn.')
    return (await api.getShipmentByTrackingCode(query.code)).data
  }, [api, query])
  const tracking = useResource(query ? load : null)
  useLoadErrorToast(tracking.error, 'Không thể tra cứu vận đơn')
  const canScanAtDestination = canManage && query && tracking.error instanceof ApiClientError &&
    tracking.error.code === 'ERR_WAREHOUSE_ACCESS_DENIED'

  function lookup(code: string) {
    const trimmed = code.trim()
    if (trimmed) {
      setInput(trimmed)
      setQuery({ code: trimmed })
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    lookup(input)
  }

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold text-slate-900">Vận đơn &amp; Hành trình</h1>
        {canManage && <button className="primary-button inline-flex items-center gap-2" type="button" onClick={() => setDispatch(true)}>
          <Truck size={17} aria-hidden="true" /> Tạo vận đơn và xuất kho
        </button>}
      </div>
      <section className="rounded-xl border border-indigo-200 bg-indigo-50 px-4 py-6 sm:p-8">
        <h2 className="text-xl font-semibold text-slate-900 sm:text-2xl">Tra cứu hành trình vận đơn</h2>
        <form onSubmit={submit} className="mt-4 flex flex-wrap items-end gap-3">
          <label className={labelStyle + ' flex-[1_1_16rem]'}>Mã vận đơn
            <input className="field bg-white font-mono" placeholder="TRK-…" type="search" required value={input}
              onChange={(event) => setInput(event.target.value)} />
          </label>
          <button type="submit" className="primary-button inline-flex items-center gap-2" disabled={!input.trim()}>
            <Search size={17} aria-hidden="true" /> Tra cứu
          </button>
        </form>
      </section>
      {newShipment && <section className={panel + ' flex flex-wrap items-center justify-between gap-3 px-4 py-3'}>
        <p className="text-sm text-slate-700">
          Đã tạo vận đơn <strong className={codeText}>{newShipment.tracking_code}</strong> · ID #{newShipment.shipment_id}
        </p>
        <button type="button" className={secondaryButton} onClick={() => lookup(newShipment.tracking_code)}>Xem hành trình</button>
      </section>}
      {query && <DataState {...tracking} empty="Không có thông tin vận đơn." retry={() => lookup(query.code)}>
        {tracking.data && <TrackingResult tracking={tracking.data}
          onScan={canManage && !['DELIVERED', 'RETURNED'].includes(tracking.data.current_status) ? setScan : undefined} />}
      </DataState>}
      {canScanAtDestination && <button type="button" className={secondaryButton + ' justify-self-start'}
        onClick={() => setScan({ code: query.code })}>
        <ScanLine size={17} aria-hidden="true" /> Quét mã cập nhật hành trình
      </button>}
      {dispatch && <DispatchDialog onClose={() => setDispatch(false)} onDispatch={setNewShipment} />}
      {scan && <ScanDialog target={scan} onClose={() => setScan(undefined)}
        onSuccess={() => { setScan(undefined); lookup(scan.code) }} />}
    </div>
  )
}
