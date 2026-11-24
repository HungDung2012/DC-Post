// @vitest-environment jsdom
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { expect, it } from 'vitest'
import { fill, list, ok, openPage, product } from './pageTestSupport'

it('allows customer search, pagination and details with Vietnamese currency', async () => {
  const { fetcher } = openPage('san-pham', 'CUSTOMER', (url) =>
    url.pathname.endsWith('/12') ? ok({ ...product, product_name: 'Chi tiết hộp carton' }) : list([product], url, 22))
  expect(await screen.findByText('Hộp carton')).toBeVisible()
  expect(screen.getByText('150.000 ₫')).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Thêm sản phẩm' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Sửa sản phẩm' })).toBeNull()
  fill('Tìm sản phẩm', 'carton')
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith(expect.stringContaining('search=carton'), expect.anything()))
  fireEvent.click(await screen.findByRole('button', { name: 'Trang sau' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith(expect.stringMatching(/page=2.*search=carton/), expect.anything()))
  fireEvent.click(await screen.findByRole('button', { name: 'Xem chi tiết' }))
  expect(await within(screen.getByRole('dialog')).findByText('Chi tiết hộp carton')).toBeVisible()
  expect(fetcher).toHaveBeenCalledWith('/api/v1/products/12', expect.objectContaining({ method: 'GET' }))
})

it.each(['STAFF', 'ADMIN'] as const)('allows %s to create and edit products', async (role) => {
  const { fetcher } = openPage('san-pham', role, (url, method) =>
    method === 'GET' ? list([product], url) : ok(product))
  for (const editing of [false, true]) {
    fireEvent.click(await screen.findByRole('button', { name: editing ? 'Sửa sản phẩm' : 'Thêm sản phẩm' }))
    if (!editing) fill('Mã sản phẩm', 'PRD-02')
    fill('Tên sản phẩm', 'Túi giấy')
    fill('Đơn giá (₫)', '12500')
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Xác nhận' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(fetcher).toHaveBeenCalledWith('/api/v1/products' + (editing ? '/12' : ''),
      expect.objectContaining({ method: editing ? 'PUT' : 'POST', body: JSON.stringify({
        ...(!editing && { product_code: 'PRD-02' }), product_name: 'Túi giấy', unit_price: 12500,
      }) }))
  }
})

