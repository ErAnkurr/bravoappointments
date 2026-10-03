import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useManageApi } from '../../lib/clerk'
import { errorMessage } from '../../lib/api'
import { Button, Card, ErrorNote, Field, Input, Select, Textarea } from '../../components/ui'
import { useDashboard } from './DashboardLayout'

const schema = z.object({
  name: z.string().trim().min(1, 'Enter a name').max(120),
  timezone: z.string().min(1),
  description: z.string().max(2000),
  address: z.string().max(255),
  phone: z.string().max(40),
  primaryColor: z.string().regex(/^#[0-9a-fA-F]{6}$/, 'Use a hex colour like #1a2b3c'),
  logoUrl: z.string().max(500),
})
type Values = z.infer<typeof schema>

const TIMEZONES: string[] = Intl.supportedValuesOf('timeZone')

export default function SettingsPage() {
  const { tenant } = useDashboard()
  const api = useManageApi()
  const queryClient = useQueryClient()
  const [copied, setCopied] = useState(false)

  const { register, handleSubmit, formState, reset, watch } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: tenant.name,
      timezone: tenant.timezone,
      description: tenant.description ?? '',
      address: tenant.address ?? '',
      phone: tenant.phone ?? '',
      primaryColor: tenant.primaryColor,
      logoUrl: tenant.logoUrl ?? '',
    },
  })

  const save = useMutation({
    mutationFn: (v: Values) => api.updateTenant(v),
    onSuccess: async (updated) => {
      await queryClient.invalidateQueries({ queryKey: ['me'] })
      await queryClient.invalidateQueries({ queryKey: ['shop', updated.slug] })
      reset({
        name: updated.name,
        timezone: updated.timezone,
        description: updated.description ?? '',
        address: updated.address ?? '',
        phone: updated.phone ?? '',
        primaryColor: updated.primaryColor,
        logoUrl: updated.logoUrl ?? '',
      })
    },
  })

  const link = `${window.location.origin}/b/${tenant.slug}`
  const color = watch('primaryColor')
  const timezones = TIMEZONES.includes(tenant.timezone) ? TIMEZONES : [tenant.timezone, ...TIMEZONES]

  return (
    <div className="grid max-w-2xl gap-6">
      <div>
        <h1 className="text-xl font-semibold">Settings</h1>
        <Card className="mt-4">
          <div className="text-sm font-medium text-stone-700">Your booking page</div>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <code className="rounded bg-stone-100 px-2 py-1 text-sm">{link}</code>
            <Button
              variant="secondary"
              onClick={async () => {
                await navigator.clipboard.writeText(link)
                setCopied(true)
                setTimeout(() => setCopied(false), 1500)
              }}
            >
              {copied ? 'Copied' : 'Copy link'}
            </Button>
          </div>
          <p className="mt-2 text-xs text-stone-500">Link to this from your own website or Instagram bio.</p>
        </Card>
      </div>

      <Card>
        <form onSubmit={handleSubmit((v) => save.mutate(v))} className="grid gap-4 sm:grid-cols-2" noValidate>
          <Field label="Business name" error={formState.errors.name?.message} className="sm:col-span-2">
            <Input {...register('name')} />
          </Field>
          <Field label="Timezone" error={formState.errors.timezone?.message} className="sm:col-span-2">
            <Select {...register('timezone')}>
              {timezones.map((tz) => (
                <option key={tz} value={tz}>
                  {tz}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Description" error={formState.errors.description?.message} className="sm:col-span-2">
            <Textarea {...register('description')} />
          </Field>
          <Field label="Address" error={formState.errors.address?.message}>
            <Input {...register('address')} />
          </Field>
          <Field label="Phone" error={formState.errors.phone?.message}>
            <Input type="tel" {...register('phone')} />
          </Field>
          <Field label="Brand colour" error={formState.errors.primaryColor?.message}>
            <div className="flex items-center gap-2">
              <span className="h-9 w-9 shrink-0 rounded-lg border border-stone-300" style={{ backgroundColor: color }} />
              <Input {...register('primaryColor')} />
            </div>
          </Field>
          <Field label="Logo URL (optional)" error={formState.errors.logoUrl?.message}>
            <Input type="url" {...register('logoUrl')} />
          </Field>

          <div className="sm:col-span-2">
            <ErrorNote message={save.isError ? errorMessage(save.error) : null} />
          </div>
          <div className="flex items-center gap-3 sm:col-span-2">
            <Button type="submit" disabled={save.isPending || !formState.isDirty}>
              {save.isPending ? 'Saving…' : 'Save changes'}
            </Button>
            {save.isSuccess && !formState.isDirty && <span className="text-sm text-emerald-700">Saved</span>}
          </div>
        </form>
      </Card>
    </div>
  )
}
