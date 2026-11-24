import { LoaderCircle } from 'lucide-react'

export function Spinner({ label = 'Đang tải', className = '' }: { label?: string; className?: string }) {
  return (
    <span role="status" className={'inline-flex items-center gap-2 text-indigo-700 ' + className}>
      <LoaderCircle size={18} className="motion-safe:animate-spin" aria-hidden="true" />
      <span className="sr-only">{label}</span>
    </span>
  )
}
