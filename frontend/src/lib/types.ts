// Shapes returned by the Spring Boot API. Keep in sync with the records in backend/.../publicapi and .../manage.

export interface Tenant {
  slug: string
  name: string
  timezone: string
  description: string | null
  address: string | null
  phone: string | null
  primaryColor: string
  logoUrl: string | null
}

// ---- public ----

export interface ServiceItem {
  id: string
  name: string
  description: string | null
  durationMinutes: number
  priceCents: number
}

export interface StaffItem {
  id: string
  displayName: string
  bio: string | null
  photoUrl: string | null
  serviceIds: string[]
}

export interface Shop {
  tenant: Tenant
  services: ServiceItem[]
  staff: StaffItem[]
}

export interface DaySlots {
  date: string // YYYY-MM-DD in the business's timezone
  slots: string[] // ISO instants
}

export interface Availability {
  timezone: string
  days: DaySlots[]
}

export interface BookRequest {
  serviceId: string
  staffId: string | null
  startAt: string
  customerName: string
  customerEmail: string
  customerPhone: string
  notes?: string
}

export type AppointmentStatus = 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW'

export interface Booking {
  token: string
  status: AppointmentStatus
  businessName: string
  businessSlug: string
  businessAddress: string | null
  businessPhone: string | null
  timezone: string
  serviceName: string
  priceCents: number
  staffName: string
  startAt: string
  endAt: string
  customerName: string
  customerEmail: string
}

// ---- manager ----

export interface Me {
  onboarded: boolean
  tenant: Tenant | null
}

export interface ManagedService extends ServiceItem {
  active: boolean
}

export interface ManagedStaff {
  id: string
  displayName: string
  bio: string | null
  photoUrl: string | null
  active: boolean
  serviceIds: string[]
}

export interface HoursWindow {
  dayOfWeek: number // 1 = Monday ... 7 = Sunday
  startTime: string // HH:mm or HH:mm:ss
  endTime: string
}

export interface TimeOffItem {
  id: string
  startAt: string
  endAt: string
  reason: string | null
}

export interface AppointmentRow {
  id: string
  startAt: string
  endAt: string
  status: AppointmentStatus
  serviceId: string
  serviceName: string
  staffId: string
  staffName: string
  customerName: string
  customerEmail: string
  customerPhone: string
  notes: string | null
  priceCents: number
}
