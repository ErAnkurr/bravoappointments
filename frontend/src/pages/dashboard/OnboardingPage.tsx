import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useManageApi } from '../../lib/clerk'
import { errorMessage } from '../../lib/api'
import { slugify } from '../../lib/format'
import { Button, Card, ErrorNote, Field, Input, Select, Spinner } from '../../components/ui'

const schema = z.object({
  name: z.string().trim().min(1, 'Enter your business name').max(120),
  slug: z
    .string()
    .regex(/^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$/, 'Use 3-40 characters: lowercase letters, numbers and hyphens'),
  timezone: z.string().min(1),
})
type Values = z.infer<typeof schema>

const TIMEZONES: string[] = Intl.supportedValuesOf('timeZone')
const BROWSER_TIMEZONE = Intl.DateTimeFormat().resolvedOptions().timeZone

export default function OnboardingPage() {
  const api = useManageApi()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const me = useQuery({ queryKey: ['me'], queryFn: api.me, retry: false })
  const [slugEdited, setSlugEdited] = useState(false)

  const { register, handleSubmit, setValue, watch, formState } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: '',
      slug: '',
      timezone: TIMEZONES.includes(BROWSER_TIMEZONE) ? BROWSER_TIMEZONE : 'America/Vancouver',
    },
  })
  const slug = watch('slug')

  const create = useMutation({
    mutationFn: (v: Values) => api.onboard(v),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['me'] })
      navigate('/dashboard', { replace: true })
    },
  })

  if (me.isPending) return <Spinner />
  if (me.data?.onboarded) return <Navigate to="/dashboard" replace />

  const nameField = register('name')

  return (
    <main className="mx-auto max-w-lg px-4 py-12">
      <h1 className="text-2xl font-semibold">Set up your business</h1>
      <p className="mt-1 text-stone-600">This creates your booking page. You can change the details later.</p>

      <Card className="mt-6">
        <form onSubmit={handleSubmit((v) => create.mutate(v))} className="grid gap-4" noValidate>
          <Field label="Business name" error={formState.errors.name?.message}>
            <Input
              {...nameField}
              onChange={(e) => {
                void nameField.onChange(e)
                if (!slugEdited) setValue('slug', slugify(e.target.value), { shouldValidate: formState.isSubmitted })
              }}
            />
          </Field>
          <Field
            label="Booking page address"
            error={formState.errors.slug?.message}
            hint={`Your page will be at ${window.location.origin}/b/${slug || 'your-shop'}`}
          >
            <Input
              {...register('slug', { onChange: () => setSlugEdited(true) })}
              autoCapitalize="none"
              spellCheck={false}
            />
          </Field>
          <Field
            label="Timezone"
            error={formState.errors.timezone?.message}
            hint="Opening hours and appointment times use this timezone."
          >
            <Select {...register('timezone')}>
              {TIMEZONES.map((tz) => (
                <option key={tz} value={tz}>
                  {tz}
                </option>
              ))}
            </Select>
          </Field>

          <ErrorNote message={create.isError ? errorMessage(create.error) : null} />
          <Button type="submit" disabled={create.isPending}>
            {create.isPending ? 'Creating…' : 'Create my booking page'}
          </Button>
        </form>
      </Card>
    </main>
  )
}
