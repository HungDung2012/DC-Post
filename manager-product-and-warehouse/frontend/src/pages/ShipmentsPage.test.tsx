// @vitest-environment jsdom
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { DevRole } from '../context/AuthContext'
import type { Shipment, ShipmentTrackingEvent } from '../types/api'
import { fill, list, ok, openPage, warehouse } from './pageTestSupport'

const trackingCode = 'TRK-0701'
const date = '2026-11-24T05:30:00Z'
const status = { status_id: 1, status_code: 'CREATED', status_name: 'Đã xuất kho' }
const shipment: Shipment = { shipment_id: 701, order_id: 501, tracking_code: trackingCode,
  shipper_id: 45, status, current_warehouse_id: 1, delivered_at: null, cod_amount: 300000 }
const initial: ShipmentTrackingEvent = { event_id: 1, status_code: 'CREATED', status_name: 'Đã xuất kho',
  from_warehouse_id: null, warehouse_id: 1, scanned_by: 22, scanned_at: date }
const transit: ShipmentTrackingEvent = { event_id: 2, status_code: 'IN_TRANSIT', status_name: 'Đến kho trung chuyển',
  from_warehouse_id: 1, warehouse_id: 2, scanned_by: 22, scanned_at: date }

function setup(role: DevRole, receivingWarehouseOnly = false) {
  let events = [initial]
  const destination = { warehouse_id: 2, warehouse_name: 'Kho Đà Nẵng', address: 'Đà Nẵng' }
  return openPage('van-don', role, (url, method) => {
    const path = url.pathname.replace('/api/v1', '')
    if (path === '/warehouses') return list([warehouse, destination], url)
    if (path === '/orders') {
      const code = url.searchParams.get('status_code')
      const orderId = code === 'PROCESSING' ? 502 : 501
      return list([{ order_id: orderId, order_code: 'ORD-0' + orderId, custom_id: 105, warehouse_id: 1,
        status: { status_id: 2, status_code: code, status_name: 'Sẵn sàng' },
        total_amount: 300000, payment_method: 'COD', created_at: date }], url)
    }
    if (path === '/shipments' && method === 'POST') return ok(shipment)
    if (path === '/shipments/701/tracking-events' && method === 'POST') {
      events = [transit, ...events]
      return ok(transit)
    }
    if (path === '/shipments/tracking/' + trackingCode && receivingWarehouseOnly && events.length === 1) {
      return Response.json({ success: false, error: {
        code: 'ERR_WAREHOUSE_ACCESS_DENIED', message: 'Vận đơn chưa đến kho được phân công.',
      } }, { status: 403 })
    }
    if (path === '/shipments/tracking/' + trackingCode) return ok({
      shipment_id: 701, tracking_code: trackingCode, order_id: 501, current_status: events[0].status_code,
      current_warehouse: events.length > 1 ? destination : warehouse, delivered_at: null, events,
    })
    return Response.json({ success: false, error: {
      code: 'ERR_RESOURCE_NOT_FOUND', message: 'Không tìm thấy vận đơn.',
    } }, { status: 404 })
  })
}

function click(name: string) {
  fireEvent.click(screen.getByRole('button', { name }))
}

function lookup(code = trackingCode) {
  fill('Mã vận đơn', code)
  click('Tra cứu')
}

describe('ShipmentsPage', () => {
  it('dispatches one eligible order and opens its new tracking code in one click', async () => {
    const { fetcher } = setup('STAFF')
    click('Tạo vận đơn và xuất kho')
    await screen.findByRole('option', { name: /ORD-0501/ })
    expect(screen.getByRole('option', { name: /ORD-0502/ })).toBeVisible()
    fill('Đơn hàng', '501')
    fill('ID người giao hàng', '45')
    click('Xác nhận')
    fireEvent.click(await screen.findByRole('button', { name: 'Xem hành trình' }))
    expect(await screen.findByRole('list', { name: 'Lịch sử giao hàng' })).toHaveTextContent('Đã xuất kho')
    expect(fetcher).toHaveBeenCalledWith('/api/v1/shipments', expect.objectContaining({
      method: 'POST', body: '{"order_id":501,"shipper_id":45}',
    }))
  })

  it('lets customers look up a shipment with a timeline and hides staff actions', async () => {
    const { fetcher } = setup('CUSTOMER')
    lookup('  ' + trackingCode + '  ')
    expect(await screen.findByRole('heading', { name: trackingCode })).toBeVisible()
    expect(screen.getByRole('list', { name: 'Lịch sử giao hàng' })).toHaveTextContent('Đã xuất kho')
    expect(screen.queryByRole('button', { name: 'Tạo vận đơn và xuất kho' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Quét mã cập nhật hành trình' })).toBeNull()
    expect(fetcher.mock.calls.some(([url]) => String(url).includes('/warehouses'))).toBe(false)
  })

  it('allows receiving staff to scan a shipment they cannot yet look up', async () => {
    const { fetcher } = setup('STAFF', true)
    lookup()
    fireEvent.click(await screen.findByRole('button', { name: 'Quét mã cập nhật hành trình' }))
    await within(screen.getByLabelText('Kho đến')).findByRole('option', { name: /Kho Đà Nẵng/ })
    fill('ID vận đơn', '701')
    fill('Kho đến', '2')
    click('Xác nhận')
    expect(await screen.findByRole('list', { name: 'Lịch sử giao hàng' })).toHaveTextContent('Đến kho trung chuyển')
    expect(fetcher).toHaveBeenCalledWith('/api/v1/shipments/701/tracking-events', expect.objectContaining({
      method: 'POST', body: '{"status_code":"IN_TRANSIT","warehouse_id":2}',
    }))
  })

  it('posts the scan warehouses and immediately reloads the timeline', async () => {
    const { fetcher } = setup('ADMIN')
    lookup()
    await screen.findByRole('heading', { name: trackingCode })
    click('Quét mã cập nhật hành trình')
    await within(screen.getByLabelText('Kho đến')).findByRole('option', { name: /Kho Đà Nẵng/ })
    fill('Trạng thái hành trình', 'IN_TRANSIT')
    fill('Kho đi', '1')
    fill('Kho đến', '2')
    click('Xác nhận')
    await waitFor(() => expect(within(screen.getByRole('list', { name: 'Lịch sử giao hàng' })).getByText('Đến kho trung chuyển')).toBeVisible())
    expect(fetcher).toHaveBeenCalledWith('/api/v1/shipments/701/tracking-events', expect.objectContaining({
      method: 'POST', body: '{"status_code":"IN_TRANSIT","from_warehouse_id":1,"warehouse_id":2}',
    }))
    expect(fetcher.mock.calls.filter(([url]) => url === '/api/v1/shipments/tracking/' + trackingCode)).toHaveLength(2)
  })

  it('shows the API error code and message in a toast for a failed lookup', async () => {
    setup('CUSTOMER')
    lookup('UNKNOWN')
    const toast = await screen.findByRole('button', { name: 'Đóng thông báo' })
    expect(within(toast.parentElement!).getByRole('alert')).toHaveTextContent('ERR_RESOURCE_NOT_FOUND')
    expect(within(toast.parentElement!).getByRole('alert')).toHaveTextContent('Không tìm thấy vận đơn.')
  })
})
