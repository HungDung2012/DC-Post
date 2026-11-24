import {
  Activity, Boxes, ChevronDown, CircleUserRound, ClipboardList, Factory, KeyRound,
  LayoutDashboard, LogOut, Menu, Package, PackageOpen, Truck,
} from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { devRoles, rolePresets, useAuth } from '../context/AuthContext'
import type { DevRole } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { createApiClient } from '../lib/api'
import { maxUserId } from '../lib/jwt'
import { Modal } from './ui/Modal'
import { Spinner } from './ui/Spinner'

const navigation = [
  { id: 'tong-quan', label: 'Tổng quan', icon: LayoutDashboard },
  { id: 'don-hang', label: 'Đơn hàng', icon: ClipboardList },
  { id: 'van-don', label: 'Vận đơn & Hành trình', icon: Truck },
  { id: 'kho', label: 'Kho & Nhân viên', icon: Factory },
  { id: 'san-pham', label: 'Sản phẩm', icon: Package },
  { id: 'ton-kho', label: 'Tồn kho & Lịch sử', icon: Boxes },
]

const healthApi = createApiClient()
const healthStates = {
  checking: { label: 'Đang kiểm tra API', tone: 'bg-slate-100 text-slate-700' },
  online: { label: 'API hoạt động', tone: 'bg-emerald-50 text-emerald-800' },
  offline: { label: 'Mất kết nối API', tone: 'bg-rose-50 text-rose-800' },
}

function HealthStatus() {
  const [status, setStatus] = useState<keyof typeof healthStates>('checking')

  useEffect(() => {
    let activeRequest: AbortController | undefined
    let disposed = false

    async function check() {
      const request = new AbortController()
      activeRequest = request
      const timeout = window.setTimeout(() => request.abort(), 5000)
      try {
        await healthApi.health(request.signal)
        if (!disposed) setStatus('online')
      } catch {
        if (!disposed) setStatus('offline')
      } finally {
        window.clearTimeout(timeout)
      }
    }
    void check()
    const poll = window.setInterval(() => void check(), 30_000)
    return () => {
      disposed = true
      activeRequest?.abort()
      window.clearInterval(poll)
    }
  }, [])

  const { label, tone } = healthStates[status]
  return (
    <span role="status" className={tone + ' inline-flex items-center gap-1.5 rounded-full px-2.5 py-1.5 text-xs font-semibold whitespace-nowrap'}>
      <Activity size={13} aria-hidden="true" />
      {label}
    </span>
  )
}

function RoleSwitcher() {
  const auth = useAuth()
  const notify = useToast()
  const menu = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const [expanded, setExpanded] = useState(false)
  const [dialog, setDialog] = useState<'custom' | 'token' | null>(null)
  const [role, setRole] = useState<DevRole>('STAFF')
  const [userId, setUserId] = useState('')
  const [token, setToken] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const sessionLabel = auth.claims
    ? `${auth.claims.role} #${auth.claims.user_id}`
    : auth.token ? 'Token ngoài' : 'Chưa đăng nhập'

  useEffect(() => {
    if (!expanded) return
    function closeOutside(event: PointerEvent) {
      if (event.target instanceof Node && !menu.current?.contains(event.target)) setExpanded(false)
    }
    document.addEventListener('pointerdown', closeOutside)
    return () => document.removeEventListener('pointerdown', closeOutside)
  }, [expanded])

  function openDialog(nextDialog: 'custom' | 'token') {
    trigger.current?.focus()
    setExpanded(false)
    setError('')
    setToken('')
    setUserId('')
    setRole(devRoles.find((option) => option === auth.claims?.role) ?? 'STAFF')
    setDialog(nextDialog)
  }

  async function selectRole(nextRole: DevRole, id: number, custom = false) {
    setExpanded(false)
    setError('')
    setPending(true)
    try {
      await auth.switchRole(nextRole, id)
      setDialog(null)
      notify({ title: 'Đã cập nhật phiên truy cập', tone: 'success' })
    } catch (failure) {
      const message = failure instanceof Error ? failure.message : 'Không thể tạo token.'
      if (custom) setError(message)
      else notify({ title: 'Không thể đổi vai trò', description: message, tone: 'error' })
    } finally {
      setPending(false)
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (dialog === 'custom') {
      void selectRole(role, Number(userId), true)
      return
    }
    try {
      auth.useExternalToken(token)
      setToken('')
      setDialog(null)
      notify({ title: 'Đã lưu Bearer token', tone: 'success' })
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Không thể dùng Bearer token.')
    }
  }

  return (
    <>
      <div
        ref={menu}
        className="relative min-w-0"
        onKeyDown={(event) => {
          if (event.key === 'Escape' && expanded) {
            setExpanded(false)
            trigger.current?.focus()
          }
        }}
      >
        <button
          ref={trigger}
          type="button"
          aria-label="Phiên truy cập"
          aria-expanded={expanded}
          disabled={pending}
          onClick={() => setExpanded(!expanded)}
          className="flex max-w-full items-center gap-2 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
        >
          {pending ? <Spinner label="Đang tạo token" /> : <CircleUserRound size={17} className="shrink-0 text-indigo-600" aria-hidden="true" />}
          <span className="min-w-0 max-w-40 truncate">{sessionLabel}</span>
          <ChevronDown size={14} className="shrink-0 text-slate-400" aria-hidden="true" />
        </button>
        {expanded && (
          <div className="absolute top-full right-0 z-30 mt-2 w-60 rounded-xl border border-slate-200 bg-white p-1.5 shadow-xl shadow-slate-900/10">
            <p className="px-3 py-2 text-xs font-semibold text-slate-500">Vai trò thử nghiệm</p>
            {rolePresets.map((preset) => (
              <button
                key={preset.role} className="menu-button" type="button" disabled={!auth.devSigningEnabled}
                onClick={() => { trigger.current?.focus(); void selectRole(preset.role, preset.user_id) }}
              >
                <CircleUserRound size={15} aria-hidden="true" /> {preset.role} #{preset.user_id}
              </button>
            ))}
            <button className="menu-button" type="button" disabled={!auth.devSigningEnabled} onClick={() => openDialog('custom')}>
              <CircleUserRound size={15} aria-hidden="true" /> ID tùy chỉnh
            </button>
            {!auth.devSigningEnabled && <p className="px-3 pb-2 text-xs text-slate-600">Thiếu VITE_DEV_JWT_SECRET.</p>}
            <div className="my-1 border-t border-slate-200" />
            <button className="menu-button" type="button" onClick={() => openDialog('token')}>
              <KeyRound size={15} aria-hidden="true" /> Dán Bearer token
            </button>
            {auth.token && (
              <button className="menu-button" type="button" onClick={() => {
                auth.clearSession()
                setExpanded(false)
                trigger.current?.focus()
              }}>
                <LogOut size={15} aria-hidden="true" /> Xóa phiên truy cập
              </button>
            )}
          </div>
        )}
      </div>
      <Modal
        title={dialog === 'custom' ? 'Vai trò thử nghiệm tùy chỉnh' : 'Dán Bearer token'}
        open={dialog !== null}
        onClose={() => { if (!pending) setDialog(null) }}
      >
        <form className="grid gap-4" onSubmit={submit}>
          {dialog === 'custom' ? (
            <>
              <label className="text-sm font-medium">Vai trò
                <select className="field" value={role} onChange={(event) => setRole(event.target.value as DevRole)}>
                  {devRoles.map((option) => <option key={option}>{option}</option>)}
                </select>
              </label>
              <label className="text-sm font-medium">Mã người dùng
                <input className="field" type="number" min={1} max={maxUserId} step={1} required
                  value={userId} onChange={(event) => setUserId(event.target.value)} />
              </label>
            </>
          ) : (
            <label className="text-sm font-medium">Bearer token
              <textarea className="field min-h-24 resize-y font-mono" required autoComplete="off" spellCheck={false}
                value={token} onChange={(event) => setToken(event.target.value)} />
            </label>
          )}
          {error && <p role="alert" className="text-sm text-rose-700">{error}</p>}
          <button className="primary-button justify-self-end" type="submit" disabled={pending}>
            {dialog === 'custom' ? 'Tạo token' : 'Dùng token'}
          </button>
        </form>
      </Modal>
    </>
  )
}

function activeNavigation() {
  return navigation.find(({ id }) => window.location.hash === '#' + id) ?? navigation[0]
}

export function Layout({ children }: { children: ReactNode }) {
  const [active, setActive] = useState(activeNavigation)
  const [mobileOpen, setMobileOpen] = useState(false)

  useEffect(() => {
    function updateLocation() {
      setActive(activeNavigation())
      setMobileOpen(false)
    }
    window.addEventListener('hashchange', updateLocation)
    return () => window.removeEventListener('hashchange', updateLocation)
  }, [])

  return (
    <div className="min-h-dvh bg-slate-50">
      <aside id="navigation-sidebar" className={
        (mobileOpen ? 'flex' : 'hidden') + ' fixed top-14 bottom-0 left-0 z-20 w-64 flex-col bg-slate-900 text-white sm:top-0 sm:flex'
      }>
        <div className="flex items-center gap-3 border-b border-white/10 px-5 py-5">
          <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-indigo-500 text-white">
            <PackageOpen size={21} aria-hidden="true" />
          </span>
          <div>
            <p className="text-lg leading-tight font-semibold tracking-tight">DC-Post</p>
            <p className="text-xs font-medium text-slate-300">OMS &amp; WMS</p>
          </div>
        </div>
        <nav aria-label="Điều hướng chính" className="grid gap-1 overflow-y-auto p-3">
          {navigation.map(({ id, label, icon: Icon }) => (
            <a
              key={id} href={'#' + id} aria-current={id === active.id ? 'page' : undefined}
              onClick={() => setMobileOpen(false)}
              className={'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium ' +
                (id === active.id ? 'bg-indigo-500/25 text-white ring-1 ring-inset ring-indigo-400/35' :
                  'text-slate-300 hover:bg-white/10 hover:text-white')}
            >
              <Icon size={17} className="shrink-0" aria-hidden="true" />
              {label}
            </a>
          ))}
        </nav>
      </aside>
      <div className="sm:pl-64">
        <header className="sticky top-0 z-20 flex h-14 items-center gap-2 border-b border-slate-200 bg-white px-3 sm:h-16 sm:px-7">
          <button
            aria-label="Điều hướng" aria-expanded={mobileOpen} aria-controls="navigation-sidebar" type="button"
            className="icon-button sm:hidden" onClick={() => setMobileOpen(!mobileOpen)}
          >
            <Menu size={20} aria-hidden="true" />
          </button>
          <span className="hidden text-sm font-medium text-slate-600 lg:inline">Quản lý Đơn hàng &amp; Kho vận</span>
          <div className="ml-auto flex min-w-0 items-center gap-2 sm:gap-3">
            <HealthStatus />
            <RoleSwitcher />
          </div>
        </header>
        <main className="mx-auto max-w-7xl px-4 py-7 sm:px-7 sm:py-9">
          <h1 className="mb-5 text-2xl font-semibold tracking-tight text-slate-900 sm:mb-7 sm:text-3xl">{active.label}</h1>
          {children}
        </main>
      </div>
    </div>
  )
}
