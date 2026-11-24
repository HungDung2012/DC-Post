import { MapPin, Plus } from 'lucide-react'
import { useCallback, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { maxUserId } from '../lib/jwt'
import type { Warehouse } from '../types/api'
import {
  cell, codeText, DataState, formText, MutationForm, pageSize, Pagination, panel,
  PermissionNotice, rowAction, Table, useResource,
} from './pageSupport'

type WarehouseAction = { kind: 'create' } | { kind: 'edit' | 'assign' | 'remove'; warehouse: Warehouse }

const titles = {
  create: 'Thêm kho', edit: 'Sửa kho', assign: 'Phân công nhân viên', remove: 'Gỡ nhân viên',
}

export function WarehousesPage() {
  const { api, claims } = useAuth()
  const admin = claims?.role === 'ADMIN'
  const allowed = admin || claims?.role === 'STAFF'
  const [page, setPage] = useState(1)
  const [revision, setRevision] = useState(0)
  const [action, setAction] = useState<WarehouseAction>()
  const load = useCallback(() => api.listWarehouses({ page, limit: pageSize }), [api, page, revision])
  const warehouses = useResource(allowed ? load : null)
  const refresh = () => setRevision((value) => value + 1)

  if (!allowed) return <PermissionNotice resource="kho" />

  function save(form: FormData) {
    if (!action) throw new Error('Chọn thao tác kho.')
    const body = { warehouse_name: formText(form, 'warehouse_name'), address: formText(form, 'address') }
    if (action.kind === 'create') return api.createWarehouse(body)
    const id = action.warehouse.warehouse_id
    if (action.kind === 'edit') return api.updateWarehouse(id, body)
    const userId = Number(form.get('user_id'))
    if (action.kind === 'assign') return api.assignStaff(id, { user_id: userId })
    return api.removeStaff(id, userId)
  }

  const selected = action && 'warehouse' in action ? action.warehouse : undefined
  const staffOperation = action?.kind === 'assign' || action?.kind === 'remove'

  return (
    <section className={panel}>
      <div className="flex flex-wrap items-center justify-between gap-3 p-4">
        <div>
          <h2 className="font-semibold text-slate-900">Danh sách kho / bưu cục</h2>
          <p className="mt-1 text-sm text-slate-600">Địa điểm vận hành và phân công nhân viên kho.</p>
        </div>
        {admin && <button type="button" className="primary-button inline-flex items-center gap-2" onClick={() => setAction({ kind: 'create' })}>
          <Plus size={16} aria-hidden="true" /> Thêm kho
        </button>}
      </div>
      <DataState {...warehouses} empty="Chưa có kho hoặc bưu cục." retry={refresh}>
        {!!warehouses.data?.data.length && (
          <Table label="Danh sách kho" headers={['Mã kho', 'Kho / Bưu cục', 'Địa chỉ', ...(admin ? ['Thao tác'] : [])]}>
            {warehouses.data.data.map((warehouse) => (
              <tr key={warehouse.warehouse_id}>
                <td className={cell}><span className={codeText}>#{warehouse.warehouse_id}</span></td>
                <td className={cell + ' min-w-36 font-semibold text-slate-900'}>{warehouse.warehouse_name}</td>
                <td className={cell + ' min-w-44'}>
                  <span className="inline-flex items-start gap-1.5"><MapPin size={15} className="mt-0.5 shrink-0 text-slate-400" aria-hidden="true" />{warehouse.address}</span>
                </td>
                {admin && <td className={cell}>
                  <div className="flex flex-wrap gap-1">
                    <button className={rowAction} type="button" onClick={() => setAction({ kind: 'edit', warehouse })}>Sửa kho</button>
                    <button className={rowAction} type="button" onClick={() => setAction({ kind: 'assign', warehouse })}>Phân công</button>
                    <button className={rowAction + ' !text-rose-700 hover:!bg-rose-50'} type="button" onClick={() => setAction({ kind: 'remove', warehouse })}>Gỡ nhân viên</button>
                  </div>
                </td>}
              </tr>
            ))}
          </Table>
        )}
      </DataState>
      <Pagination meta={warehouses.data?.meta} onPage={setPage} />
      {admin && action && (
        <MutationForm title={titles[action.kind]} onClose={() => setAction(undefined)} save={save}
          danger={action.kind === 'remove'}
          onSuccess={() => {
            if (action.kind === 'create') setPage(1)
            if (!staffOperation) refresh()
            setAction(undefined)
          }}>
          {staffOperation ? <>
            <p className="text-sm text-slate-600">
              Kho <strong className="text-slate-900">{selected?.warehouse_name}</strong>
            </p>
            <label className="text-sm font-medium">Mã người dùng
              <input className="field" name="user_id" type="number" min={1} max={maxUserId} step={1} required />
            </label>
          </> : <>
            <label className="text-sm font-medium">Tên kho
              <input className="field" name="warehouse_name" defaultValue={selected?.warehouse_name}
                maxLength={255} pattern=".*\S.*" required />
            </label>
            <label className="text-sm font-medium">Địa chỉ
              <input className="field" name="address" defaultValue={selected?.address}
                maxLength={255} pattern=".*\S.*" required />
            </label>
          </>}
        </MutationForm>
      )}
    </section>
  )
}

