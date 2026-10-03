import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useManageApi } from '../../lib/clerk'
import { errorMessage } from '../../lib/api'
import type { HoursWindow, ManagedService, ManagedStaff } from '../../lib/types'
import { WEEKDAYS, formatDateTime, todayIn } from '../../lib/format'
import { Button, Card, ErrorNote, Field, Input, Spinner, Textarea, cn } from '../../components/ui'
import { useDashboard } from './DashboardLayout'

export default function StaffPage() {
  const api = useManageApi()
  const staff = useQuery({ queryKey: ['staff'], queryFn: api.listStaff })
  const services = useQuery({ queryKey: ['services'], queryFn: api.listServices })
  const [selected, setSelected] = useState<string | 'new' | null>(null)

  const current = staff.data?.find((s) => s.id === selected)

  return (
    <div className="grid gap-6 md:grid-cols-[16rem_1fr]">
      <aside className="grid content-start gap-2">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-semibold">Barbers</h1>
          <Button variant="secondary" onClick={() => setSelected('new')}>
            Add
          </Button>
        </div>
        {staff.isPending && <Spinner />}
        <ErrorNote message={staff.isError ? errorMessage(staff.error) : null} />
        {staff.data?.map((s) => (
          <button
            key={s.id}
            type="button"
            onClick={() => setSelected(s.id)}
            className={cn(
              'rounded-lg border px-3 py-2 text-left text-sm',
              s.id === selected ? 'border-stone-900 bg-white' : 'border-stone-200 bg-white hover:border-stone-400',
              !s.active && 'opacity-60',
            )}
          >
            <span className="font-medium">{s.displayName}</span>
            {!s.active && <span className="ml-2 text-xs text-stone-500">(hidden)</span>}
          </button>
        ))}
        {staff.data?.length === 0 && <p className="text-sm text-stone-600">No barbers yet.</p>}
      </aside>

      <section>
        {selected === null && <Card className="text-stone-600">Select a barber, or add one.</Card>}
        {selected === 'new' && services.data && (
          <ProfileForm
            key="new"
            services={services.data}
            onSaved={(created) => setSelected(created.id)}
          />
        )}
        {current && services.data && <StaffEditor key={current.id} staff={current} services={services.data} />}
      </section>
    </div>
  )
}

type Tab = 'profile' | 'hours' | 'timeoff'

function StaffEditor({ staff, services }: { staff: ManagedStaff; services: ManagedService[] }) {
  const [tab, setTab] = useState<Tab>('profile')
  const tabs: Array<{ id: Tab; label: string }> = [
    { id: 'profile', label: 'Profile & services' },
    { id: 'hours', label: 'Weekly hours' },
    { id: 'timeoff', label: 'Time off' },
  ]
  return (
    <div className="grid gap-4">
      <div className="flex gap-1 border-b border-stone-200" role="tablist">
        {tabs.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setTab(t.id)}
            className={cn(
              '-mb-px border-b-2 px-3 py-2 text-sm font-medium',
              tab === t.id ? 'border-stone-900 text-stone-900' : 'border-transparent text-stone-500 hover:text-stone-800',
            )}
          >
            {t.label}
          </button>
        ))}
      </div>
      {tab === 'profile' && <ProfileForm staff={staff} services={services} />}
      {tab === 'hours' && <HoursEditor staffId={staff.id} />}
      {tab === 'timeoff' && <TimeOffEditor staffId={staff.id} />}
    </div>
  )
}

// ------------------------------------------------------------------------------------- profile

const profileSchema = z.object({
  displayName: z.string().trim().min(1, 'Enter a name').max(80),
  bio: z.string().max(2000),
  photoUrl: z.string().max(500),
  active: z.boolean(),
})
type ProfileValues = z.infer<typeof profileSchema>

function ProfileForm({
  staff,
  services,
  onSaved,
}: {
  staff?: ManagedStaff
  services: ManagedService[]
  onSaved?: (saved: ManagedStaff) => void
}) {
  const api = useManageApi()
  const queryClient = useQueryClient()
  const [serviceIds, setServiceIds] = useState<Set<string>>(new Set(staff?.serviceIds ?? []))

  const { register, handleSubmit, formState } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      displayName: staff?.displayName ?? '',
      bio: staff?.bio ?? '',
      photoUrl: staff?.photoUrl ?? '',
      active: staff?.active ?? true,
    },
  })

  const save = useMutation({
    mutationFn: (v: ProfileValues) => {
      const body = { ...v, serviceIds: [...serviceIds] }
      return staff ? api.updateStaff(staff.id, body) : api.createStaff(body)
    },
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ['staff'] })
      onSaved?.(saved)
    },
  })

  function toggle(id: string) {
    setServiceIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  return (
    <Card>
      <form onSubmit={handleSubmit((v) => save.mutate(v))} className="grid gap-4" noValidate>
        <Field label="Name" error={formState.errors.displayName?.message}>
          <Input {...register('displayName')} />
        </Field>
        <Field label="Bio (optional)" error={formState.errors.bio?.message}>
          <Textarea {...register('bio')} />
        </Field>
        <Field label="Photo URL (optional)" error={formState.errors.photoUrl?.message}>
          <Input type="url" {...register('photoUrl')} />
        </Field>

        <fieldset>
          <legend className="mb-1 text-sm font-medium text-stone-700">Services they perform</legend>
          {services.length === 0 ? (
            <p className="text-sm text-stone-600">Add services first (Services tab), then assign them here.</p>
          ) : (
            <div className="grid gap-1">
              {services.map((s) => (
                <label key={s.id} className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={serviceIds.has(s.id)} onChange={() => toggle(s.id)} />
                  {s.name}
                  {!s.active && <span className="text-xs text-stone-500">(hidden)</span>}
                </label>
              ))}
            </div>
          )}
        </fieldset>

        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" {...register('active')} /> Shown on the booking page and bookable
        </label>

        <ErrorNote message={save.isError ? errorMessage(save.error) : null} />
        <div className="flex items-center gap-3">
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : staff ? 'Save changes' : 'Add barber'}
          </Button>
          {save.isSuccess && staff && <span className="text-sm text-emerald-700">Saved</span>}
        </div>
      </form>
    </Card>
  )
}

// --------------------------------------------------------------------------------- weekly hours

const trimTime = (t: string) => t.slice(0, 5)

function HoursEditor({ staffId }: { staffId: string }) {
  const api = useManageApi()
  const queryClient = useQueryClient()
  const hours = useQuery({ queryKey: ['hours', staffId], queryFn: () => api.getHours(staffId) })

  if (hours.isPending) return <Spinner />
  if (hours.isError) return <ErrorNote message={errorMessage(hours.error)} />

  return (
    <HoursForm
      initial={hours.data.map((w) => ({ ...w, startTime: trimTime(w.startTime), endTime: trimTime(w.endTime) }))}
      onSave={async (windows) => {
        await api.saveHours(staffId, windows)
        await queryClient.invalidateQueries({ queryKey: ['hours', staffId] })
      }}
    />
  )
}

function HoursForm({
  initial,
  onSave,
}: {
  initial: HoursWindow[]
  onSave: (windows: HoursWindow[]) => Promise<void>
}) {
  const [windows, setWindows] = useState<HoursWindow[]>(initial)
  const save = useMutation({ mutationFn: () => onSave(windows) })

  const patch = (index: number, change: Partial<HoursWindow>) =>
    setWindows((prev) => prev.map((w, i) => (i === index ? { ...w, ...change } : w)))
  const remove = (index: number) => setWindows((prev) => prev.filter((_, i) => i !== index))
  const add = (dayOfWeek: number) =>
    setWindows((prev) => [...prev, { dayOfWeek, startTime: '09:00', endTime: '17:00' }])

  return (
    <Card>
      <p className="mb-3 text-sm text-stone-600">
        Guests can only book inside these windows. Add two windows on one day for a lunch break. Times are in the
        business&apos;s timezone.
      </p>
      <div className="grid gap-3">
        {WEEKDAYS.map((name, dayIndex) => {
          const dayOfWeek = dayIndex + 1
          const dayWindows = windows
            .map((w, index) => ({ w, index }))
            .filter(({ w }) => w.dayOfWeek === dayOfWeek)
          return (
            <div key={name} className="grid items-start gap-2 sm:grid-cols-[7rem_1fr_auto]">
              <div className="pt-2 text-sm font-medium">{name}</div>
              <div className="grid gap-2">
                {dayWindows.length === 0 && <div className="pt-2 text-sm text-stone-500">Closed</div>}
                {dayWindows.map(({ w, index }) => (
                  <div key={index} className="flex items-center gap-2">
                    <Input
                      type="time"
                      aria-label={`${name} start`}
                      className="w-32"
                      value={w.startTime}
                      onChange={(e) => patch(index, { startTime: e.target.value })}
                    />
                    <span className="text-stone-500">to</span>
                    <Input
                      type="time"
                      aria-label={`${name} end`}
                      className="w-32"
                      value={w.endTime}
                      onChange={(e) => patch(index, { endTime: e.target.value })}
                    />
                    <Button variant="ghost" aria-label={`Remove ${name} window`} onClick={() => remove(index)}>
                      Remove
                    </Button>
                  </div>
                ))}
              </div>
              <Button variant="secondary" onClick={() => add(dayOfWeek)}>
                Add hours
              </Button>
            </div>
          )
        })}
      </div>

      <div className="mt-4 grid gap-3">
        <ErrorNote message={save.isError ? errorMessage(save.error) : null} />
        <div className="flex items-center gap-3">
          <Button disabled={save.isPending} onClick={() => save.mutate()}>
            {save.isPending ? 'Saving…' : 'Save hours'}
          </Button>
          {save.isSuccess && <span className="text-sm text-emerald-700">Saved</span>}
        </div>
      </div>
    </Card>
  )
}

// ----------------------------------------------------------------------------------- time off

const timeOffSchema = z.object({
  startDate: z.string().min(1, 'Pick a start date'),
  endDate: z.string().min(1, 'Pick an end date'),
  startTime: z.string(),
  endTime: z.string(),
  reason: z.string().max(200),
})
type TimeOffValues = z.infer<typeof timeOffSchema>

function TimeOffEditor({ staffId }: { staffId: string }) {
  const { tenant } = useDashboard()
  const api = useManageApi()
  const queryClient = useQueryClient()
  const list = useQuery({ queryKey: ['timeoff', staffId], queryFn: () => api.listTimeOff(staffId) })
  const today = todayIn(tenant.timezone)

  const { register, handleSubmit, reset, formState } = useForm<TimeOffValues>({
    resolver: zodResolver(timeOffSchema),
    defaultValues: { startDate: today, endDate: today, startTime: '', endTime: '', reason: '' },
  })

  const add = useMutation({
    mutationFn: (v: TimeOffValues) =>
      api.addTimeOff(staffId, {
        startDate: v.startDate,
        endDate: v.endDate,
        startTime: v.startTime || undefined,
        endTime: v.endTime || undefined,
        reason: v.reason || undefined,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['timeoff', staffId] })
      reset({ startDate: today, endDate: today, startTime: '', endTime: '', reason: '' })
    },
  })
  const remove = useMutation({
    mutationFn: (id: string) => api.deleteTimeOff(staffId, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['timeoff', staffId] }),
  })

  return (
    <div className="grid gap-4">
      <Card>
        <p className="mb-3 text-sm text-stone-600">
          Block days or hours when this barber can&apos;t take bookings. Leave the times empty to block whole days.
          Existing appointments aren&apos;t cancelled, so check the calendar.
        </p>
        <form onSubmit={handleSubmit((v) => add.mutate(v))} className="grid gap-3 sm:grid-cols-2" noValidate>
          <Field label="From date" error={formState.errors.startDate?.message}>
            <Input type="date" {...register('startDate')} />
          </Field>
          <Field label="To date (inclusive)" error={formState.errors.endDate?.message}>
            <Input type="date" {...register('endDate')} />
          </Field>
          <Field label="Start time (optional)">
            <Input type="time" {...register('startTime')} />
          </Field>
          <Field label="End time (optional)">
            <Input type="time" {...register('endTime')} />
          </Field>
          <Field label="Reason (optional)" className="sm:col-span-2">
            <Input {...register('reason')} />
          </Field>
          <div className="grid gap-3 sm:col-span-2">
            <ErrorNote message={add.isError ? errorMessage(add.error) : null} />
            <div>
              <Button type="submit" disabled={add.isPending}>
                {add.isPending ? 'Adding…' : 'Add time off'}
              </Button>
            </div>
          </div>
        </form>
      </Card>

      {list.isPending && <Spinner />}
      <ErrorNote message={remove.isError ? errorMessage(remove.error) : list.isError ? errorMessage(list.error) : null} />
      {list.data?.length === 0 && <p className="text-sm text-stone-600">No time off scheduled.</p>}
      {list.data?.map((t) => (
        <Card key={t.id} className="flex items-center justify-between gap-4">
          <div className="text-sm">
            <div className="font-medium">
              {formatDateTime(t.startAt, tenant.timezone)} → {formatDateTime(t.endAt, tenant.timezone)}
            </div>
            {t.reason && <div className="text-stone-600">{t.reason}</div>}
          </div>
          <Button variant="secondary" disabled={remove.isPending} onClick={() => remove.mutate(t.id)}>
            Delete
          </Button>
        </Card>
      ))}
    </div>
  )
}
