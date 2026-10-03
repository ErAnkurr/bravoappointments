import { Link } from 'react-router'
import { Card } from '../components/ui'

export default function LandingPage() {
  return (
    <main className="mx-auto max-w-2xl px-4 py-16">
      <h1 className="text-3xl font-semibold tracking-tight">BravoAppointments</h1>
      <p className="mt-3 text-stone-600">
        Online booking for barber shops. Each business gets its own booking page; its manager runs the calendar from
        the dashboard.
      </p>

      <div className="mt-8 grid gap-4 sm:grid-cols-2">
        <Card>
          <h2 className="font-medium">I run a shop</h2>
          <p className="mt-1 text-sm text-stone-600">Set up services, barbers and hours, and manage bookings.</p>
          <Link to="/dashboard" className="mt-4 inline-block text-sm font-medium text-stone-900 underline">
            Open the dashboard
          </Link>
        </Card>
        <Card>
          <h2 className="font-medium">See a booking page</h2>
          <p className="mt-1 text-sm text-stone-600">The demo shop only exists when the API runs with the dev profile.</p>
          <Link to="/b/demo-barbers" className="mt-4 inline-block text-sm font-medium text-stone-900 underline">
            Demo Barbers
          </Link>
        </Card>
      </div>
    </main>
  )
}
