import { useMemo } from 'react'
import type { ReactNode } from 'react'
import { RedirectToSignIn, SignedIn, SignedOut, useAuth } from '@clerk/clerk-react'
import { manageApi } from './api'

export const clerkPublishableKey: string | undefined = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY || undefined
export const hasClerk = Boolean(clerkPublishableKey)

/** Manager API client bound to the signed-in user's Clerk session token. Only call inside <ClerkProvider>. */
export function useManageApi() {
  const { getToken } = useAuth()
  return useMemo(() => manageApi(() => getToken()), [getToken])
}

export function ClerkMissing() {
  return (
    <div className="mx-auto max-w-md p-8 text-center">
      <h1 className="text-xl font-semibold">Manager sign-in isn&apos;t configured</h1>
      <p className="mt-2 text-stone-600">
        Set <code className="rounded bg-stone-200 px-1">VITE_CLERK_PUBLISHABLE_KEY</code> in{' '}
        <code className="rounded bg-stone-200 px-1">frontend/.env</code> and restart the dev server. Public booking
        pages work without it.
      </p>
    </div>
  )
}

/** Renders children only for signed-in users; everyone else is sent to sign in. */
export function Protected({ children }: { children: ReactNode }) {
  if (!hasClerk) return <ClerkMissing />
  return (
    <>
      <SignedIn>{children}</SignedIn>
      <SignedOut>
        <RedirectToSignIn />
      </SignedOut>
    </>
  )
}
