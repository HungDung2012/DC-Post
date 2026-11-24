import { createHmac } from 'node:crypto'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { decodeJwt, normalizeBearerToken, signDevJwt, signJwt } from './jwt'

const claims = { user_id: 22, email: 'nhân.viên@dc-post.test', role: 'STAFF', iat: 1_800_000_000, exp: 1_800_003_600 }

afterEach(() => {
  vi.unstubAllEnvs()
  vi.useRealTimers()
})

describe('HS256 JWT', () => {
  it('signs the exact Go actor claims with unpadded base64url and the UTF-8 key', async () => {
    const token = await signJwt(claims, 'khóa thử nghiệm')
    const [header, payload, signature] = token.split('.')
    expect(token).toMatch(/^[\w-]+\.[\w-]+\.[\w-]+$/)
    expect(JSON.parse(Buffer.from(header, 'base64url').toString())).toEqual({ alg: 'HS256', typ: 'JWT' })
    expect(signature).toBe(createHmac('sha256', 'khóa thử nghiệm').update(header + '.' + payload).digest('base64url'))
    expect(decodeJwt(token)).toEqual(claims)
  })

  it('uses the configured dev key and issues second-based dates', async () => {
    vi.stubEnv('VITE_DEV_JWT_SECRET', 'test-key')
    vi.useFakeTimers()
    vi.setSystemTime(claims.iat * 1000 + 500)
    const token = await signDevJwt({ user_id: claims.user_id, email: claims.email, role: claims.role })
    expect(token).toBe(await signJwt(claims, 'test-key'))
  })

  it('rejects dev signing without a key or with an ID outside Go’s accepted range', async () => {
    vi.stubEnv('VITE_DEV_JWT_SECRET', '')
    await expect(signDevJwt(claims)).rejects.toThrow('VITE_DEV_JWT_SECRET')
    await expect(signJwt({ ...claims, user_id: 2_147_483_648 }, 'key')).rejects.toThrow()
  })

  it('accepts a case-insensitive Bearer prefix and leaves opaque tokens usable', () => {
    expect(normalizeBearerToken('  bEaReR   outside-token  ')).toBe('outside-token')
    expect(decodeJwt('outside-token')).toBeNull()
    expect(normalizeBearerToken('Bearer')).toBe('')
  })

  it('decodes compatible external claims without requiring the optional iat claim', () => {
    const { iat: _iat, ...external } = claims
    const payload = Buffer.from(JSON.stringify(external)).toString('base64url')
    expect(decodeJwt('Bearer e30.' + payload + '.signature')).toEqual(external)
  })
})
