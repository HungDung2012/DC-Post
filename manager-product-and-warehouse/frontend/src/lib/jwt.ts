export interface ActorClaims {
  user_id: number
  email: string
  role: string
}

export interface JwtClaims extends ActorClaims {
  exp: number
  iat: number
}

type DecodedJwtClaims = Omit<JwtClaims, 'iat'> & { iat?: number }

const encoder = new TextEncoder()
export const maxUserId = 2_147_483_647

export function normalizeBearerToken(value: string): string {
  return value.trim().replace(/^bearer(?:\s+|$)/i, '')
}

function base64url(bytes: Uint8Array): string {
  return btoa(Array.from(bytes, (byte) => String.fromCharCode(byte)).join(''))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function isActor(claims: Partial<ActorClaims>): claims is ActorClaims {
  const { user_id, email, role } = claims
  return typeof user_id === 'number' && Number.isInteger(user_id) &&
    user_id > 0 && user_id <= maxUserId &&
    typeof email === 'string' && email.trim() !== '' &&
    typeof role === 'string' && role.trim() !== ''
}

// Decodes claims for display. The API verifies the token on authenticated requests.
export function decodeJwt(value: string): DecodedJwtClaims | null {
  const segments = normalizeBearerToken(value).split('.')
  if (segments.length !== 3 || !segments.every((segment) => /^[\w-]+$/.test(segment))) return null

  try {
    const payload = atob(segments[1].replace(/-/g, '+').replace(/_/g, '/'))
    const claims: unknown = JSON.parse(new TextDecoder().decode(Uint8Array.from(payload, (char) => char.charCodeAt(0))))
    if (typeof claims !== 'object' || claims === null) return null

    const { exp, iat, ...actor } = claims as Partial<JwtClaims>
    if (!isActor(actor) || typeof exp !== 'number' || !Number.isFinite(exp)) return null
    const { user_id, email, role } = actor
    return { user_id, email, role, exp, ...(typeof iat === 'number' && Number.isFinite(iat) ? { iat } : {}) }
  } catch {
    return null
  }
}

export async function signJwt({ user_id, email, role, exp, iat }: JwtClaims, secret: string): Promise<string> {
  if (!secret) throw new Error('Thiếu cấu hình VITE_DEV_JWT_SECRET.')
  if (!isActor({ user_id, email, role })) throw new Error('Thông tin người dùng không hợp lệ.')
  if (!Number.isSafeInteger(exp) || !Number.isSafeInteger(iat) || exp <= iat) {
    throw new Error('Thời hạn token không hợp lệ.')
  }

  const subtle = globalThis.crypto?.subtle
  if (!subtle) throw new Error('Web Crypto yêu cầu localhost hoặc HTTPS để tạo token.')

  const header = base64url(encoder.encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' })))
  const payload = base64url(encoder.encode(JSON.stringify({ user_id, email, role, exp, iat })))
  const message = header + '.' + payload
  const key = await subtle.importKey('raw', encoder.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign'])
  const signature = await subtle.sign('HMAC', key, encoder.encode(message))
  return message + '.' + base64url(new Uint8Array(signature))
}

export function signDevJwt(actor: ActorClaims): Promise<string> {
  const iat = Math.floor(Date.now() / 1000)
  return signJwt({ ...actor, exp: iat + 3600, iat }, import.meta.env.VITE_DEV_JWT_SECRET)
}
