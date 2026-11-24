import { CheckCircle2, CircleAlert, Info, X } from 'lucide-react'
import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'

type ToastTone = 'success' | 'error' | 'info'

export interface ToastInput {
  title: string
  description?: string
  tone?: ToastTone
}

type Toast = ToastInput & { id: number }

const ToastContext = createContext<((toast: ToastInput) => void) | null>(null)
const toneStyles = {
  success: { icon: CheckCircle2, color: 'bg-emerald-50 text-emerald-700' },
  error: { icon: CircleAlert, color: 'bg-rose-50 text-rose-700' },
  info: { icon: Info, color: 'bg-indigo-50 text-indigo-700' },
}

function ToastItem({ toast, dismiss }: { toast: Toast; dismiss: (id: number) => void }) {
  const { id, title, description, tone = 'info' } = toast
  const { icon: Icon, color } = toneStyles[tone]

  useEffect(() => {
    if (tone === 'error') return
    const timer = window.setTimeout(() => dismiss(id), 5000)
    return () => window.clearTimeout(timer)
  }, [dismiss, id, tone])

  return (
    <div className="pointer-events-auto flex items-start gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-lg shadow-slate-900/10">
      <span className={color + ' grid size-9 shrink-0 place-items-center rounded-lg'}>
        <Icon size={18} aria-hidden="true" />
      </span>
      <div role={tone === 'error' ? 'alert' : 'status'} className="min-w-0 flex-1 break-words">
        <p className="text-sm font-semibold text-slate-900">{title}</p>
        {description && <p className="mt-1 text-sm text-slate-600">{description}</p>}
      </div>
      <button className="icon-button" type="button" aria-label="Đóng thông báo" onClick={() => dismiss(id)}>
        <X size={16} aria-hidden="true" />
      </button>
    </div>
  )
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const nextId = useRef(0)
  const [toasts, setToasts] = useState<Toast[]>([])
  const notify = useCallback((toast: ToastInput) => {
    const id = nextId.current++
    setToasts((items) => [...items.slice(-3), { ...toast, id }])
  }, [])
  const dismiss = useCallback((id: number) => {
    setToasts((items) => items.filter((item) => item.id !== id))
  }, [])

  return (
    <ToastContext.Provider value={notify}>
      {children}
      <div className="pointer-events-none fixed right-4 bottom-4 z-50 grid w-[min(25rem,calc(100%-2rem))] gap-2">
        {toasts.map((toast) => <ToastItem key={toast.id} toast={toast} dismiss={dismiss} />)}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const notify = useContext(ToastContext)
  if (!notify) throw new Error('useToast requires ToastProvider.')
  return notify
}
