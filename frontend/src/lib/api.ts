import type {
  AppointmentRow,
  AppointmentStatus,
  Availability,
  BookRequest,
  Booking,
  HoursWindow,
  ManagedService,
  ManagedStaff,
  Me,
  Shop,
  Tenant,
  TimeOffItem,
} from './types'

const BASE: string = import.meta.env.VITE_API_URL || '/api'

export class ApiError extends Error {
  status: number
  code: string
  fields?: Record<string, string>

  constructor(status: number, code: string, message: string, fields?: Record<string, string>) {
    super(message)
    this.status = status
    this.code = code
    this.fields = fields
  }
}

export type TokenGetter = () => Promise<string | null>

interface RequestOptions {
  method?: string
  body?: unknown
  getToken?: TokenGetter
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (options.getToken) {
    const token = await options.getToken()
    if (token) headers['Authorization'] = `Bearer ${token}`
  }

  let res: Response
  try {
    res = await fetch(`${BASE}${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'NETWORK', 'Could not reach the server. Check your connection and try again.')
  }

  if (res.status === 204) return undefined as T

  const text = await res.text()
  let data: unknown = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = null
    }
  }

  if (!res.ok) {
    const err = (data ?? {}) as { code?: string; message?: string; fields?: Record<string, string> }
    throw new ApiError(res.status, err.code ?? `HTTP_${res.status}`, err.message ?? res.statusText, err.fields ?? undefined)
  }
  return data as T
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.fields) {
      const first = Object.values(error.fields)[0]
      if (first) return first
    }
    return error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong'
}

// ---------------------------------------------------------------------------- public (no auth)

export const publicApi = {
  shop: (slug: string) => request<Shop>(`/public/${encodeURIComponent(slug)}`),

  availability: (slug: string, serviceId: string, staffId: string | null, from: string, to: string) => {
    const params = new URLSearchParams({ serviceId, from, to })
    if (staffId) params.set('staffId', staffId)
    return request<Availability>(`/public/${encodeURIComponent(slug)}/availability?${params.toString()}`)
  },

  book: (slug: string, body: BookRequest) =>
    request<Booking>(`/public/${encodeURIComponent(slug)}/appointments`, { method: 'POST', body }),

  booking: (token: string) => request<Booking>(`/public/bookings/${encodeURIComponent(token)}`),

  cancel: (token: string) =>
    request<Booking>(`/public/bookings/${encodeURIComponent(token)}/cancel`, { method: 'POST' }),
}

// ------------------------------------------------------------------ manager (Clerk bearer token)

export type TenantInput = Omit<Tenant, 'slug'>

export interface ServiceInput {
  name: string
  description: string
  durationMinutes: number
  priceCents: number
  active: boolean
}

export interface StaffInput {
  displayName: string
  bio: string
  photoUrl: string
  active: boolean
  serviceIds: string[]
}

export interface TimeOffInput {
  startDate: string
  endDate: string
  startTime?: string
  endTime?: string
  reason?: string
}

export function manageApi(getToken: TokenGetter) {
  const call = <T>(path: string, method?: string, body?: unknown) =>
    request<T>(`/manage${path}`, { method, body, getToken })

  return {
    me: () => call<Me>('/me'),
    onboard: (body: { name: string; slug: string; timezone: string }) => call<Tenant>('/onboarding', 'POST', body),
    getTenant: () => call<Tenant>('/tenant'),
    updateTenant: (body: TenantInput) => call<Tenant>('/tenant', 'PUT', body),

    listServices: () => call<ManagedService[]>('/services'),
    createService: (body: ServiceInput) => call<ManagedService>('/services', 'POST', body),
    updateService: (id: string, body: ServiceInput) => call<ManagedService>(`/services/${id}`, 'PUT', body),

    listStaff: () => call<ManagedStaff[]>('/staff'),
    createStaff: (body: StaffInput) => call<ManagedStaff>('/staff', 'POST', body),
    updateStaff: (id: string, body: StaffInput) => call<ManagedStaff>(`/staff/${id}`, 'PUT', body),

    getHours: (staffId: string) => call<HoursWindow[]>(`/staff/${staffId}/hours`),
    saveHours: (staffId: string, windows: HoursWindow[]) =>
      call<HoursWindow[]>(`/staff/${staffId}/hours`, 'PUT', { windows }),

    listTimeOff: (staffId: string) => call<TimeOffItem[]>(`/staff/${staffId}/time-off`),
    addTimeOff: (staffId: string, body: TimeOffInput) =>
      call<TimeOffItem>(`/staff/${staffId}/time-off`, 'POST', body),
    deleteTimeOff: (staffId: string, timeOffId: string) =>
      call<void>(`/staff/${staffId}/time-off/${timeOffId}`, 'DELETE'),

    listAppointments: (from: string, to: string, staffId: string | null) => {
      const params = new URLSearchParams({ from, to })
      if (staffId) params.set('staffId', staffId)
      return call<AppointmentRow[]>(`/appointments?${params.toString()}`)
    },
    updateAppointment: (id: string, patch: { status?: AppointmentStatus; staffId?: string }) =>
      call<AppointmentRow>(`/appointments/${id}`, 'PATCH', patch),
  }
}

export type ManageApi = ReturnType<typeof manageApi>
