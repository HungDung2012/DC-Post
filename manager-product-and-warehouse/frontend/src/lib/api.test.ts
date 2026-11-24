import { describe, expect, it, vi } from 'vitest'
import { ApiClientError, createApiClient } from './api'

const product = { product_code: 'PRD-01', product_name: 'Thùng giấy', unit_price: 100 }
const warehouse = { warehouse_name: 'Kho 1', address: 'Hà Nội' }
const stock = { warehouse_id: 1, items: [{ product_id: 2, quantity: 3 }] }
const order = { custom_id: 105, warehouse_id: 1, payment_method: 'COD', items: stock.items }
const meta = { page: 1, limit: 20, total_items: 0, total_pages: 0 }

describe('Go API client', () => {
  it('covers every registered method and path', async () => {
    const fetcher = vi.fn<typeof fetch>()
    const api = createApiClient(() => 'jwt', fetcher)
    const routes: Array<[string, string, () => Promise<unknown>, object?]> = [
      ['POST', '/products', () => api.createProduct(product), product],
      ['GET', '/products', () => api.listProducts()],
      ['GET', '/products/2', () => api.getProduct(2)],
      ['PUT', '/products/2', () => api.updateProduct(2, product), product],
      ['POST', '/warehouses', () => api.createWarehouse(warehouse), warehouse],
      ['GET', '/warehouses', () => api.listWarehouses()],
      ['PUT', '/warehouses/1', () => api.updateWarehouse(1, warehouse), warehouse],
      ['POST', '/warehouses/1/staff', () => api.assignStaff(1, { user_id: 22 }), { user_id: 22 }],
      ['DELETE', '/warehouses/1/staff/22', () => api.removeStaff(1, 22)],
      ['GET', '/warehouses/1/inventory', () => api.listWarehouseInventory(1)],
      ['POST', '/inventory/import', () => api.importStock(stock), stock],
      ['POST', '/inventory/export', () => api.exportStock(stock), stock],
      ['POST', '/inventory/adjust', () => api.adjustStock({ warehouse_id: 1, product_id: 2, actual_quantity: 0 }), { warehouse_id: 1, product_id: 2, actual_quantity: 0 }],
      ['GET', '/inventory/transactions', () => api.listInventoryTransactions()],
      ['POST', '/orders', () => api.createOrder(order), order],
      ['GET', '/orders', () => api.listOrders()],
      ['GET', '/orders/8', () => api.getOrderDetail(8)],
      ['PATCH', '/orders/8/status', () => api.updateOrderStatus(8, { new_status_code: 'CONFIRMED' }), { new_status_code: 'CONFIRMED' }],
      ['GET', '/orders/8/status-history', () => api.getOrderStatusHistory(8)],
      ['POST', '/orders/8/cancel', () => api.cancelOrder(8)],
      ['POST', '/orders/8/payment/confirm', () => api.confirmPayment(8, { transaction_code: 'TX-1' }), { transaction_code: 'TX-1' }],
      ['PATCH', '/refunds/4/status', () => api.updateRefundStatus(4, { refund_status: 'COMPLETED' }), { refund_status: 'COMPLETED' }],
      ['POST', '/shipments', () => api.createShipment({ order_id: 8, shipper_id: null }), { order_id: 8, shipper_id: null }],
      ['POST', '/shipments/3/tracking-events', () => api.scanTrackingEvent(3, { status_code: 'DELIVERED', warehouse_id: null }), { status_code: 'DELIVERED', warehouse_id: null }],
      ['GET', '/shipments/tracking/TRK%20%237', () => api.getShipmentByTrackingCode('TRK #7')],
    ]
    const paginatedPaths = ['/products', '/warehouses', '/warehouses/1/inventory', '/inventory/transactions', '/orders']
    for (const [method, path, call, body] of routes) {
      const payload = method === 'GET' && paginatedPaths.includes(path)
        ? { success: true, message: 'OK', data: [], meta }
        : { success: true, message: 'OK', data: null }
      fetcher.mockResolvedValueOnce(Response.json(payload))
      expect(await call()).toEqual(payload)
      expect(fetcher).toHaveBeenLastCalledWith('/api/v1' + path, expect.objectContaining({
        method,
        headers: expect.objectContaining({ Authorization: 'Bearer jwt' }),
        body: body === undefined ? undefined : JSON.stringify(body),
      }))
    }
  })

  it('retains pagination and sends encoded filters with the current token', async () => {
    const envelope = { success: true, message: 'OK', data: [], meta: { page: 2, limit: 20, total_items: 0, total_pages: 0 } }
    const fetcher = vi.fn(async () => Response.json(envelope))
    let token = 'first'
    const api = createApiClient(() => token, fetcher)
    expect(await api.listWarehouseInventory(1, { search: 'A&B', low_stock: false, page: 2 })).toEqual(envelope)
    expect(fetcher).toHaveBeenLastCalledWith('/api/v1/warehouses/1/inventory?search=A%26B&low_stock=false&page=2',
      expect.objectContaining({ headers: expect.objectContaining({ Authorization: 'Bearer first' }) }))
    token = 'second'
    await api.listProducts()
    expect(fetcher).toHaveBeenLastCalledWith('/api/v1/products',
      expect.objectContaining({ headers: expect.objectContaining({ Authorization: 'Bearer second' }) }))
  })

  it('reads the public health payload without forwarding a bearer token', async () => {
    const fetcher = vi.fn(async () => Response.json({ status: 'ok' }))
    const api = createApiClient(() => 'external', fetcher)
    expect(await api.health()).toEqual({ status: 'ok' })
    expect(fetcher).toHaveBeenCalledWith('/health', expect.objectContaining({ headers: { Accept: 'application/json' } }))
  })

  it('rejects a paginated success envelope with missing or malformed metadata', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ success: true, message: 'OK', data: [] }))
      .mockResolvedValueOnce(Response.json({ success: true, message: 'OK', data: [], meta: { ...meta, limit: '20' } }))
    const api = createApiClient(undefined, fetcher)
    await expect(api.listProducts()).rejects.toMatchObject({ status: 200, code: 'ERR_INVALID_RESPONSE' })
    await expect(api.listOrders()).rejects.toMatchObject({ status: 200, code: 'ERR_INVALID_RESPONSE' })
  })

  it('preserves the status, code, message and details from Go errors', async () => {
    const error = { code: 'ERR_INSUFFICIENT_STOCK', message: 'Thiếu tồn kho', details: { available: 4 } }
    const api = createApiClient(undefined, async () => Response.json({ success: false, error }, { status: 400 }))
    const failure = await api.createOrder(order).catch((caught: unknown) => caught)
    expect(failure).toBeInstanceOf(ApiClientError)
    expect(failure).toMatchObject({ ...error, status: 400 })
  })

  it('reports non-JSON proxy responses and rejected network requests', async () => {
    const proxy = createApiClient(undefined, async () => new Response('Bad Gateway', { status: 502 }))
    await expect(proxy.listProducts()).rejects.toMatchObject({ status: 502, code: 'ERR_INVALID_RESPONSE' })
    const offline = createApiClient(undefined, async () => { throw new TypeError('offline') })
    await expect(offline.health()).rejects.toMatchObject({ status: 0, code: 'ERR_NETWORK' })
  })
})
