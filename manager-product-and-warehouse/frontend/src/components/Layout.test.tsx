// @vitest-environment jsdom
import { webcrypto } from 'node:crypto'
import '@testing-library/jest-dom/vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { App } from '../App'
import { AuthProvider, useAuth } from '../context/AuthContext'
import { ToastProvider } from '../context/ToastContext'
import { Layout } from './Layout'
import { Badge } from './ui/Badge'

afterEach(() => {
  cleanup()
  sessionStorage.clear()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

it('renders the six navigation destinations and the live health result', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => Response.json({ status: 'ok' })))
  render(<App />)
  expect(screen.getAllByRole('link')).toHaveLength(6)
  expect(screen.getByRole('link', { name: 'Tổng quan' })).toHaveAttribute('aria-current', 'page')
  expect(await screen.findByText('API hoạt động')).toBeVisible()
})

it('shows a disconnected status when the health endpoint fails', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 503 })))
  render(<App />)
  expect(await screen.findByText('Mất kết nối API')).toBeVisible()
})

it('checks health in browsers with AbortController but without AbortSignal helpers', async () => {
  vi.stubGlobal('AbortSignal', class {})
  vi.stubGlobal('fetch', vi.fn(async () => Response.json({ status: 'ok' })))
  render(<App />)
  expect(await screen.findByText('API hoạt động')).toBeVisible()
})

it('uses Vietnamese labels and semantic colors for delivery, payment and refund statuses', () => {
  render(<><Badge status="OUT_FOR_DELIVERY" /><Badge status="PAID" /><Badge status="REJECTED" /></>)
  expect(screen.getByText('Đang giao hàng')).toHaveClass('text-sky-800')
  expect(screen.getByText('Đã thanh toán')).toHaveClass('text-emerald-800')
  expect(screen.getByText('Từ chối hoàn tiền')).toHaveClass('text-rose-800')
})

it('switches to STAFF #22 and captures custom development credentials', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => Response.json({ status: 'ok' })))
  vi.stubGlobal('crypto', webcrypto)
  vi.stubEnv('VITE_DEV_JWT_SECRET', 'test-key')
  render(<App />)
  fireEvent.click(screen.getByRole('button', { name: 'Phiên truy cập' }))
  fireEvent.click(screen.getByRole('button', { name: 'STAFF #22' }))
  expect(await screen.findByText('STAFF #22')).toBeVisible()
  fireEvent.click(screen.getByRole('button', { name: 'Phiên truy cập' }))
  fireEvent.click(screen.getByRole('button', { name: 'ID tùy chỉnh' }))
  expect(screen.getByRole('dialog')).toBeVisible()
  fireEvent.change(screen.getByLabelText('Mã người dùng'), { target: { value: '42' } })
  fireEvent.click(screen.getByRole('button', { name: 'Tạo token' }))
  expect(await screen.findByText('STAFF #42')).toBeVisible()
})

it('applies a pasted external Bearer token to subsequent API calls', async () => {
  const fetcher = vi.fn<typeof fetch>(async (path) => Response.json(path === '/health'
    ? { status: 'ok' }
    : { success: true, message: 'OK', data: [], meta: { page: 1, limit: 20, total_items: 0, total_pages: 0 } }))
  vi.stubGlobal('fetch', fetcher)
  function Probe() {
    const { api } = useAuth()
    return <button onClick={() => void api.listProducts()}>Kiểm tra token</button>
  }
  render(<ToastProvider><AuthProvider><Layout><Probe /></Layout></AuthProvider></ToastProvider>)
  fireEvent.click(screen.getByRole('button', { name: 'Phiên truy cập' }))
  fireEvent.click(screen.getByRole('button', { name: 'Dán Bearer token' }))
  fireEvent.change(screen.getByLabelText('Bearer token'), { target: { value: 'Bearer opaque-token' } })
  fireEvent.click(screen.getByRole('button', { name: 'Dùng token' }))
  fireEvent.click(screen.getByRole('button', { name: 'Kiểm tra token' }))
  expect(fetcher).toHaveBeenLastCalledWith('/api/v1/products',
    expect.objectContaining({ headers: expect.objectContaining({ Authorization: 'Bearer opaque-token' }) }))
})
