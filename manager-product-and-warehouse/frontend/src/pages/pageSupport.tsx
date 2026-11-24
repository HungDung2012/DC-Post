import { ChevronLeft, ChevronRight, CircleAlert, LockKeyhole } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { EmptyState } from '../components/ui/EmptyState'
import { Modal } from '../components/ui/Modal'
import { Spinner } from '../components/ui/Spinner'
import { useToast } from '../context/ToastContext'
import { ApiClientError } from '../lib/api'
import type { ApiClient } from '../lib/api'
import type { PaginationMeta, Warehouse } from '../types/api'

export const pageSize = 20
export const panel = 'min-w-0 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xs'
export const secondaryButton = 'inline-flex items-center justify-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-45'
export const rowAction = 'rounded-md px-2 py-1.5 text-xs font-semibold whitespace-nowrap text-indigo-700 hover:bg-indigo-50'
export const cell = 'px-4 py-2.5 align-middle'
export const numericCell = cell + ' text-right tabular-nums'
export const codeText = 'font-mono text-xs font-semibold text-indigo-700'
export const numberFormat = new Intl.NumberFormat('vi-VN')
export const currencyFormat = new Intl.NumberFormat('vi-VN', {
  style: 'currency', currency: 'VND', maximumFractionDigits: 2,
})
export const dateFormat = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh',
})

export function useResource<T>(load: (() => Promise<T>) | null) {
  const [result, setResult] = useState<{ load: typeof load; data?: T; error?: Error }>()
  useEffect(() => {
    if (!load) return
    let current = true
    load().then(
      (data) => { if (current) setResult({ load, data }) },
      (error: Error) => { if (current) setResult({ load, error }) },
    )
    return () => { current = false }
  }, [load])

  return {
    data: result?.load === load ? result?.data : undefined,
    error: result?.load === load ? result?.error : undefined,
    loading: load !== null && result?.load !== load,
  }
}

export function useSearch() {
  const [input, setInput] = useState('')
  const [search, setSearch] = useState('')
  useEffect(() => {
    const timer = window.setTimeout(() => setSearch(input.trim()), 250)
    return () => window.clearTimeout(timer)
  }, [input])
  return { input, setInput, search }
}

export async function allWarehouses(api: ApiClient) {
  const response = await api.listWarehouses({ page: 1, limit: 100 })
  const warehouses: Warehouse[] = [...response.data]
  for (let page = 2; page <= response.meta.total_pages; page += 1) {
    const next = await api.listWarehouses({ page, limit: 100 })
    warehouses.push(...next.data)
  }
  return { warehouses, total: response.meta.total_items }
}

export function PermissionNotice({ resource }: { resource: string }) {
  return <EmptyState icon={LockKeyhole} title={'Bạn không có quyền truy cập ' + resource}
    description="Khu vực này dành cho nhân viên và quản trị viên." />
}

export function Table({ label, headers, children }: {
  label: string
  headers: Array<string | { label: string; numeric: true }>
  children: ReactNode
}) {
  return (
    <div className="overflow-x-auto">
      <table aria-label={label} className="w-full text-left text-sm text-slate-700">
        <thead className="border-y border-slate-200 bg-slate-50 text-xs font-semibold whitespace-nowrap text-slate-600">
          <tr>{headers.map((header) => typeof header === 'string'
            ? <th key={header} scope="col" className={cell}>{header}</th>
            : <th key={header.label} scope="col" className={numericCell}>{header.label}</th>)}</tr>
        </thead>
        <tbody className="divide-y divide-slate-100 [&>tr:hover]:bg-slate-50/70">{children}</tbody>
      </table>
    </div>
  )
}

export function Pagination({ meta, onPage }: { meta?: PaginationMeta; onPage: (page: number) => void }) {
  if (!meta || meta.total_pages < 1) return null
  const { page, limit, total_items, total_pages } = meta
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 border-t border-slate-200 px-4 py-2.5 text-xs text-slate-600">
      <span>{numberFormat.format((page - 1) * limit + 1)}–{numberFormat.format(Math.min(page * limit, total_items))} / {numberFormat.format(total_items)} kết quả</span>
      <nav aria-label="Phân trang" className="flex items-center gap-2">
        <button type="button" className="icon-button disabled:opacity-40" aria-label="Trang trước" disabled={page <= 1} onClick={() => onPage(page - 1)}>
          <ChevronLeft size={17} aria-hidden="true" />
        </button>
        <span className="tabular-nums">Trang {page} / {total_pages}</span>
        <button type="button" className="icon-button disabled:opacity-40" aria-label="Trang sau" disabled={page >= total_pages} onClick={() => onPage(page + 1)}>
          <ChevronRight size={17} aria-hidden="true" />
        </button>
      </nav>
    </div>
  )
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Vui lòng thử lại.'
}

export function DataState({ loading, error, empty, retry, children }: {
  loading: boolean; error?: Error; empty: string; retry?: () => void; children?: ReactNode
}) {
  if (loading) return <div className="grid min-h-28 place-items-center"><Spinner label="Đang tải dữ liệu" /></div>
  if (error) {
    return (
      <div className="flex items-start gap-3 px-4 py-5 text-sm text-rose-800" role="alert">
        <CircleAlert size={18} className="shrink-0" aria-hidden="true" />
        <div className="flex-1">
          <p className="font-semibold">{error instanceof ApiClientError ? error.code : 'Không thể tải dữ liệu'}</p>
          <p>{errorMessage(error)}</p>
        </div>
        {retry && <button type="button" className={secondaryButton} onClick={retry}>Thử lại</button>}
      </div>
    )
  }
  return children || <p className="px-4 py-9 text-center text-sm text-slate-600">{empty}</p>
}

export function MutationForm({ title, onClose, onSuccess, save, children, danger = false }: {
  title: string
  onClose: () => void
  onSuccess: () => void
  save: (form: FormData) => Promise<{ message: string }>
  children: ReactNode
  danger?: boolean
}) {
  const notify = useToast()
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    const form = new FormData(event.currentTarget)
    setPending(true)
    setError('')
    try {
      const result = await save(form)
      notify({ title: result.message || 'Đã lưu thay đổi', tone: 'success' })
      onSuccess()
    } catch (failure) {
      const message = errorMessage(failure)
      const code = failure instanceof ApiClientError ? failure.code : undefined
      setError([code, message].filter(Boolean).join(' · '))
      notify({ title: code ?? 'Không thể lưu thay đổi', description: message, tone: 'error' })
    } finally {
      setPending(false)
    }
  }
  return (
    <Modal title={title} open onClose={() => { if (!pending) onClose() }}>
      <form onSubmit={submit}>
        <fieldset disabled={pending} className="grid min-w-0 gap-4">
          {children}
          {error && <p className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800" role="alert">{error}</p>}
          <div className="flex justify-end gap-2 border-t border-slate-200 pt-4">
            <button className={secondaryButton} type="button" onClick={onClose}>Hủy</button>
            <button className={danger ? 'primary-button !bg-rose-700 hover:!bg-rose-800' : 'primary-button'}
              type="submit" disabled={pending}>{pending ? 'Đang gửi…' : 'Xác nhận'}</button>
          </div>
        </fieldset>
      </form>
    </Modal>
  )
}

export function formText(form: FormData, name: string) {
  return String(form.get(name) ?? '').trim()
}

