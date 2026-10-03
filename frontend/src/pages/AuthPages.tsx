import type { ReactNode } from 'react'
import { SignIn, SignUp } from '@clerk/clerk-react'
import { ClerkMissing, hasClerk } from '../lib/clerk'

function Centered({ children }: { children: ReactNode }) {
  return <main className="flex min-h-screen items-center justify-center p-4">{children}</main>
}

export function SignInPage() {
  if (!hasClerk) return <ClerkMissing />
  return (
    <Centered>
      <SignIn routing="path" path="/sign-in" signUpUrl="/sign-up" fallbackRedirectUrl="/dashboard" />
    </Centered>
  )
}

export function SignUpPage() {
  if (!hasClerk) return <ClerkMissing />
  return (
    <Centered>
      <SignUp routing="path" path="/sign-up" signInUrl="/sign-in" fallbackRedirectUrl="/onboarding" />
    </Centered>
  )
}
