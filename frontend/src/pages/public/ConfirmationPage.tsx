import type { CSSProperties } from 'react'
import { Link, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { errorMessage, publicApi } from '../../lib/api'
import { formatDateTime, formatDuration, formatMoney } from '../../lib/format'
import { Card, ConfirmButton, ErrorNote, Spinner, StatusPill } from '../../components/ui'

export default function ConfirmationPage() {
  const { slug = '', token = '' } = useParams()
  const queryClient = useQueryClient()
  const booking = useQuery({ queryKey: ['booking', token], queryFn: () => publicApi.booking(token), retry: false })
  // Branding (colour) lives on the shop payload; reuse it when it's already cached, otherwise fall back to default.
  const shop = useQuery({ queryKey: ['shop', slug], queryFn: () => publicApi.shop(slug), retry: false })

  const cancel = useMutation({
    mutationFn: () => publicApi.cancel(token),
    onSuccess: (updated) => queryClient.setQueryData(['booking', token], updated),
  })

  if (booking.isPending) {
    return (
      <main className="mx-auto max-w-lg px-4 py-10">
        <Spinner />
      </main>
    )
  }
  if (booking.isError) {
    return (
      <main className="mx-auto max-w-md px-4 py-20 text-center">
        <h1 className="text-xl font-semibold">Booking not found</h1>
        <p className="mt-2 text-stone-600">This link may be incorrect or expired.</p>
      </main>
    )
  }

  const b = booking.data
  const style = shop.data
    ? ({ '--brand': shop.data.tenant.primaryColor } as CSSProperties)
    : undefined
  const upcoming = new Date(b.startAt).getTime() > Date.now()
  const cancelled = b.status === 'CANCELLED'

  return (
    <main style={style} className="mx-auto max-w-lg px-4 py-10">
      <h1 className="text-2xl font-semibold">
        {cancelled ? 'Appointment cancelled' : 'You’re booked'}
      </h1>
      <p className="mt-1 text-stone-600">
        {cancelled
          ? 'This appointment has been cancelled.'
          : `A confirmation has been sent to ${b.customerEmail}.`}
      </p>

      <Card className="mt-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="text-lg font-medium">{b.serviceName}</div>
            <div className="text-sm text-stone-600">
              with {b.staffName} · {formatDuration(Math.round((new Date(b.endAt).getTime() - new Date(b.startAt).getTime()) / 60000))}{' '}
              · {formatMoney(b.priceCents)}
            </div>
          </div>
          <StatusPill status={b.status} />
        </div>
        <dl className="mt-4 grid gap-3 text-sm">
          <div>
            <dt className="text-stone-500">When</dt>
            <dd className="font-medium">{formatDateTime(b.startAt, b.timezone)}</dd>
          </div>
          <div>
            <dt className="text-stone-500">Where</dt>
            <dd className="font-medium">
              {b.businessName}
              {b.businessAddress && <span className="block font-normal">{b.businessAddress}</span>}
              {b.businessPhone && <span className="block font-normal">{b.businessPhone}</span>}
            </dd>
          </div>
        </dl>
      </Card>

      {!cancelled && upcoming && b.status === 'CONFIRMED' && (
        <div className="mt-6 space-y-3">
          <ErrorNote message={cancel.isError ? errorMessage(cancel.error) : null} />
          <ConfirmButton
            confirmLabel="Yes, cancel appointment"
            disabled={cancel.isPending}
            onConfirm={() => cancel.mutate()}
          >
            Cancel appointment
          </ConfirmButton>
          <p className="text-xs text-stone-500">To change the time, cancel and book a new slot.</p>
        </div>
      )}

      <p className="mt-10 text-sm">
        <Link to={`/b/${b.businessSlug}`} className="underline">
          Book another appointment
        </Link>
      </p>
    </main>
  )
}
