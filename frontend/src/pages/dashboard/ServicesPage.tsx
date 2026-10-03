import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useManageApi } from '../../lib/clerk'
import { errorMessage } from '../../lib/api'
import type { ServiceInput } from '../../lib/api'
import type { ManagedService } from '../../lib/types'
import { formatDuration, formatMoney } from '../../lib/format'
import { Button, Card, ErrorNote, Field, Input, Spinner, Textarea, cn } from '../../components/ui'

// Form values are strings; they're converted to cents/minutes on submit.
const schema = z.object({
  name: z.string().trim().min(1, 'Enter a name').max(120),
  description: z.string().max(2000),
  durationMinutes: z.string().regex(/^\d+$/, 'Whole minutes, e.g. 30').refine((v) => +v >= 5 && +v <= 480, 'Between 5 and 480'),
  price: z.string().regex(/^\d+(\.\d{1,2})?$/, 'e.g. 35 or 35.50'),
  active: z.boolean(),
})
type Values = z.infer<typeof schema>

function toValues(s?: ManagedService): Values {
  return {
    name: s?.name ?? '',
    description: s?.description ?? '',
    durationMinutes: String(s?.durationMinutes ?? 30),
    price: s ? (s.priceCents / 100).toFixed(2) : '',
    active: s?.active ?? true,
  }
}

function toInput(v: Values): ServiceInput {
  return {
    name: v.name,
    description: v.description,
    durationMinutes: Number(v.durationMinutes),
    priceCents: Math.round(Number(v.price) * 100),
    active: v.active,
  }
}

export default function ServicesPage() {
  const api = useManageApi()
  const services = useQuery({ queryKey: ['services'], queryFn: api.listServices })
  const [editing, setEditing] = useState<string | 'new' | null>(null)

  return (
    <div className="grid gap-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Services</h1>
        {editing !== 'new' && <Button onClick={() => setEditing('new')}>Add service</Button>}
      </div>

      {editing === 'new' && <ServiceForm onDone={() => setEditing(null)} />}
      {services.isPending && <Spinner />}
      <ErrorNote message={services.isError ? errorMessage(services.error) : null} />

      {services.data?.length === 0 && editing !== 'new' && (
        <Card className="text-center text-stone-600">No services yet. Add your first one so guests can book.</Card>
      )}

      {services.data?.map((s) =>
        editing === s.id ? (
          <ServiceForm key={s.id} service={s} onDone={() => setEditing(null)} />
        ) : (
          <Card key={s.id} className={cn('flex items-center justify-between gap-4', !s.active && 'opacity-60')}>
            <div>
              <div className="font-medium">
                {s.name} {!s.active && <span className="text-xs font-normal text-stone-500">(hidden)</span>}
              </div>
              <div className="text-sm text-stone-600">
                {formatDuration(s.durationMinutes)} · {formatMoney(s.priceCents)}
              </div>
              {s.description && <div className="mt-1 text-sm text-stone-500">{s.description}</div>}
            </div>
            <Button variant="secondary" onClick={() => setEditing(s.id)}>
              Edit
            </Button>
          </Card>
        ),
      )}
    </div>
  )
}

function ServiceForm({ service, onDone }: { service?: ManagedService; onDone: () => void }) {
  const api = useManageApi()
  const queryClient = useQueryClient()
  const { register, handleSubmit, formState } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: toValues(service),
  })

  const save = useMutation({
    mutationFn: (v: Values) => (service ? api.updateService(service.id, toInput(v)) : api.createService(toInput(v))),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['services'] })
      onDone()
    },
  })

  return (
    <Card>
      <form onSubmit={handleSubmit((v) => save.mutate(v))} className="grid gap-4 sm:grid-cols-2" noValidate>
        <Field label="Name" error={formState.errors.name?.message} className="sm:col-span-2">
          <Input {...register('name')} />
        </Field>
        <Field label="Duration (minutes)" error={formState.errors.durationMinutes?.message}>
          <Input inputMode="numeric" {...register('durationMinutes')} />
        </Field>
        <Field label="Price" error={formState.errors.price?.message}>
          <Input inputMode="decimal" {...register('price')} />
        </Field>
        <Field label="Description (optional)" error={formState.errors.description?.message} className="sm:col-span-2">
          <Textarea {...register('description')} />
        </Field>
        <label className="flex items-center gap-2 text-sm sm:col-span-2">
          <input type="checkbox" {...register('active')} /> Visible on the booking page
        </label>

        <div className="sm:col-span-2">
          <ErrorNote message={save.isError ? errorMessage(save.error) : null} />
        </div>
        <div className="flex gap-2 sm:col-span-2">
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save'}
          </Button>
          <Button variant="ghost" onClick={onDone}>
            Cancel
          </Button>
        </div>
      </form>
    </Card>
  )
}
