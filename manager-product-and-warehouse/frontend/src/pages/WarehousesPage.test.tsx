// @vitest-environment jsdom
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { expect, it } from 'vitest'
import { fill, list, ok, openPage, warehouse } from './pageTestSupport'

it('paginates warehouse data and reserves mutations for admins', async () => {
  const { fetcher } = openPage('kho', 'STAFF', (url) =>
    list([url.searchParams.get('page') === '2' ? { ...warehouse, warehouse_name: 'Kho Đà Nẵng' } : warehouse], url, 21))
  expect(await screen.findByText('Kho Hà Nội')).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Thêm kho' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Phân công' })).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Trang sau' }))
  expect(await screen.findByText('Kho Đà Nẵng')).toBeVisible()
  expect(fetcher).toHaveBeenCalledWith('/api/v1/warehouses?page=2&limit=20', expect.objectContaining({ method: 'GET' }))
})

it('shows a customer permission notice without a warehouse request', () => {
  const { fetcher } = openPage('kho', 'CUSTOMER', () => ok([]))
  expect(screen.getByText('Bạn không có quyền truy cập kho')).toBeVisible()
  expect(fetcher.mock.calls.every(([url]) => !String(url).includes('/warehouses'))).toBe(true)
})

it('creates and edits a warehouse, assigns staff and removes staff by user ID', async () => {
  const { fetcher } = openPage('kho', 'ADMIN', (url, method) =>
    method === 'GET' ? list([warehouse], url) : ok(null))
  await screen.findByText('Kho Hà Nội')
  const calls = [
    { action: 'Thêm kho', method: 'POST', path: 'warehouses', body: { warehouse_name: 'Kho mới', address: 'Địa chỉ mới' } },
    { action: 'Sửa kho', method: 'PUT', path: 'warehouses/1', body: { warehouse_name: 'Kho mới', address: 'Địa chỉ mới' } },
    { action: 'Phân công', method: 'POST', path: 'warehouses/1/staff', body: { user_id: 22 } },
    { action: 'Gỡ nhân viên', method: 'DELETE', path: 'warehouses/1/staff/22', body: undefined },
  ]
  for (const { action, path, method, body } of calls) {
    fireEvent.click(await screen.findByRole('button', { name: action }))
    if (body?.warehouse_name !== undefined) {
      fill('Tên kho', body.warehouse_name)
      fill('Địa chỉ', body.address)
    } else fill('Mã người dùng', '22')
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Xác nhận' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(fetcher).toHaveBeenCalledWith('/api/v1/' + path, expect.objectContaining({
      method, body: body ? JSON.stringify(body) : undefined,
    }))
  }
})

