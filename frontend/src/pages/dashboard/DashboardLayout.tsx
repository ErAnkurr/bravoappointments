import { Navigate, NavLink, Outlet, useOutletContext } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { UserButton } from '@clerk/clerk-react'
import { useManageApi } from '../../lib/clerk'
import { errorMessage } from '../../lib/api'
import type { Tenant } from '../../lib/types'
import { ErrorNote, Spinner, cn } from '../../components/ui'

export interface DashboardContext {
  tenant: Tenant
}

/** Pages under /dashboard read the signed-in business from here. */
export function useDashboard(): DashboardContext {
  return useOutletContext<DashboardContext>()
}

const NAV = [
  { to: '/dashboard', label: 'Appointments', end: true },
  { to: '/dashboard/services', label: 'Services', end: false },
  { to: '/dashboard/staff', label: 'Barbers', end: false },
  { to: '/dashboard/settings', label: 'Settings', end: false },
]

export default function DashboardLayout() {
  const api = useManageApi()
  const me = useQuery({ queryKey: ['me'], queryFn: api.me, retry: false })

  if (me.isPending) {
    return (
      <main className="mx-auto max-w-5xl px-4">
        <Spinner />
      </main>
    )
  }
  if (me.isError) {
    return (
      <main className="mx-auto max-w-md px-4 py-16">
        <ErrorNote message={errorMessage(me.error)} />
      </main>
    )
  }
  if (!me.data.onboarded || !me.data.tenant) {
    return <Navigate to="/onboarding" replace />
  }

  const tenant = me.data.tenant
  const context: DashboardContext = { tenant }

  return (
    <div className="min-h-screen">
      <header className="border-b border-stone-200 bg-white">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div className="font-semibold">{tenant.name}</div>
          <nav className="flex gap-1" aria-label="Dashboard">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  cn(
                    'rounded-lg px-3 py-1.5 text-sm font-medium',
                    isActive ? 'bg-stone-900 text-white' : 'text-stone-700 hover:bg-stone-100',
                  )
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
          <UserButton />
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-6">
        <Outlet context={context} />
      </main>
    </div>
  )
}
