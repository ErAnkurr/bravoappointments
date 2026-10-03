import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useManageApi } from '../../lib/clerk'
import { errorMessage } from '../../lib/api'
import type { AppointmentRow, AppointmentStatus } from '../../lib/types'
import { addDays, dayInZone, formatDay, formatMoney, formatTime, todayIn } from '../../lib/format'
import { Button, Card, ConfirmButton, ErrorNote, Field, Input, Select, Spinner, StatusPill } from '../../components/ui'
import { useDashboard } from './DashboardLayout'

export default function AppointmentsPage() {
  const { tenant } = useDashboard()
  const api = useManageApi()
  const queryClient = useQueryClient()

  const today = todayIn(tenant.timezone)
  const [from, setFrom] = useState(today)
  const [to, setTo] = useState(addDays(today, 7))
  const [staffId, setStaffId] = useState('')

  const staff = useQuery({ queryKey: ['staff'], queryFn: api.listStaff })
  const list = useQuery({
    queryKey: ['appointments', from, to, staffId],
    queryFn: () => api.listAppointments(from, to, staffId || null),
  })

  const update = useMutation({
    mutationFn: (v: { id: string; patch: { status?: AppointmentStatus; staffId?: string } }) =>
      api.updateAppointment(v.id, v.patch),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['appointments'] }),
  })

  const rows = list.data ?? []
  const byDay = new Map<string, AppointmentRow[]>()
  for (const row of rows) {
    const day = dayInZone(row.startAt, tenant.timezone)
    byDay.set(day, [...(byDay.get(day) ?? []), row])
  }

  const bookingLink = `${window.location.origin}/b/${tenant.slug}`

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-end gap-3">
        <Field label="From">
          <Input type="date" value={from} onChange={(e) => e.target.value && setFrom(e.target.value)} />
        </Field>
        <Field label="To">
          <Input type="date" value={to} onChange={(e) => e.target.value && setTo(e.target.value)} />
        </Field>
        <Field label="Barber">
          <Select value={staffId} onChange={(e) => setStaffId(e.target.value)}>
            <option value="">Everyone</option>
            {(staff.data ?? []).map((s) => (
              <option key={s.id} value={s.id}>
                {s.displayName}
              </option>
            ))}
          </Select>
        </Field>
        <div className="ml-auto text-sm text-stone-600">
          Booking page:{' '}
          <a className="font-medium underline" href={bookingLink} target="_blank" rel="noreferrer">
            {bookingLink}
          </a>
        </div>
      </div>

      <ErrorNote message={update.isError ? errorMessage(update.error) : list.isError ? errorMessage(list.error) : null} />
      {list.isPending && <Spinner />}

      {list.isSuccess && rows.length === 0 && (
        <Card className="text-center text-stone-600">No appointments in this range.</Card>
      )}

      {[...byDay.entries()].map(([day, items]) => (
        <section key={day}>
          <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-stone-500">
            {formatDay(day, 'long')}
          </h2>
          <div className="grid gap-3">
            {items.map((a) => (
              <Card key={a.id} className="grid gap-3 sm:grid-cols-[1fr_auto]">
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">
                      {formatTime(a.startAt, tenant.timezone)} – {formatTime(a.endAt, tenant.timezone)}
                    </span>
                    <StatusPill status={a.status} />
                  </div>
                  <div className="mt-1 text-sm">
                    {a.serviceName} · {formatMoney(a.priceCents)}
                  </div>
                  <div className="mt-1 text-sm text-stone-600">
                    {a.customerName} · {a.customerPhone} ·{' '}
                    <a className="underline" href={`mailto:${a.customerEmail}`}>
                      {a.customerEmail}
                    </a>
                  </div>
                  {a.notes && <div className="mt-1 text-sm italic text-stone-500">“{a.notes}”</div>}
                </div>

                {a.status !== 'CANCELLED' && (
                  <div className="flex flex-col gap-2 sm:items-end">
                    <Select
                      aria-label="Barber"
                      className="sm:w-44"
                      value={a.staffId}
                      disabled={update.isPending}
                      onChange={(e) => update.mutate({ id: a.id, patch: { staffId: e.target.value } })}
                    >
                      {(staff.data ?? [])
                        .filter((s) => s.active || s.id === a.staffId)
                        .map((s) => (
                          <option key={s.id} value={s.id}>
                            {s.displayName}
                          </option>
                        ))}
                    </Select>
                    <div className="flex flex-wrap gap-2">
                      {a.status === 'CONFIRMED' && (
                        <>
                          <Button
                            variant="secondary"
                            disabled={update.isPending}
                            onClick={() => update.mutate({ id: a.id, patch: { status: 'COMPLETED' } })}
                          >
                            Completed
                          </Button>
                          <Button
                            variant="secondary"
                            disabled={update.isPending}
                            onClick={() => update.mutate({ id: a.id, patch: { status: 'NO_SHOW' } })}
                          >
                            No-show
                          </Button>
                        </>
                      )}
                      {a.status !== 'CONFIRMED' && (
                        <Button
                          variant="secondary"
                          disabled={update.isPending}
                          onClick={() => update.mutate({ id: a.id, patch: { status: 'CONFIRMED' } })}
                        >
                          Reopen
                        </Button>
                      )}
                      <ConfirmButton
                        confirmLabel="Cancel it"
                        disabled={update.isPending}
                        onConfirm={() => update.mutate({ id: a.id, patch: { status: 'CANCELLED' } })}
                      >
                        Cancel
                      </ConfirmButton>
                    </div>
                  </div>
                )}
              </Card>
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}
