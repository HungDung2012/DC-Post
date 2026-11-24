import '@testing-library/jest-dom/vitest'
import { afterEach, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { App } from '../App'
import type { DevRole } from '../context/AuthContext'

afterEach(() => {
  cleanup()
  sessionStorage.clear()
  history.replaceState(null, '', '/')
  vi.unstubAllGlobals()
})

export const warehouse = { warehouse_id: 1, warehouse_name: 'Kho Hà Nội', address: '12 Lê Lợi' }
export const product = { product_id: 12, product_code: 'PRD-012', product_name: 'Hộp carton', unit_price: 150000 }

export function ok(data: unknown, message = 'Thao tác thành công') {
  return Response.json({ success: true, message, data })
}

export function list(data: unknown[], url: URL, total = data.length) {
  const page = Number(url.searchParams.get('page') ?? 1)
  const limit = Number(url.searchParams.get('limit') ?? 20)
  return Response.json({
    success: true, message: 'OK', data,
    meta: { page, limit, total_items: total, total_pages: Math.ceil(total / limit) },
  })
}

export function openPage(hash: string, role: DevRole, respond: (url: URL, method: string) => Response) {
  const claims = btoa(JSON.stringify({
    role, user_id: 105, email: 'test@dc-post.test', exp: Math.floor(Date.now() / 1000) + 300,
  })).replace(/=+$/, '')
  sessionStorage.setItem('dc-post.bearer-token', 'header.' + claims + '.signature')
  history.replaceState(null, '', '#' + hash)
  const fetcher = vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), location.origin)
    if (url.pathname === '/health') return Response.json({ status: 'ok' })
    return respond(url, init?.method ?? 'GET')
  })
  vi.stubGlobal('fetch', fetcher)
  return { ...render(<App />), fetcher }
}

export function fill(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

