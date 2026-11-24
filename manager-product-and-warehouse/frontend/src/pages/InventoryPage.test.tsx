// @vitest-environment jsdom
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { expect, it } from 'vitest'
import { fill, list, ok, openPage, product, warehouse } from './pageTestSupport'

function inventoryResponse(url: URL) {
  if (url.pathname.endsWith('/warehouses')) return list([warehouse], url)
  if (url.pathname.endsWith('/transactions')) return list([{
    transaction_id: 5005, warehouse, product, shipment_id: 701, performed_by: 22,
    transaction_type: 'EXPORT', quantity: -2, created_at: '2026-11-24T01:30:00Z',
  }], url)
  const stock = { ...warehouse, inventory_id: 1, product, quantity: 15, reserved_quantity: 5, available_quantity: 10 }
  return list([stock, { ...stock, inventory_id: 2, quantity: 5, available_quantity: 0,
    product: { ...product, product_id: 13, product_name: 'Túi giấy' } }], url)
}

it('shows stock quantities and low-stock severity, and requests stock and history filters', async () => {
  const { fetcher } = openPage('ton-kho', 'ADMIN', inventoryResponse)
  const row = (await screen.findByText('Hộp carton')).closest('tr')!
  expect(within(row).getByText('15')).toBeVisible()
  expect(within(row).getByText('5')).toBeVisible()
  expect(within(row).getByText('10')).toHaveClass('text-amber-800')
  expect(screen.getByText('0')).toHaveClass('text-rose-800')
  fireEvent.click(screen.getByRole('checkbox', { name: 'Chỉ hiện tồn thấp' }))
  fill('Tìm hàng tồn kho', 'carton')
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith(
    expect.stringMatching(/\/warehouses\/1\/inventory\?.*search=carton.*low_stock=true/), expect.anything()))
  fireEvent.click(screen.getByRole('tab', { name: 'Lịch sử ra/vào kho' }))
  fill('Kho giao dịch', '1')
  fill('Mã sản phẩm', '12')
  fill('Mã vận đơn', '701')
  fill('Loại giao dịch', 'EXPORT')
  fill('Từ ngày (UTC)', '2026-11-01')
  fill('Đến ngày (UTC)', '2026-11-24')
  fireEvent.click(screen.getByRole('button', { name: 'Áp dụng' }))
  await waitFor(() => expect(fetcher.mock.calls.some(([input]) => {
    const url = new URL(String(input), location.origin)
    const expected = { warehouse_id: '1', product_id: '12', shipment_id: '701', transaction_type: 'EXPORT', from_date: '2026-11-01', to_date: '2026-11-24' }
    return url.pathname.endsWith('/transactions') && Object.entries(expected).every(([key, value]) => url.searchParams.get(key) === value)
  })).toBe(true))
  expect(await screen.findByText('−2')).toBeVisible()
})

it('shows history dates on the same UTC day used by transaction filters', async () => {
  openPage('ton-kho', 'ADMIN', (url) => url.pathname.endsWith('/transactions')
    ? list([{
      transaction_id: 9, warehouse, product, shipment_id: null, performed_by: 22,
      transaction_type: 'IMPORT', quantity: 2, created_at: '2026-11-23T20:00:00Z',
    }], url)
    : inventoryResponse(url))
  fireEvent.click(screen.getByRole('tab', { name: 'Lịch sử ra/vào kho' }))
  expect(await screen.findByRole('columnheader', { name: 'Thời gian (UTC)' })).toBeVisible()
  expect(screen.getByText(/23\/11\/26.*20:00|20:00.*23\/11\/26/)).toBeVisible()
})

const cases = [
  { action: 'Nhập kho', endpoint: 'import', payload: { warehouse_id: 1, items: [{ product_id: 12, quantity: 5 }, { product_id: 13, quantity: 3 }] } },
  { action: 'Xuất kho nội bộ', endpoint: 'export', payload: { warehouse_id: 1, items: [{ product_id: 12, quantity: 5 }] } },
  { action: 'Kiểm kê / Điều chỉnh', endpoint: 'adjust', payload: { warehouse_id: 1, product_id: 12, actual_quantity: 0 } },
]

function submitStock(action: string) {
  fireEvent.click(screen.getByRole('button', { name: action }))
  fill('Mã sản phẩm 1', '12')
  fill(action === 'Kiểm kê / Điều chỉnh' ? 'Số lượng thực tế' : 'Số lượng 1', action === 'Kiểm kê / Điều chỉnh' ? '0' : '5')
  if (action === 'Nhập kho') {
    fireEvent.click(screen.getByRole('button', { name: 'Thêm dòng sản phẩm' }))
    fill('Mã sản phẩm 2', '13')
    fill('Số lượng 2', '3')
  }
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Xác nhận' }))
}

it.each(cases)('posts $action and reloads inventory', async ({ action, endpoint, payload }) => {
  const { fetcher } = openPage('ton-kho', 'ADMIN', (url, method) =>
    method === 'POST' ? ok(null, 'Đã lưu thay đổi tồn kho') : inventoryResponse(url))
  await screen.findByText('Hộp carton')
  submitStock(action)
  expect(await screen.findByText('Đã lưu thay đổi tồn kho')).toBeVisible()
  expect(fetcher).toHaveBeenCalledWith('/api/v1/inventory/' + endpoint,
    expect.objectContaining({ method: 'POST', body: JSON.stringify(payload) }))
  await waitFor(() => expect(fetcher.mock.calls.filter(([url]) => String(url).includes('/warehouses/1/inventory'))).toHaveLength(2))
})

it.each([
  ['Xuất kho nội bộ', 'ERR_INSUFFICIENT_STOCK', 400],
  ['Kiểm kê / Điều chỉnh', 'ERR_BELOW_RESERVED_STOCK', 422],
  ['Nhập kho', 'ERR_WAREHOUSE_ACCESS_DENIED', 403],
] as const)('shows a toast and preserves the form on %s failure', async (action, code, status) => {
  openPage('ton-kho', 'ADMIN', (url, method) => method === 'POST'
    ? Response.json({ success: false, error: { code, message: 'Không thể cập nhật kho' } }, { status })
    : inventoryResponse(url))
  await screen.findByText('Hộp carton')
  submitStock(action)
  expect(await screen.findByText(code)).toBeVisible()
  expect(screen.getAllByText('Không thể cập nhật kho').length).toBeGreaterThan(0)
  expect(screen.getByRole('dialog')).toBeVisible()
  expect(screen.getByLabelText('Mã sản phẩm 1')).toHaveValue(12)
})

it('blocks customer inventory requests and stock actions', () => {
  const { fetcher } = openPage('ton-kho', 'CUSTOMER', inventoryResponse)
  expect(screen.getByText('Bạn không có quyền truy cập tồn kho')).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Nhập kho' })).toBeNull()
  expect(fetcher.mock.calls.every(([url]) => url === '/health')).toBe(true)
})

