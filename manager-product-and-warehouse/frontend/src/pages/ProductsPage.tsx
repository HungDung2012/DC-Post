import { Plus, Search } from 'lucide-react'
import { useCallback, useState } from 'react'
import { Drawer } from '../components/ui/Drawer'
import { useAuth } from '../context/AuthContext'
import type { Product } from '../types/api'
import {
  cell, codeText, currencyFormat, DataState, formText, MutationForm, numericCell, pageSize,
  Pagination, panel, rowAction, Table, useResource, useSearch,
} from './pageSupport'

export function ProductsPage() {
  const { api, claims } = useAuth()
  const canEdit = claims?.role === 'ADMIN' || claims?.role === 'STAFF'
  const [pagination, setPagination] = useState({ search: '', page: 1 })
  const [revision, setRevision] = useState(0)
  const { input, setInput, search } = useSearch()
  const [editing, setEditing] = useState<Product | 'new'>()
  const [detailId, setDetailId] = useState<number>()
  const page = pagination.search === search ? pagination.page : 1
  const load = useCallback(() => api.listProducts({ page, limit: pageSize, search }), [api, page, search, revision])
  const loadDetail = useCallback(() => api.getProduct(detailId!), [api, detailId])
  const products = useResource(load)
  const detail = useResource(detailId === undefined ? null : loadDetail)
  const refresh = () => setRevision((value) => value + 1)
  const original = editing === 'new' ? undefined : editing

  function save(form: FormData) {
    const body = { product_name: formText(form, 'product_name'), unit_price: Number(form.get('unit_price')) }
    return original
      ? api.updateProduct(original.product_id, body)
      : api.createProduct({ product_code: formText(form, 'product_code'), ...body })
  }

  return (
    <section className={panel}>
      <div className="flex flex-wrap items-end justify-between gap-3 p-4">
        <label className="w-full max-w-sm text-sm font-medium text-slate-700">Tìm sản phẩm
          <span className="relative block">
            <Search size={16} aria-hidden="true" className="pointer-events-none absolute top-2.5 left-3 text-slate-400" />
            <input type="search" className="field pl-9" placeholder="Tên hoặc mã sản phẩm" value={input}
              onChange={(event) => { setInput(event.target.value); setPagination({ search, page: 1 }) }} />
          </span>
        </label>
        {canEdit && <button className="primary-button inline-flex items-center gap-2" type="button" onClick={() => setEditing('new')}>
          <Plus size={16} aria-hidden="true" /> Thêm sản phẩm
        </button>}
      </div>
      <DataState {...products} empty="Không tìm thấy sản phẩm." retry={refresh}>
        {!!products.data?.data.length && (
          <Table label="Danh mục sản phẩm" headers={['Mã sản phẩm', 'Tên sản phẩm', { label: 'Đơn giá', numeric: true }, 'Thao tác']}>
            {products.data.data.map((product) => (
              <tr key={product.product_id}>
                <td className={cell}><span className={codeText}>{product.product_code}</span></td>
                <td className={cell + ' min-w-40 font-semibold text-slate-900'}>{product.product_name}</td>
                <td className={numericCell + ' whitespace-nowrap'}>{currencyFormat.format(product.unit_price)}</td>
                <td className={cell}>
                  <div className="flex flex-wrap gap-1">
                    <button className={rowAction} type="button" onClick={() => setDetailId(product.product_id)}>Xem chi tiết</button>
                    {canEdit && <button className={rowAction} type="button" onClick={() => setEditing(product)}>Sửa sản phẩm</button>}
                  </div>
                </td>
              </tr>
            ))}
          </Table>
        )}
      </DataState>
      <Pagination meta={products.data?.meta} onPage={(next) => setPagination({ search, page: next })} />
      <Drawer open={detailId !== undefined} title="Chi tiết sản phẩm" onClose={() => setDetailId(undefined)}>
        <DataState {...detail} empty="Không có thông tin sản phẩm.">
          {detail.data && (
            <dl className="grid gap-3">
              <div className="rounded-lg bg-indigo-50 p-4">
                <dt className="text-xs font-semibold text-indigo-800">Tên sản phẩm</dt>
                <dd className="mt-1 text-xl font-semibold text-slate-900">{detail.data.data.product_name}</dd>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="rounded-lg border border-slate-200 p-3">
                  <dt className="text-xs font-semibold text-slate-600">Mã sản phẩm</dt>
                  <dd className={codeText + ' mt-1'}>{detail.data.data.product_code}</dd>
                </div>
                <div className="rounded-lg border border-slate-200 p-3">
                  <dt className="text-xs font-semibold text-slate-600">Đơn giá</dt>
                  <dd className="mt-1 font-semibold tabular-nums">{currencyFormat.format(detail.data.data.unit_price)}</dd>
                </div>
              </div>
            </dl>
          )}
        </DataState>
      </Drawer>
      {canEdit && editing && (
        <MutationForm title={original ? 'Sửa sản phẩm' : 'Thêm sản phẩm'} onClose={() => setEditing(undefined)}
          save={save} onSuccess={() => {
            if (!original) setPagination({ search, page: 1 })
            refresh()
            setEditing(undefined)
          }}>
          <label className="text-sm font-medium">Mã sản phẩm
            <input className="field disabled:bg-slate-100" name="product_code" defaultValue={original?.product_code}
              disabled={!!original} maxLength={255} pattern=".*\S.*" required />
          </label>
          <label className="text-sm font-medium">Tên sản phẩm
            <input className="field" name="product_name" defaultValue={original?.product_name}
              maxLength={255} pattern=".*\S.*" required />
          </label>
          <label className="text-sm font-medium">Đơn giá (₫)
            <input className="field" name="unit_price" type="number" min={0.01} max={9999999999999.99}
              step={0.01} defaultValue={original?.unit_price} required />
          </label>
        </MutationForm>
      )}
    </section>
  )
}

