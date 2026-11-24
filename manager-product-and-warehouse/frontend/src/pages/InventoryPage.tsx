import { ArrowDownToLine, ArrowUpFromLine, ClipboardCheck, Plus, Search, Trash2 } from 'lucide-react'
import { useCallback, useId, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import { Badge } from '../components/ui/Badge'
import { useAuth } from '../context/AuthContext'
import { maxUserId } from '../lib/jwt'
import type { InventoryTxFilter, Warehouse } from '../types/api'
import {
  allWarehouses, cell, codeText, DataState, formText, MutationForm, numberFormat, numericCell,
  pageSize, Pagination, panel, PermissionNotice, secondaryButton, Table, useResource, useSearch,
} from './pageSupport'

const operations = {
  import: { label: 'Nhập kho', icon: ArrowDownToLine },
  export: { label: 'Xuất kho nội bộ', icon: ArrowUpFromLine },
  adjust: { label: 'Kiểm kê / Điều chỉnh', icon: ClipboardCheck },
}
type Operation = keyof typeof operations
const stockOperations: Operation[] = ['import', 'export', 'adjust']

const tabs = [{ key: 'stock', label: 'Hàng tồn kho' }, { key: 'history', label: 'Lịch sử ra/vào kho' }] as const
type Tab = (typeof tabs)[number]['key']

const transactionTypes = {
  IMPORT: 'Nhập kho', EXPORT: 'Xuất kho', RESERVE: 'Giữ hàng', RELEASE: 'Giải phóng giữ chỗ',
  RETURN: 'Hoàn kho', ADJUST: 'Kiểm kê',
}
const transactionDate = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'short', timeStyle: 'short', timeZone: 'UTC',
})

function WarehouseOptions({ warehouses }: { warehouses: Warehouse[] }) {
  return warehouses.map((warehouse) => <option key={warehouse.warehouse_id} value={warehouse.warehouse_id}>
    {warehouse.warehouse_name} · #{warehouse.warehouse_id}
  </option>)
}

function StockDialog({ operation, warehouse, onClose, onSuccess }: {
  operation: Operation; warehouse: Warehouse; onClose: () => void; onSuccess: () => void
}) {
  const { api } = useAuth()
  const [rows, setRows] = useState([0])
  const nextRow = useRef(1)
  const adjustment = operation === 'adjust'
  const warehouse_id = warehouse.warehouse_id

  function save(form: FormData) {
    if (adjustment) return api.adjustStock({
      warehouse_id, product_id: Number(form.get('product-0')), actual_quantity: Number(form.get('quantity-0')),
    })
    const items = rows.map((key) => ({
      product_id: Number(form.get('product-' + key)),
      quantity: Number(form.get('quantity-' + key)),
    }))
    return operation === 'import' ? api.importStock({ warehouse_id, items }) : api.exportStock({ warehouse_id, items })
  }

  return (
    <MutationForm title={operations[operation].label} save={save} onClose={onClose} onSuccess={onSuccess}>
      <p className="text-sm text-slate-600">Kho <strong className="text-slate-900">{warehouse.warehouse_name}</strong></p>
      {operation === 'export' && <p className="rounded-lg bg-amber-50 p-2.5 text-sm text-amber-900">
        Dùng cho xuất nội bộ hoặc xuất hủy hàng hỏng.
      </p>}
      {rows.map((key, index) => (
        <div key={key} className="flex flex-wrap items-end gap-2 rounded-lg border border-slate-200 p-3">
          <label className="min-w-24 flex-1 text-sm font-medium">Mã sản phẩm {index + 1}
            <input name={'product-' + key} className="field" type="number" min={1} max={maxUserId} step={1} required />
          </label>
          <label className="min-w-24 flex-1 text-sm font-medium">{adjustment ? 'Số lượng thực tế' : 'Số lượng ' + (index + 1)}
            <input name={'quantity-' + key} className="field" type="number" min={adjustment ? 0 : 1} max={maxUserId} step={1} required />
          </label>
          {rows.length > 1 && <button type="button" className="icon-button mb-0.5 text-rose-700"
            aria-label={'Xóa dòng ' + (index + 1)} onClick={() => setRows(rows.filter((row) => row !== key))}>
            <Trash2 size={16} aria-hidden="true" />
          </button>}
        </div>
      ))}
      {!adjustment && <button type="button" className={secondaryButton + ' justify-self-start'}
        onClick={() => { const key = nextRow.current++; setRows([...rows, key]) }}>
        <Plus size={15} aria-hidden="true" /> Thêm dòng sản phẩm
      </button>}
    </MutationForm>
  )
}

function HistoryFilters({ warehouses, onApply }: { warehouses: Warehouse[]; onApply: (filters: InventoryTxFilter) => void }) {
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
      warehouse_id: Number(form.get('warehouse_id')) || undefined,
      product_id: Number(form.get('product_id')) || undefined,
      shipment_id: Number(form.get('shipment_id')) || undefined,
      transaction_type: formText(form, 'transaction_type'),
      from_date,
      to_date,
    })
  }

  return (
    <form className="grid gap-3 p-4" onSubmit={apply} onReset={() => { setDateError(false); onApply({}) }}>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6">
        <label className="min-w-0 text-sm font-medium">Kho giao dịch
          <select name="warehouse_id" className="field">
            <option value="">Tất cả kho</option>
            <WarehouseOptions warehouses={warehouses} />
          </select>
        </label>
        <label className="text-sm font-medium">Mã sản phẩm
          <input name="product_id" type="number" min={1} max={maxUserId} step={1} className="field" />
        </label>
        <label className="text-sm font-medium">Mã vận đơn
          <input name="shipment_id" type="number" min={1} max={maxUserId} step={1} className="field" />
        </label>
        <label className="text-sm font-medium">Loại giao dịch
          <select name="transaction_type" className="field">
            <option value="">Tất cả giao dịch</option>
            {Object.entries(transactionTypes).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
          </select>
        </label>
        <label className="min-w-0 text-sm font-medium">Từ ngày (UTC)
          <input name="from_date" type="date" className="field min-w-0" />
        </label>
        <label className="min-w-0 text-sm font-medium">Đến ngày (UTC)
          <input name="to_date" type="date" className="field min-w-0" />
        </label>
      </div>
      {dateError && <p role="alert" className="text-sm text-rose-800">Ngày kết thúc phải từ ngày bắt đầu trở đi.</p>}
      <div className="flex gap-2">
        <button type="submit" className="primary-button">Áp dụng</button>
        <button type="reset" className={secondaryButton}>Xóa bộ lọc</button>
      </div>
    </form>
  )
}

export function InventoryPage() {
  const { api, claims } = useAuth()
  const allowed = claims?.role === 'ADMIN' || claims?.role === 'STAFF'
  const [tab, setTab] = useState<Tab>('stock')
  const tabId = useId()
  const tabButtons = useRef<Partial<Record<Tab, HTMLButtonElement>>>({})
  const [warehouseId, setWarehouseId] = useState<number>()
  const [warehouseRevision, setWarehouseRevision] = useState(0)
  const [revision, setRevision] = useState(0)
  const [operation, setOperation] = useState<Operation>()
  const [stockPagination, setStockPagination] = useState({ search: '', page: 1 })
  const [historyPage, setHistoryPage] = useState(1)
  const [filters, setFilters] = useState<InventoryTxFilter>({})
  const [lowStock, setLowStock] = useState(false)
  const { input, setInput, search } = useSearch()
  const stockPage = stockPagination.search === search ? stockPagination.page : 1
  const loadWarehouses = useCallback(() => allWarehouses(api), [api, warehouseRevision])
  const warehouses = useResource(allowed ? loadWarehouses : null)
  const options = warehouses.data?.warehouses ?? []
  const selected = warehouseId ?? (claims?.role === 'ADMIN' ? options[0]?.warehouse_id : undefined)
  const selectedWarehouse = options.find((warehouse) => warehouse.warehouse_id === selected)
  const loadStock = useCallback(() => api.listWarehouseInventory(selected!, {
    page: stockPage, limit: pageSize, search, low_stock: lowStock || undefined,
  }), [api, selected, stockPage, search, lowStock, revision])
  const loadHistory = useCallback(() => api.listInventoryTransactions({
    ...filters, page: historyPage, limit: pageSize,
  }), [api, historyPage, filters, revision])
  const stock = useResource(allowed && tab === 'stock' && selectedWarehouse ? loadStock : null)
  const history = useResource(allowed && tab === 'history' ? loadHistory : null)
  const refresh = () => setRevision((value) => value + 1)

  if (!allowed) return <PermissionNotice resource="tồn kho" />

  function switchTab(event: KeyboardEvent<HTMLButtonElement>) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
    event.preventDefault()
    const next = event.key === 'Home' ? 'stock' : event.key === 'End' ? 'history' : tab === 'stock' ? 'history' : 'stock'
    setTab(next)
    tabButtons.current[next]?.focus()
  }

  return (
    <section className={panel}>
      <div className="flex gap-1 border-b border-slate-200 px-3 pt-1" role="tablist" aria-label="Quản lý tồn kho">
        {tabs.map(({ key, label }) => (
          <button key={key} ref={(element) => { if (element) tabButtons.current[key] = element }}
            id={tabId + '-' + key} type="button" role="tab" aria-selected={tab === key}
            aria-controls={tabId + '-panel-' + key} tabIndex={tab === key ? 0 : -1}
            onClick={() => setTab(key)} onKeyDown={switchTab}
            className={'border-b-2 px-3 py-3 text-sm font-semibold ' +
              (tab === key ? 'border-indigo-600 text-indigo-700' : 'border-transparent text-slate-600 hover:text-indigo-700')}>
            {label}
          </button>
        ))}
      </div>

      <div id={tabId + '-panel-stock'} role="tabpanel" aria-labelledby={tabId + '-stock'} hidden={tab !== 'stock'}>
        {tab === 'stock' && <>
          <div className="grid gap-3 p-4">
            <div className="flex flex-wrap items-end gap-3">
              <label className="w-full max-w-xs text-sm font-medium text-slate-700">Kho / Bưu cục
                <select className="field" value={selected ?? ''} disabled={!options.length}
                  onChange={(event) => { setWarehouseId(Number(event.target.value)); setStockPagination({ search, page: 1 }) }}>
                  <option value="" disabled>Chọn kho</option>
                  <WarehouseOptions warehouses={options} />
                </select>
              </label>
              <label className="w-full max-w-xs text-sm font-medium text-slate-700">Tìm hàng tồn kho
                <span className="relative block">
                  <Search size={16} aria-hidden="true" className="pointer-events-none absolute top-2.5 left-3 text-slate-400" />
                  <input className="field pl-9" type="search" value={input} placeholder="Tên hoặc mã sản phẩm"
                    onChange={(event) => { setInput(event.target.value); setStockPagination({ search, page: 1 }) }} />
                </span>
              </label>
              <label className="inline-flex min-h-9 items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 text-sm font-medium text-amber-900">
                <input type="checkbox" checked={lowStock} className="size-4 accent-indigo-600"
                  onChange={(event) => { setLowStock(event.target.checked); setStockPagination({ search, page: 1 }) }} />
                Chỉ hiện tồn thấp
              </label>
            </div>
            <div className="flex flex-wrap gap-2">
              {stockOperations.map((key) => {
                const { icon: Icon, label } = operations[key]
                return <button key={key} type="button" disabled={!selectedWarehouse}
                  className={key === 'import' ? 'primary-button inline-flex items-center gap-2' : secondaryButton}
                  onClick={() => setOperation(key)}><Icon size={16} aria-hidden="true" />{label}</button>
              })}
            </div>
          </div>
          {selectedWarehouse ? (
            <>
              <DataState {...stock} empty={lowStock ? 'Không có mặt hàng tồn thấp phù hợp.' : 'Không tìm thấy hàng tồn kho.'} retry={refresh}>
                {!!stock.data?.data.length && (
                  <Table label="Hàng tồn kho" headers={['Mã sản phẩm', 'Tên sản phẩm', { label: 'Tồn thực tế', numeric: true },
                    { label: 'Giữ chỗ', numeric: true }, { label: 'Khả dụng', numeric: true }]}>
                    {stock.data.data.map((record) => {
                      const available = record.available_quantity
                      return <tr key={record.inventory_id}>
                        <td className={cell}><span className={codeText}>{record.product.product_code}</span></td>
                        <td className={cell + ' min-w-40 font-semibold text-slate-900'}>{record.product.product_name}</td>
                        <td className={numericCell}>{numberFormat.format(record.quantity)}</td>
                        <td className={numericCell}>{numberFormat.format(record.reserved_quantity)}</td>
                        <td className={numericCell + ' font-semibold'}>{available <= 10
                          ? <Badge status={available <= 0 ? 'CANCELLED' : 'PENDING'} label={numberFormat.format(available)} />
                          : numberFormat.format(available)}</td>
                      </tr>
                    })}
                  </Table>
                )}
              </DataState>
              <Pagination meta={stock.data?.meta} onPage={(page) => setStockPagination({ search, page })} />
            </>
          ) : <DataState {...warehouses} retry={() => setWarehouseRevision((value) => value + 1)}
            empty={options.length ? 'Chọn kho để xem số lượng tồn.' : 'Chưa có kho để hiển thị.'} />}
        </>}
      </div>

      <div id={tabId + '-panel-history'} role="tabpanel" aria-labelledby={tabId + '-history'} hidden={tab !== 'history'}>
        <HistoryFilters warehouses={options} onApply={(next) => { setFilters(next); setHistoryPage(1) }} />
        {tab === 'history' && <>
          {warehouses.error && <DataState loading={false} error={warehouses.error} empty="" retry={() => setWarehouseRevision((value) => value + 1)} />}
          <DataState {...history} empty="Không tìm thấy giao dịch phù hợp." retry={refresh}>
            {!!history.data?.data.length && (
              <Table label="Lịch sử ra/vào kho" headers={['Giao dịch', 'Thời gian (UTC)', 'Kho / Bưu cục', 'Sản phẩm', 'Loại',
                { label: 'Thay đổi', numeric: true }, 'Vận đơn', 'Nhân viên']}>
                {history.data.data.map((tx) => (
                  <tr key={tx.transaction_id}>
                    <td className={cell}><span className={codeText}>#{tx.transaction_id}</span></td>
                    <td className={cell + ' whitespace-nowrap'}>{transactionDate.format(new Date(tx.created_at))}</td>
                    <td className={cell + ' min-w-32'}>{tx.warehouse.warehouse_name}</td>
                    <td className={cell + ' min-w-36'}>
                      <p className="font-medium text-slate-900">{tx.product.product_name}</p>
                      <p className={codeText}>{tx.product.product_code}</p>
                    </td>
                    <td className={cell + ' whitespace-nowrap'}><Badge status={tx.transaction_type} /></td>
                    <td className={numericCell + ' font-semibold ' + (tx.quantity < 0 ? 'text-rose-800' : tx.quantity > 0 ? 'text-emerald-800' : '')}>
                      {tx.quantity < 0 ? '−' : tx.quantity > 0 ? '+' : ''}{numberFormat.format(Math.abs(tx.quantity))}
                    </td>
                    <td className={cell}>{tx.shipment_id === null ? '—' : '#' + tx.shipment_id}</td>
                    <td className={cell}>#{tx.performed_by}</td>
                  </tr>
                ))}
              </Table>
            )}
          </DataState>
          <Pagination meta={history.data?.meta} onPage={setHistoryPage} />
        </>}
      </div>

      {operation && selectedWarehouse && (
        <StockDialog operation={operation} warehouse={selectedWarehouse} onClose={() => setOperation(undefined)}
          onSuccess={() => { setOperation(undefined); refresh() }} />
      )}
    </section>
  )
}

