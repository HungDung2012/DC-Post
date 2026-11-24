// @vitest-environment jsdom
import { fireEvent, screen, within } from '@testing-library/react'
import { expect, it } from 'vitest'
import { list, openPage, product, warehouse } from './pageTestSupport'

const order = {
  order_id: 9, order_code: 'ORD-009', custom_id: 105, warehouse_id: 1,
  status: { status_id: 1, status_code: 'PENDING', status_name: 'Chờ xử lý' },
  total_amount: 150000, created_at: '2026-11-24T01:00:00Z', payment_method: 'COD',
}

it('uses order status totals and low-stock counts from API metadata', async () => {
  const { fetcher } = openPage('tong-quan', 'ADMIN', (url) => {
    if (url.pathname.endsWith('/orders')) {
      const totals: Record<string, number> = { PENDING: 19, SHIPPING: 7, CONFIRMED: 2, PROCESSING: 3 }
      const status = url.searchParams.get('status_code')
      return list(status ? [] : [order], url, status ? totals[status] : 120)
    }
    if (url.pathname.endsWith('/products')) return list([product], url, 32)
    if (url.pathname.endsWith('/warehouses')) return list([warehouse], url)
    return list([], url, 11)
  })
  expect(await screen.findByText('ORD-009')).toBeVisible()
  expect(within(screen.getByText('Tổng đơn hàng').closest('article')!).getByText('120')).toBeVisible()
  expect(await screen.findByText('11 mặt hàng tồn thấp')).toBeVisible()
  expect(await screen.findByText('24 chờ xử lý')).toBeVisible()
  expect(screen.getByText('7 đang giao')).toBeVisible()
  expect(fetcher).toHaveBeenCalledWith(expect.stringMatching(/\/warehouses\/1\/inventory\?.*low_stock=true/), expect.anything())
  fireEvent.click(screen.getByRole('link', { name: 'Quản lý tồn kho' }))
  expect(await screen.findByRole('tab', { name: 'Hàng tồn kho' })).toBeVisible()
})

it('shows customer orders and catalog without requesting warehouse data', async () => {
  const { fetcher } = openPage('tong-quan', 'CUSTOMER', (url) =>
    url.pathname.endsWith('/orders') ? list([order], url) : list([product], url))
  expect(await screen.findByText('ORD-009')).toBeVisible()
  expect(await screen.findByText('Hộp carton')).toBeVisible()
  expect(screen.queryByText('Tổng kho / bưu cục')).toBeNull()
  expect(fetcher.mock.calls.every(([url]) => !/warehouses|inventory/.test(String(url)))).toBe(true)
})

