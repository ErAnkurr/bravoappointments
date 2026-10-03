import { useState } from 'react'
import type { CSSProperties, ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import type { UseFormReturn } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { ApiError, errorMessage, publicApi } from '../../lib/api'
import type { ServiceItem, StaffItem, Tenant } from '../../lib/types'
import {
  addDays,
  formatDateTime,
  formatDay,
  formatDuration,
  formatMoney,
  formatTime,
  readableTextOn,
  todayIn,
} from '../../lib/format'
import { Button, Card, ErrorNote, Field, Input, Spinner, Textarea, cn } from '../../components/ui'

const detailsSchema = z.object({
  customerName: z.string().trim().min(1, 'Enter your name').max(120),
  customerEmail: z.email('Enter a valid email address'),
  customerPhone: z.string().trim().min(7, 'Enter a phone number').max(40),
  notes: z.string().max(1000).optional(),
})
type Details = z.infer<typeof detailsSchema>

function Step({ number, title, children }: { number: number; title: string; children: ReactNode }) {
  return (
    <section className="mt-8">
      <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold">
        <span className="flex h-6 w-6 items-center justify-center rounded-full bg-brand text-xs text-on-brand">
          {number}
        </span>
        {title}
      </h2>
      {children}
    </section>
  )
}

export default function BookingPage() {
  const { slug = '' } = useParams()
  const shop = useQuery({ queryKey: ['shop', slug], queryFn: () => publicApi.shop(slug), retry: false })

  const [serviceId, setServiceId] = useState<string | null>(null)
  const [staffId, setStaffId] = useState<string | null>(null) // null = any available barber
  const [slot, setSlot] = useState<string | null>(null)
  // Shown above the calendar when a slot is lost to someone else; it must outlive the details form.
  const [notice, setNotice] = useState<string | null>(null)
  // Lives here (not in DetailsForm) so what the guest typed survives losing a slot and picking another.
  const detailsForm = useForm<Details>({ resolver: zodResolver(detailsSchema) })

  if (shop.isPending) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-10">
        <Spinner label="Loading" />
      </main>
    )
  }
  if (shop.isError) {
    const notFound = shop.error instanceof ApiError && shop.error.status === 404
    return (
      <main className="mx-auto max-w-md px-4 py-20 text-center">
        <h1 className="text-xl font-semibold">{notFound ? "We couldn't find that business" : 'Something went wrong'}</h1>
        <p className="mt-2 text-stone-600">
          {notFound ? 'Check the link and try again.' : errorMessage(shop.error)}
        </p>
      </main>
    )
  }

  const { tenant, services, staff } = shop.data
  const service = services.find((s) => s.id === serviceId) ?? null
  const eligibleStaff = service ? staff.filter((s) => s.serviceIds.includes(service.id)) : []
  const brand = {
    '--brand': tenant.primaryColor,
    '--brand-contrast': readableTextOn(tenant.primaryColor),
  } as CSSProperties

  return (
    <div style={brand} className="min-h-screen">
      <header className="bg-brand text-on-brand">
        <div className="mx-auto max-w-2xl px-4 py-8">
          {tenant.logoUrl && <img src={tenant.logoUrl} alt="" className="mb-3 h-12 w-auto rounded" />}
          <h1 className="text-2xl font-semibold">{tenant.name}</h1>
          {tenant.description && <p className="mt-1 opacity-90">{tenant.description}</p>}
          <p className="mt-2 text-sm opacity-80">
            {[tenant.address, tenant.phone].filter(Boolean).join(' · ')}
          </p>
        </div>
      </header>

      <main className="mx-auto max-w-2xl px-4 pb-16">
        <Step number={1} title="Choose a service">
          {services.length === 0 ? (
            <p className="text-stone-600">This business hasn&apos;t added any services yet.</p>
          ) : (
            <div className="grid gap-3">
              {services.map((s) => (
                <ServiceCard
                  key={s.id}
                  service={s}
                  selected={s.id === serviceId}
                  onSelect={() => {
                    setServiceId(s.id)
                    setStaffId(null)
                    setSlot(null)
                  }}
                />
              ))}
            </div>
          )}
        </Step>

        {service && (
          <Step number={2} title="Choose a barber">
            <div className="grid gap-3 sm:grid-cols-2">
              <StaffCard
                name="Any available"
                initial="★"
                bio="First free barber. Usually the most times to choose from."
                selected={staffId === null}
                onSelect={() => {
                  setStaffId(null)
                  setSlot(null)
                }}
              />
              {eligibleStaff.map((s) => (
                <StaffCard
                  key={s.id}
                  name={s.displayName}
                  bio={s.bio}
                  photoUrl={s.photoUrl}
                  selected={s.id === staffId}
                  onSelect={() => {
                    setStaffId(s.id)
                    setSlot(null)
                  }}
                />
              ))}
            </div>
            {eligibleStaff.length === 0 && (
              <p className="mt-3 text-sm text-stone-600">No barber is set up for this service yet.</p>
            )}
          </Step>
        )}

        {service && eligibleStaff.length > 0 && (
          <Step number={3} title="Pick a time">
            {notice && (
              <div className="mb-3">
                <ErrorNote message={notice} />
              </div>
            )}
            <TimePicker
              key={`${service.id}:${staffId ?? 'any'}`}
              slug={slug}
              timezone={tenant.timezone}
              serviceId={service.id}
              staffId={staffId}
              slot={slot}
              onSlot={(picked) => {
                setNotice(null)
                setSlot(picked)
              }}
            />
          </Step>
        )}

        {service && slot && (
          <Step number={4} title="Your details">
            <DetailsForm
              form={detailsForm}
              slug={slug}
              tenant={tenant}
              service={service}
              staff={eligibleStaff.find((s) => s.id === staffId) ?? null}
              staffId={staffId}
              slot={slot}
              onSlotTaken={(message) => {
                setSlot(null)
                setNotice(message)
              }}
            />
          </Step>
        )}

        <p className="mt-12 text-center text-xs text-stone-400">
          <Link to="/" className="hover:underline">
            Powered by BravoAppointments
          </Link>
        </p>
      </main>
    </div>
  )
}

function ServiceCard({
  service,
  selected,
  onSelect,
}: {
  service: ServiceItem
  selected: boolean
  onSelect: () => void
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className={cn(
        'rounded-xl border bg-white p-4 text-left transition hover:border-brand',
        selected ? 'border-brand ring-2 ring-brand' : 'border-stone-200',
      )}
    >
      <div className="flex items-baseline justify-between gap-4">
        <span className="font-medium">{service.name}</span>
        <span className="text-sm font-medium">{formatMoney(service.priceCents)}</span>
      </div>
      <div className="mt-0.5 text-sm text-stone-500">{formatDuration(service.durationMinutes)}</div>
      {service.description && <p className="mt-1 text-sm text-stone-600">{service.description}</p>}
    </button>
  )
}

function StaffCard({
  name,
  bio,
  photoUrl,
  initial,
  selected,
  onSelect,
}: {
  name: string
  bio?: string | null
  photoUrl?: string | null
  initial?: string
  selected: boolean
  onSelect: () => void
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className={cn(
        'flex gap-3 rounded-xl border bg-white p-4 text-left transition hover:border-brand',
        selected ? 'border-brand ring-2 ring-brand' : 'border-stone-200',
      )}
    >
      {photoUrl ? (
        <img src={photoUrl} alt="" className="h-12 w-12 shrink-0 rounded-full object-cover" />
      ) : (
        <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-stone-200 text-lg font-semibold text-stone-600">
          {initial ?? name.charAt(0)}
        </span>
      )}
      <span>
        <span className="block font-medium">{name}</span>
        {bio && <span className="block text-sm text-stone-600">{bio}</span>}
      </span>
    </button>
  )
}

function TimePicker({
  slug,
  timezone,
  serviceId,
  staffId,
  slot,
  onSlot,
}: {
  slug: string
  timezone: string
  serviceId: string
  staffId: string | null
  slot: string | null
  onSlot: (slot: string) => void
}) {
  const from = todayIn(timezone)
  const to = addDays(from, 13)
  const availability = useQuery({
    queryKey: ['availability', slug, serviceId, staffId, from],
    queryFn: () => publicApi.availability(slug, serviceId, staffId, from, to),
  })
  const [pickedDay, setPickedDay] = useState<string | null>(null)

  if (availability.isPending) return <Spinner label="Checking availability" />
  if (availability.isError) return <ErrorNote message={errorMessage(availability.error)} />

  const days = availability.data.days
  const firstOpen = days.find((d) => d.slots.length > 0)?.date ?? null
  if (firstOpen === null) {
    return (
      <p className="rounded-lg bg-white p-4 text-stone-600">
        No openings in the next two weeks. Try another barber, or call the shop.
      </p>
    )
  }
  const activeDay = pickedDay ?? firstOpen
  const activeSlots = days.find((d) => d.date === activeDay)?.slots ?? []

  return (
    <div>
      <div className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-2" role="tablist" aria-label="Day">
        {days.map((d) => {
          const open = d.slots.length > 0
          const active = d.date === activeDay
          return (
            <button
              key={d.date}
              type="button"
              role="tab"
              aria-selected={active}
              disabled={!open}
              onClick={() => setPickedDay(d.date)}
              className={cn(
                'shrink-0 rounded-lg border px-3 py-2 text-sm transition',
                active ? 'border-brand bg-brand text-on-brand' : 'border-stone-200 bg-white hover:border-brand',
                !open && 'cursor-not-allowed opacity-40 hover:border-stone-200',
              )}
            >
              {formatDay(d.date)}
            </button>
          )
        })}
      </div>

      <div className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-4">
        {activeSlots.map((iso) => (
          <button
            key={iso}
            type="button"
            aria-pressed={iso === slot}
            onClick={() => onSlot(iso)}
            className={cn(
              'rounded-lg border px-2 py-2 text-sm transition',
              iso === slot ? 'border-brand bg-brand text-on-brand' : 'border-stone-200 bg-white hover:border-brand',
            )}
          >
            {formatTime(iso, timezone)}
          </button>
        ))}
      </div>
    </div>
  )
}

function DetailsForm({
  form,
  slug,
  tenant,
  service,
  staff,
  staffId,
  slot,
  onSlotTaken,
}: {
  form: UseFormReturn<Details>
  slug: string
  tenant: Tenant
  service: ServiceItem
  staff: StaffItem | null
  staffId: string | null
  slot: string
  onSlotTaken: (message: string) => void
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { register, handleSubmit, formState } = form

  const book = useMutation({
    mutationFn: (values: Details) =>
      publicApi.book(slug, {
        serviceId: service.id,
        staffId,
        startAt: slot,
        customerName: values.customerName,
        customerEmail: values.customerEmail,
        customerPhone: values.customerPhone,
        notes: values.notes || undefined,
      }),
    onSuccess: (booking) => navigate(`/b/${slug}/booking/${booking.token}`),
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'SLOT_UNAVAILABLE') {
        // Someone else got it first: refresh the calendar and let the guest pick again.
        void queryClient.invalidateQueries({ queryKey: ['availability'] })
        onSlotTaken(error.message)
      }
    },
  })

  return (
    <Card>
      <p className="mb-4 rounded-lg bg-stone-100 px-3 py-2 text-sm">
        <strong>{service.name}</strong> · {formatDuration(service.durationMinutes)} · {formatMoney(service.priceCents)}
        <br />
        {formatDateTime(slot, tenant.timezone)}
        {staff ? ` with ${staff.displayName}` : ''}
      </p>

      <form onSubmit={handleSubmit((values) => book.mutate(values))} className="grid gap-4" noValidate>
        <Field label="Name" error={formState.errors.customerName?.message}>
          <Input autoComplete="name" {...register('customerName')} />
        </Field>
        <Field label="Email" error={formState.errors.customerEmail?.message} hint="We'll send your confirmation here.">
          <Input type="email" autoComplete="email" {...register('customerEmail')} />
        </Field>
        <Field label="Phone" error={formState.errors.customerPhone?.message}>
          <Input type="tel" autoComplete="tel" {...register('customerPhone')} />
        </Field>
        <Field label="Notes (optional)" error={formState.errors.notes?.message}>
          <Textarea {...register('notes')} />
        </Field>

        <ErrorNote message={book.isError ? errorMessage(book.error) : null} />
        <Button type="submit" disabled={book.isPending}>
          {book.isPending ? 'Booking…' : 'Book appointment'}
        </Button>
      </form>
    </Card>
  )
}
