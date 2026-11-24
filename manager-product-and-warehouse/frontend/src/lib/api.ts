import type * as DTO from '../types/api'

export class ApiClientError extends Error {
  readonly code: string
  readonly details: unknown

  constructor(readonly status: number, error: DTO.ApiError['error']) {
    super(error.message)
    this.name = 'ApiClientError'
    this.code = error.code
    this.details = error.details
  }
}

function queryString(query: object): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  }
  const encoded = params.toString()
  return encoded ? '?' + encoded : ''
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

export function createApiClient(
  getToken: () => string | null = () => null,
  fetcher: typeof fetch = (input, init) => fetch(input, init),
) {
  async function request(path: string, init: RequestInit): Promise<{ response: Response; payload: unknown }> {
    let response: Response
    try {
      response = await fetcher(path, init)
    } catch (cause) {
      if (init.signal?.aborted) throw cause
      throw new ApiClientError(0, { code: 'ERR_NETWORK', message: 'Không thể kết nối đến API.' })
    }

    let payload: unknown
    try {
      payload = await response.json()
    } catch (cause) {
      if (init.signal?.aborted) throw cause
      throw invalidResponse(response.status)
    }

    if (isRecord(payload) && payload.success === false && isRecord(payload.error)) {
      const { code, message, details } = payload.error
      if (typeof code === 'string' && typeof message === 'string') {
        throw new ApiClientError(response.status, { code, message, details })
      }
    }
    if (!response.ok) throw invalidResponse(response.status)
    return { response, payload }
  }

  function invalidResponse(status: number): ApiClientError {
    return new ApiClientError(status, { code: 'ERR_INVALID_RESPONSE', message: 'Phản hồi API không hợp lệ.' })
  }

  function send<T>(method: string, path: string, body?: object): Promise<DTO.ApiSuccess<T>>
  function send<T>(method: 'GET', path: string, body: undefined, paginated: true): Promise<DTO.PaginatedSuccess<T>>
  async function send(method: string, path: string, body?: object, paginated = false): Promise<DTO.ApiSuccess<unknown>> {
    const token = getToken()
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (token) headers.Authorization = 'Bearer ' + token
    if (body !== undefined) headers['Content-Type'] = 'application/json'

    const { response, payload } = await request('/api/v1' + path, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body),
    })
    if (!isRecord(payload) || payload.success !== true || typeof payload.message !== 'string' || !('data' in payload)) {
      throw invalidResponse(response.status)
    }
    if (paginated) {
      const { data, meta } = payload
      if (!Array.isArray(data) || !isRecord(meta) ||
          ![meta.page, meta.limit, meta.total_items, meta.total_pages].every(Number.isInteger)) {
        throw invalidResponse(response.status)
      }
    }
    return payload as unknown as DTO.ApiSuccess<unknown>
  }

  function list<T>(path: string, query: object): Promise<DTO.PaginatedSuccess<T>> {
    return send<T>('GET', path + queryString(query), undefined, true)
  }

  return {
    async health(signal?: AbortSignal): Promise<DTO.HealthResponse> {
      const { response, payload } = await request('/health', { headers: { Accept: 'application/json' }, signal })
      if (isRecord(payload) && payload.status === 'ok') return { status: 'ok' }
      throw invalidResponse(response.status)
    },

    createProduct: (body: DTO.CreateProductReq) => send<DTO.Product>('POST', '/products', body),
    listProducts: (query: DTO.ProductListQuery = {}) => list<DTO.Product>('/products', query),
    getProduct: (id: number) => send<DTO.Product>('GET', `/products/${id}`),
    updateProduct: (id: number, body: DTO.UpdateProductReq) => send<DTO.Product>('PUT', `/products/${id}`, body),

    createWarehouse: (body: DTO.WarehouseReq) => send<DTO.Warehouse>('POST', '/warehouses', body),
    listWarehouses: (query: DTO.WarehouseListQuery = {}) => list<DTO.Warehouse>('/warehouses', query),
    updateWarehouse: (id: number, body: DTO.WarehouseReq) => send<DTO.Warehouse>('PUT', `/warehouses/${id}`, body),
    assignStaff: (id: number, body: DTO.AssignStaffReq) => send<null>('POST', `/warehouses/${id}/staff`, body),
    removeStaff: (id: number, userId: number) => send<null>('DELETE', `/warehouses/${id}/staff/${userId}`),
    listWarehouseInventory: (id: number, query: DTO.InventoryListQuery = {}) =>
      list<DTO.Inventory>(`/warehouses/${id}/inventory`, query),
    importStock: (body: DTO.ImportStockReq) => send<DTO.StockChangeResponse>('POST', '/inventory/import', body),
    exportStock: (body: DTO.ExportStockReq) => send<DTO.StockChangeResponse>('POST', '/inventory/export', body),
    adjustStock: (body: DTO.AdjustStockReq) => send<DTO.StockChangeResponse>('POST', '/inventory/adjust', body),
    listInventoryTransactions: (query: DTO.InventoryTxFilter = {}) =>
      list<DTO.InventoryTransaction>('/inventory/transactions', query),

    createOrder: (body: DTO.CreateOrderReq) => send<DTO.Order>('POST', '/orders', body),
    listOrders: (query: DTO.OrderListQuery = {}) => list<DTO.OrderSummary>('/orders', query),
    getOrderDetail: (id: number) => send<DTO.OrderDetailResponse>('GET', `/orders/${id}`),
    updateOrderStatus: (id: number, body: DTO.UpdateOrderStatusReq) =>
      send<DTO.OrderStatusChangeResponse>('PATCH', `/orders/${id}/status`, body),
    getOrderStatusHistory: (id: number) => send<DTO.OrderStatusHistory[]>('GET', `/orders/${id}/status-history`),
    cancelOrder: (id: number) => send<DTO.CancelOrderResponse>('POST', `/orders/${id}/cancel`),
    confirmPayment: (id: number, body: DTO.ConfirmPaymentReq) =>
      send<DTO.Payment>('POST', `/orders/${id}/payment/confirm`, body),
    updateRefundStatus: (id: number, body: DTO.UpdateRefundStatusReq) =>
      send<DTO.Refund>('PATCH', `/refunds/${id}/status`, body),

    createShipment: (body: DTO.CreateShipmentReq) => send<DTO.Shipment>('POST', '/shipments', body),
    scanTrackingEvent: (id: number, body: DTO.ScanTrackingEventReq) =>
      send<DTO.ShipmentTrackingEvent>('POST', `/shipments/${id}/tracking-events`, body),
    getShipmentByTrackingCode: (code: string) =>
      send<DTO.ShipmentTrackingResponse>('GET', '/shipments/tracking/' + encodeURIComponent(code)),
  }
}

export type ApiClient = ReturnType<typeof createApiClient>
