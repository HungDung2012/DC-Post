export interface PaginationMeta {
  page: number
  limit: number
  total_items: number
  total_pages: number
}

export interface ApiSuccess<T> {
  success: true
  message: string
  data: T
  meta?: PaginationMeta
}

export interface ApiError {
  success: false
  error: {
    code: string
    message: string
    details?: unknown
  }
}

export type PaginatedSuccess<T> = ApiSuccess<T[]> & { meta: PaginationMeta }

export interface HealthResponse {
  status: 'ok'
}

export interface PageQuery {
  page?: number
  limit?: number
}

export interface DateRangeQuery {
  from_date?: string
  to_date?: string
}

export interface CreateProductReq extends UpdateProductReq {
  product_code: string
}

export interface UpdateProductReq {
  product_name: string
  unit_price: number
}

export interface ProductListQuery extends PageQuery {
  search?: string
}

export interface ProductSummary {
  product_id: number
  product_code: string
  product_name: string
}

export interface Product extends ProductSummary {
  unit_price: number
}

export interface WarehouseReq {
  warehouse_name: string
  address: string
}

export type WarehouseListQuery = PageQuery

export interface AssignStaffReq {
  user_id: number
}

export interface WarehouseSummary {
  warehouse_id: number
  warehouse_name: string
}

export interface Warehouse extends WarehouseSummary {
  address: string
}

export interface InventoryListQuery extends PageQuery {
  search?: string
  low_stock?: boolean
}

export interface Inventory extends WarehouseSummary {
  inventory_id: number
  product: Product
  quantity: number
  reserved_quantity: number
  available_quantity: number
}

export interface StockItem {
  product_id: number
  quantity: number
}

export interface ImportStockReq {
  warehouse_id: number
  items: StockItem[]
}

export type ExportStockReq = ImportStockReq

export interface AdjustStockReq {
  warehouse_id: number
  product_id: number
  actual_quantity: number
}

export interface StockChange {
  transaction_id: number
  product_id: number
  transaction_type: string
  quantity: number
  new_stock_quantity: number
  created_at: string
}

export interface StockChangeResponse {
  warehouse_id: number
  performed_by: number
  transactions: StockChange[]
}

export interface InventoryTxFilter extends PageQuery, DateRangeQuery {
  warehouse_id?: number
  product_id?: number
  shipment_id?: number
  performed_by?: number
  transaction_type?: string
}

export interface InventoryTransaction {
  transaction_id: number
  warehouse: WarehouseSummary
  product: ProductSummary
  shipment_id: number | null
  performed_by: number
  transaction_type: string
  quantity: number
  created_at: string
}

export interface CreateOrderReq {
  custom_id?: number
  warehouse_id: number
  payment_method: string
  items: OrderItemReq[]
}

export interface OrderItemReq {
  product_id: number
  quantity: number
}

export interface OrderListQuery extends PageQuery, DateRangeQuery {
  status_code?: string
  order_code?: string
  custom_id?: number
}

export interface UpdateOrderStatusReq {
  new_status_code: string
}

export interface ConfirmPaymentReq {
  transaction_code: string
}

export interface UpdateRefundStatusReq {
  refund_status: string
}

export interface StatusResponse {
  status_id: number
  status_code: string
  status_name: string
}

export interface OrderSummary {
  order_id: number
  order_code: string
  custom_id: number
  warehouse_id: number
  status: StatusResponse
  total_amount: number
  payment_method: string
  created_at: string
}

export interface Order extends OrderSummary {
  items: OrderItem[]
  payment: Payment
}

export interface OrderDetailResponse extends Order {
  refunds: Refund[]
  shipments: Shipment[]
  status_history: OrderStatusHistory[]
}

export interface OrderItem {
  order_item_id: number
  product_id: number
  item_name: string
  quantity: number
  unit_price: number
}

export interface Payment {
  payment_id: number
  amount: number
  payment_method: string
  payment_status: string
  transaction_code: string | null
  paid_at: string | null
}

export interface Refund {
  refund_id: number
  payment_id: number
  refund_amount: number
  refund_status: string
  created_at: string
}

export interface OrderStatusHistory {
  history_id: number
  order_id: number
  old_status: StatusResponse | null
  new_status: StatusResponse
  changed_by: number
  change_at: string
}

export interface OrderStatusChangeResponse {
  order_id: number
  order_code: string
  old_status_code: string
  new_status_code: string
  changed_by: number
  change_at: string
}

export interface CancelOrderResponse {
  order_id: number
  status_code: string
  stock_released: boolean
  refund: Refund | null
}

export interface CreateShipmentReq {
  order_id: number
  shipper_id?: number | null
}

export interface ScanTrackingEventReq {
  status_code: string
  from_warehouse_id?: number | null
  warehouse_id?: number | null
}

export interface Shipment {
  shipment_id: number
  order_id: number
  tracking_code: string
  shipper_id: number | null
  status: StatusResponse
  current_warehouse_id: number | null
  cod_amount: number
  delivered_at: string | null
}

export interface ShipmentTrackingResponse {
  shipment_id: number
  tracking_code: string
  order_id: number
  current_status: string
  current_warehouse: WarehouseSummary | null
  delivered_at: string | null
  events: ShipmentTrackingEvent[]
}

export interface ShipmentTrackingEvent {
  event_id: number
  status_code: string
  status_name: string
  from_warehouse_id: number | null
  warehouse_id: number | null
  scanned_by: number
  scanned_at: string
}

export type ProductResponse = Product
export type WarehouseResponse = Warehouse
export type InventoryResponse = Inventory
export type InventoryTransactionResponse = InventoryTransaction
export type OrderResponse = Order
export type OrderItemResponse = OrderItem
export type OrderStatusHistoryResponse = OrderStatusHistory
export type PaymentResponse = Payment
export type RefundResponse = Refund
export type ShipmentResponse = Shipment
export type TrackingEventResponse = ShipmentTrackingEvent
