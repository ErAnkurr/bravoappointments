import { Route, Routes } from 'react-router'
import { Protected } from './lib/clerk'
import LandingPage from './pages/LandingPage'
import NotFoundPage from './pages/NotFoundPage'
import { SignInPage, SignUpPage } from './pages/AuthPages'
import BookingPage from './pages/public/BookingPage'
import ConfirmationPage from './pages/public/ConfirmationPage'
import OnboardingPage from './pages/dashboard/OnboardingPage'
import DashboardLayout from './pages/dashboard/DashboardLayout'
import AppointmentsPage from './pages/dashboard/AppointmentsPage'
import ServicesPage from './pages/dashboard/ServicesPage'
import StaffPage from './pages/dashboard/StaffPage'
import SettingsPage from './pages/dashboard/SettingsPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/sign-in/*" element={<SignInPage />} />
      <Route path="/sign-up/*" element={<SignUpPage />} />

      {/* Public, guest-facing */}
      <Route path="/b/:slug" element={<BookingPage />} />
      <Route path="/b/:slug/booking/:token" element={<ConfirmationPage />} />

      {/* Manager */}
      <Route
        path="/onboarding"
        element={
          <Protected>
            <OnboardingPage />
          </Protected>
        }
      />
      <Route
        path="/dashboard"
        element={
          <Protected>
            <DashboardLayout />
          </Protected>
        }
      >
        <Route index element={<AppointmentsPage />} />
        <Route path="services" element={<ServicesPage />} />
        <Route path="staff" element={<StaffPage />} />
        <Route path="settings" element={<SettingsPage />} />
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
