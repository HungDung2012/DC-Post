const tones = {
  amber: 'bg-amber-50 text-amber-800 ring-amber-200',
  indigo: 'bg-indigo-50 text-indigo-800 ring-indigo-200',
  violet: 'bg-violet-50 text-violet-800 ring-violet-200',
  sky: 'bg-sky-50 text-sky-800 ring-sky-200',
  emerald: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  rose: 'bg-rose-50 text-rose-800 ring-rose-200',
  orange: 'bg-orange-50 text-orange-800 ring-orange-200',
  slate: 'bg-slate-100 text-slate-700 ring-slate-200',
}

const statuses: Partial<Record<string, [keyof typeof tones, string]>> = {
  PENDING: ['amber', 'Chờ xử lý'],
  CONFIRMED: ['indigo', 'Đã xác nhận'],
  PROCESSING: ['violet', 'Đang xử lý'],
  SHIPPING: ['sky', 'Đang giao'],
  DELIVERED: ['emerald', 'Đã giao'],
  CANCELLED: ['rose', 'Đã hủy'],
  RETURNED: ['orange', 'Đã hoàn hàng'],
  IMPORT: ['emerald', 'Nhập kho'],
  EXPORT: ['rose', 'Xuất kho'],
  RESERVE: ['amber', 'Giữ hàng'],
  RELEASE: ['slate', 'Giải phóng giữ chỗ'],
  ADJUST: ['sky', 'Kiểm kê'],
  RETURN: ['orange', 'Hoàn kho'],
  CREATED: ['indigo', 'Đã tạo'],
  IN_TRANSIT: ['sky', 'Đang luân chuyển'],
  OUT_FOR_DELIVERY: ['sky', 'Đang giao hàng'],
  PAID: ['emerald', 'Đã thanh toán'],
  FAILED: ['rose', 'Thanh toán thất bại'],
  REFUNDED: ['emerald', 'Đã hoàn tiền'],
  COMPLETED: ['emerald', 'Hoàn tiền thành công'],
  REJECTED: ['rose', 'Từ chối hoàn tiền'],
}

export function Badge({ status, label }: { status: string; label?: string }) {
  const style = Object.hasOwn(statuses, status) ? statuses[status] : undefined
  const [tone, name] = style ?? ['slate', status]
  return (
    <span className={tones[tone] + ' inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset'}>
      <span className="size-1.5 rounded-full bg-current" aria-hidden="true" />
      {label ?? name}
    </span>
  )
}
