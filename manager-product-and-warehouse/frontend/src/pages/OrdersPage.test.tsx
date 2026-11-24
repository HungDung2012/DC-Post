// @vitest-environment jsdom
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { DevRole } from '../context/AuthContext'
import type { OrderDetailResponse } from '../types/api'
import { fill, list, ok, openPage, product, warehouse } from './pageTestSupport'

const date = '2026-11-24T01:00:00Z'
const pending = { status_id: 1, status_code: 'PENDING', status_name: 'Chờ xác nhận' }
const order: OrderDetailResponse = {
  order_id: 501, order_code: 'ORD-0501', custom_id: 105, warehouse_id: 1,
  status: pending, total_amount: 300000, payment_method: 'COD', created_at: date,
  items: [{ order_item_id: 901, product_id: 12, item_name: 'Hộp carton', quantity: 2, unit_price: 150000 }],
  payment: { payment_id: 301, amount: 300000, payment_method: 'COD', payment_status: 'PENDING', transaction_code: null, paid_at: null },
  refunds: [], shipments: [], status_history: [],
}
const secondProduct = { product_id: 15, product_code: 'PRD-015', product_name: 'Túi chống sốc', unit_price: 50000 }

function setup(role: DevRole, {
  paid = false, failCreation = false, shrinkOnCancel = false, refundStatus = 'COMPLETED',
} = {}) {
  let detail = { ...order, payment: { ...order.payment, payment_status: paid ? 'PAID' : 'PENDING' } }
  return openPage('don-hang', role, (url, method) => {
    const path = url.pathname.replace('/api/v1', '')
    if (path === '/warehouses') return list([warehouse], url)
    if (path === '/products') return list([product, secondProduct], url)
    if (path === '/orders' && method === 'GET') {
      if (shrinkOnCancel && detail.status.status_code === 'CANCELLED') {
        const rows = url.searchParams.get('page') === '1' ? [order] : []
        return list(rows, url, 20)
      }
      return list([detail], url, 40)
    }
    if (path === '/orders' && method === 'POST') {
      if (failCreation) return Response.json({ success: false, error: {
        code: 'ERR_INSUFFICIENT_STOCK', message: 'Tồn kho khả dụng không đủ.',
      } }, { status: 400 })
      return ok(detail, 'Tạo đơn hàng thành công')
    }
    if (path === '/orders/501/status-history') return ok([{
      history_id: 1, order_id: 501, old_status: null, new_status: detail.status, changed_by: 105, change_at: date,
    }])
    if (path === '/orders/501/status') {
      const next = detail.status.status_code === 'PENDING'
        ? { status_id: 2, status_code: 'CONFIRMED', status_name: 'Đã xác nhận' }
        : { status_id: 3, status_code: 'PROCESSING', status_name: 'Đang đóng gói' }
      detail = { ...detail, status: next }
      return ok({ order_id: 501, order_code: 'ORD-0501', old_status_code: next.status_id === 2 ? 'PENDING' : 'CONFIRMED',
        new_status_code: next.status_code, changed_by: 105, change_at: date })
    }
    if (path === '/orders/501/payment/confirm') {
      detail = { ...detail, payment: { ...detail.payment, payment_status: 'PAID', transaction_code: 'BANK-01', paid_at: date } }
      return ok(detail.payment)
    }
    if (path === '/orders/501/cancel') {
      const refund = { refund_id: 10, payment_id: 301, refund_amount: 300000, refund_status: 'PENDING', created_at: date }
      detail = { ...detail, status: { status_id: 6, status_code: 'CANCELLED', status_name: 'Đã hủy' },
        refunds: paid ? [refund] : [] }
      return ok({ order_id: 501, status_code: 'CANCELLED', stock_released: true, refund: paid ? refund : null })
    }
    if (path === '/refunds/10/status') {
      const result = { ...detail.refunds[0], refund_status: refundStatus }
      detail = { ...detail, refunds: [result], payment: {
        ...detail.payment, payment_status: refundStatus === 'COMPLETED' ? 'REFUNDED' : 'PAID',
      } }
      return ok(result)
    }
    if (path === '/orders/501') return ok(detail)
    throw new Error('Unexpected request ' + method + ' ' + path)
  })
}

function click(name: string) {
  fireEvent.click(screen.getByRole('button', { name }))
}

async function openDetail() {
  fireEvent.click(await screen.findByRole('button', { name: 'ORD-0501' }))
  return screen.findByRole('dialog', { name: /ORD-0501/ })
}

describe('OrdersPage', () => {
  it('creates a customer order with multiple products and previews its total', async () => {
    const { fetcher } = setup('CUSTOMER')
    click('Tạo đơn hàng')
    await screen.findByRole('option', { name: /Hộp carton/ })
    fill('ID kho', '1')
    fill('Sản phẩm 1', '12')
    fill('Số lượng 1', '2')
    click('Thêm dòng sản phẩm')
    fill('Sản phẩm 2', '15')
    fill('Số lượng 2', '3')
    fill('Phương thức thanh toán', 'E_WALLET')
    expect(within(screen.getByRole('dialog')).getByText(/450[.\s]000/)).toBeVisible()
    click('Xác nhận')
    expect(await screen.findByText('Tạo đơn hàng thành công')).toBeVisible()
    expect(fetcher).toHaveBeenCalledWith('/api/v1/orders', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ warehouse_id: 1, payment_method: 'E_WALLET',
        items: [{ product_id: 12, quantity: 2 }, { product_id: 15, quantity: 3 }] }),
    }))
    expect(fetcher.mock.calls.some(([url]) => String(url).includes('/warehouses'))).toBe(false)
  })

  it('preserves applied filters when paging through staff orders', async () => {
    const { fetcher } = setup('STAFF')
    fill('Mã đơn hàng', 'ORD')
    fill('Trạng thái đơn hàng', 'PENDING')
    fill('ID khách hàng', '105')
    fill('Từ ngày', '2026-11-01')
    fill('Đến ngày', '2026-11-24')
    click('Áp dụng')
    fireEvent.click(await screen.findByRole('button', { name: 'Trang sau' }))
    await waitFor(() => {
      const queries = fetcher.mock.calls.map(([url]) => new URL(String(url), location.origin).searchParams)
      expect(queries.some((query) => query.get('page') === '2' && query.get('status_code') === 'PENDING' &&
        query.get('order_code') === 'ORD' && query.get('custom_id') === '105' &&
        query.get('from_date') === '2026-11-01' && query.get('to_date') === '2026-11-24')).toBe(true)
    })
  })

  it('advances pending orders through confirmation and packing with history', async () => {
    const { fetcher } = setup('STAFF')
    const drawer = within(await openDetail())
    fireEvent.click(await drawer.findByRole('button', { name: 'Xác nhận đơn' }))
    click('Xác nhận')
    fireEvent.click(await drawer.findByRole('button', { name: 'Đóng gói' }))
    click('Xác nhận')
    await waitFor(() => expect(drawer.getByRole('list', { name: 'Lịch sử trạng thái' })).toHaveTextContent('Đang đóng gói'))
    for (const status of ['CONFIRMED', 'PROCESSING']) {
      expect(fetcher).toHaveBeenCalledWith('/api/v1/orders/501/status', expect.objectContaining({
        method: 'PATCH', body: JSON.stringify({ new_status_code: status }),
      }))
    }
    expect(drawer.queryByRole('button', { name: 'Hủy đơn hàng' })).toBeNull()
  })

  it('confirms a pending payment with the entered transaction code', async () => {
    const { fetcher } = setup('ADMIN')
    const drawer = within(await openDetail())
    fireEvent.click(await drawer.findByRole('button', { name: 'Xác nhận thanh toán' }))
    fill('Mã giao dịch', 'BANK-01')
    click('Xác nhận')
    expect(await drawer.findByText('BANK-01')).toBeVisible()
    expect(fetcher).toHaveBeenCalledWith('/api/v1/orders/501/payment/confirm', expect.objectContaining({
      method: 'POST', body: '{"transaction_code":"BANK-01"}',
    }))
  })

  it('returns to the last available page when cancellation removes the last matching row', async () => {
    setup('STAFF', { shrinkOnCancel: true })
    fill('Trạng thái đơn hàng', 'PENDING')
    click('Áp dụng')
    fireEvent.click(await screen.findByRole('button', { name: 'Trang sau' }))
    const drawer = within(await openDetail())
    fireEvent.click(await drawer.findByRole('button', { name: 'Hủy đơn hàng' }))
    click('Xác nhận')
    await waitFor(() => expect(drawer.getByRole('list', { name: 'Lịch sử trạng thái' })).toHaveTextContent('Đã hủy'))
    expect(drawer.queryByRole('button', { name: 'Xác nhận thanh toán' })).toBeNull()
    await waitFor(() => expect(screen.getByText('Trang 1 / 1')).toBeVisible())
  })

  it('shows customers the tracking code for their dispatched order', async () => {
    const dispatched = { ...order, shipments: [{
      shipment_id: 701, order_id: 501, tracking_code: 'TRK-0701', shipper_id: null,
      status: { status_id: 1, status_code: 'CREATED', status_name: 'Đã xuất kho' },
      current_warehouse_id: 1, cod_amount: 300000, delivered_at: null,
    }] }
    openPage('don-hang', 'CUSTOMER', (url) => {
      if (url.pathname.endsWith('/status-history')) return ok([])
      if (url.pathname.endsWith('/501')) return ok(dispatched)
      return list([dispatched], url)
    })
    const drawer = within(await openDetail())
    expect(await drawer.findByText('TRK-0701')).toBeVisible()
  })

  it('shows the order list API failure in a toast', async () => {
    openPage('don-hang', 'STAFF', () => Response.json({ success: false, error: {
      code: 'ERR_INTERNAL_SERVER', message: 'Không thể tải danh sách đơn.',
    } }, { status: 500 }))
    const toast = await screen.findByRole('button', { name: 'Đóng thông báo' })
    expect(within(toast.parentElement!).getByRole('alert')).toHaveTextContent('ERR_INTERNAL_SERVER')
  })

  it.each([
    ['Duyệt và hoàn tiền', 'COMPLETED', 'Hoàn tiền thành công'],
    ['Từ chối hoàn tiền', 'REJECTED', 'Từ chối hoàn tiền'],
  ])(
    'warns before canceling paid orders and supports refund action %s', async (action, status, badge) => {
      const { fetcher } = setup('STAFF', { paid: true, refundStatus: status })
      const drawer = within(await openDetail())
      fireEvent.click(await drawer.findByRole('button', { name: 'Hủy đơn hàng' }))
      expect(screen.getByText(/tự động tạo yêu cầu hoàn tiền/i)).toBeVisible()
      expect(fetcher).not.toHaveBeenCalledWith('/api/v1/orders/501/cancel', expect.anything())
      click('Xác nhận')
      fireEvent.click(await drawer.findByRole('button', { name: action }))
      click('Xác nhận')
      await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/v1/refunds/10/status', expect.objectContaining({
        method: 'PATCH', body: JSON.stringify({ refund_status: status }),
      })))
      await waitFor(() => {
        expect(drawer.queryByRole('button', { name: action })).toBeNull()
        expect(drawer.getByText(badge)).toBeVisible()
      })
    },
  )

  it('shows the API error code and message in a toast without closing the order form', async () => {
    const { fetcher } = setup('ADMIN', { failCreation: true })
    click('Tạo đơn hàng')
    await screen.findByRole('option', { name: /Kho Hà Nội/ })
    await screen.findByRole('option', { name: /Hộp carton/ })
    fill('Kho xuất hàng', '1')
    const dialog = screen.getByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText('ID khách hàng'), { target: { value: '212' } })
    fill('Sản phẩm 1', '12')
    click('Xác nhận')
    expect(await screen.findByText('ERR_INSUFFICIENT_STOCK')).toBeVisible()
    expect(screen.getByText('Tồn kho khả dụng không đủ.')).toBeVisible()
    expect(dialog).toBeVisible()
    expect(fetcher).toHaveBeenCalledWith('/api/v1/orders', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ warehouse_id: 1, payment_method: 'COD',
        items: [{ product_id: 12, quantity: 1 }], custom_id: 212 }),
    }))
  })
})
