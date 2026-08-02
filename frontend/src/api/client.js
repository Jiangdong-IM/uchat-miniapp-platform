const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
const TOKEN_KEY = 'uchat-miniapp-platform-token'

let accessToken = window.localStorage.getItem(TOKEN_KEY) || ''

export class ApiError extends Error {
  constructor(message, { code = 'REQUEST_FAILED', status = 0, details = null } = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.details = details
  }
}

export function setAccessToken(token) {
  accessToken = token || ''
  if (accessToken) {
    window.localStorage.setItem(TOKEN_KEY, accessToken)
  } else {
    window.localStorage.removeItem(TOKEN_KEY)
  }
}

export function hasAccessToken() {
  return Boolean(accessToken)
}

export async function apiRequest(path, options = {}) {
  const headers = new Headers(options.headers || {})
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })
  } catch (error) {
    throw new ApiError('无法连接管理平台服务，请检查后端是否已启动。', {
      code: 'NETWORK_ERROR',
      details: error,
    })
  }

  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('application/json') ? await response.json() : null
  const message = payload?.message || payload?.msg || `请求失败（${response.status}）`
  if (!response.ok || payload?.success === false) {
    if (response.status === 401) setAccessToken('')
    throw new ApiError(message, {
      code: payload?.code || 'REQUEST_FAILED',
      status: response.status,
      details: payload?.details || null,
    })
  }
  return payload && Object.hasOwn(payload, 'data') ? payload.data : payload
}

function jsonBody(value) {
  return JSON.stringify(value)
}

function queryString(parameters) {
  const query = new URLSearchParams()
  Object.entries(parameters).forEach(([key, value]) => {
    if (value !== '' && value !== null && value !== undefined) query.set(key, String(value))
  })
  const encoded = query.toString()
  return encoded ? `?${encoded}` : ''
}

export const api = {
  auth: {
    register: (form) => apiRequest('/auth/register', { method: 'POST', body: jsonBody(form) }),
    login: (form) => apiRequest('/auth/login', { method: 'POST', body: jsonBody(form) }),
    me: () => apiRequest('/auth/me'),
    logout: () => apiRequest('/auth/logout', { method: 'POST' }),
  },
  developer: {
    dashboard: () => apiRequest('/developer/dashboard'),
    apps: () => apiRequest('/developer/apps'),
    app: (id) => apiRequest(`/developer/apps/${id}`),
    createApp: (form) => apiRequest('/developer/apps', { method: 'POST', body: jsonBody(form) }),
    updateApp: (id, form) => apiRequest(`/developer/apps/${id}`, { method: 'PUT', body: jsonBody(form) }),
    uploadAsset: (id, kind, file) => {
      const body = new FormData()
      body.append('file', file)
      return apiRequest(`/developer/apps/${id}/assets/${kind}`, { method: 'POST', body })
    },
    uploadVersion: (id, file, releaseNotes) => {
      const body = new FormData()
      body.append('file', file)
      body.append('releaseNotes', releaseNotes || '')
      return apiRequest(`/developer/apps/${id}/versions`, { method: 'POST', body })
    },
    versions: (id) => apiRequest(`/developer/apps/${id}/versions`),
    reviews: (id) => apiRequest(`/developer/apps/${id}/reviews`),
    delist: (id) => apiRequest(`/developer/apps/${id}/delist`, { method: 'POST' }),
    appeals: () => apiRequest('/developer/appeals'),
    createAppeal: (form) => apiRequest('/developer/appeals', { method: 'POST', body: jsonBody(form) }),
  },
  admin: {
    registrations: (status = 'PENDING') => apiRequest(`/admin/registrations?status=${encodeURIComponent(status)}`),
    decideRegistration: (id, form) => apiRequest(`/admin/registrations/${id}/decision`, {
      method: 'POST',
      body: jsonBody({ decision: form.approved ? 'APPROVED' : 'REJECTED', reviewNote: form.reason || '' }),
    }),
    versions: (status = 'PENDING_REVIEW') => apiRequest(`/admin/versions?status=${encodeURIComponent(status)}`),
    decideVersion: (id, form) => apiRequest(`/admin/versions/${id}/decision`, {
      method: 'POST',
      body: jsonBody({ decision: form.approved ? 'APPROVED' : 'REJECTED', reviewNote: form.reason || '' }),
    }),
    apps: (parameters = {}) => apiRequest(`/admin/apps${queryString(parameters)}`),
    app: (id) => apiRequest(`/admin/apps/${id}`),
    updateApp: (id, form) => apiRequest(`/admin/apps/${id}`, { method: 'PUT', body: jsonBody(form) }),
    uploadAppAsset: (id, kind, file) => {
      const body = new FormData()
      body.append('file', file)
      return apiRequest(`/admin/apps/${id}/assets/${kind}`, { method: 'POST', body })
    },
    publishApp: (id) => apiRequest(`/admin/apps/${id}/publish`, { method: 'POST' }),
    delistApp: (id) => apiRequest(`/admin/apps/${id}/delist`, { method: 'POST' }),
    comments: (parameters = {}) => apiRequest(`/admin/comments${queryString(parameters)}`),
    comment: (id) => apiRequest(`/admin/comments/${id}`),
    createComment: (form) => apiRequest('/admin/comments', { method: 'POST', body: jsonBody(form) }),
    updateComment: (id, form) => apiRequest(`/admin/comments/${id}`, { method: 'PUT', body: jsonBody(form) }),
    deleteComment: (id) => apiRequest(`/admin/comments/${id}`, { method: 'DELETE' }),
    featureComment: (id, featured) => apiRequest(`/admin/comments/${id}/featured`, {
      method: 'PUT',
      body: jsonBody({ featured }),
    }),
    developers: (parameters = {}) => apiRequest(`/admin/developers${queryString(parameters)}`),
    banDeveloper: (id, form) => apiRequest(`/admin/developers/${id}/ban`, {
      method: 'POST',
      body: jsonBody(form),
    }),
    unbanDeveloper: (id, form) => apiRequest(`/admin/developers/${id}/unban`, {
      method: 'POST',
      body: jsonBody(form),
    }),
    appeals: (parameters = {}) => apiRequest(`/admin/appeals${queryString(parameters)}`),
    decideAppeal: (id, form) => apiRequest(`/admin/appeals/${id}/decision`, {
      method: 'POST',
      body: jsonBody(form),
    }),
  },
}
