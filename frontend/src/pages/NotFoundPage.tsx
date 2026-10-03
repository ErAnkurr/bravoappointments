import { Link } from 'react-router'

export default function NotFoundPage() {
  return (
    <main className="mx-auto max-w-md px-4 py-20 text-center">
      <h1 className="text-2xl font-semibold">Page not found</h1>
      <p className="mt-2 text-stone-600">That address doesn&apos;t exist.</p>
      <Link to="/" className="mt-6 inline-block text-sm font-medium underline">
        Back home
      </Link>
    </main>
  )
}
