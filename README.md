# BravoAppointments

Multi-tenant appointment booking for barber shops. Every business gets a hosted booking page at `/b/<slug>`.
Guests pick a service, a barber (or "any available") and a time, and no account is needed. The business manager
runs services, barbers, working hours and the calendar from a dashboard.

- **Backend:** Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL
- **Frontend:** React 19, Vite, React Router, TanStack Query, React Hook Form + Zod, Tailwind CSS
- **Manager auth:** [Clerk](https://clerk.com) (managers only; guests never sign in)
- **Email:** [Resend](https://resend.com), or logged to the console when no API key is set

> **Status: early MVP, work in progress.** See [Project status](#project-status) for what has and hasn't been
> verified before you rely on anything here.

## Contents

1. [What's in the MVP](#whats-in-the-mvp)
2. [Project status](#project-status)
3. [Repository layout](#repository-layout)
4. [Getting started](#getting-started)
5. [Configuration](#configuration)
6. [Manager dashboard and Clerk](#manager-dashboard-and-clerk)
7. [How it works](#how-it-works)
8. [Data model](#data-model)
9. [API reference](#api-reference)
10. [Frontend routes](#frontend-routes)
11. [Testing](#testing)
12. [Docker and deployment](#docker-and-deployment)
13. [Known gaps and roadmap](#known-gaps-and-roadmap)
14. [Development workflow](#development-workflow)

## What's in the MVP

**Guest booking page** (`/b/<slug>`, no login)
- Business profile, service list with duration and price, barber list.
- Pick a service, then a barber or "any available", then a date and time.
- Confirmation page and confirmation email. A secret link lets the guest view and cancel the booking.

**Manager dashboard** (`/dashboard`, Clerk sign-in)
- One-time business setup: name, URL slug, timezone.
- Appointments: list and filter by date range and barber, change status, reassign a barber.
- Services: create, edit, deactivate.
- Barbers: create, edit, deactivate; choose which services each one performs; weekly working hours; time off.
- Settings: business details, brand colour (the booking page is themed with it), logo URL.

**Deliberately not in the MVP:** staff logins, manager-created or rescheduled appointments, payments,
SMS, portfolio uploads, analytics, custom domains, per-business currency. See
[Known gaps and roadmap](#known-gaps-and-roadmap).

## Project status

Read this before trusting the rest of the README.

| Area | State |
| --- | --- |
| Database schema and dev seed | Tested on real PostgreSQL 16: overlapping bookings are rejected, cancelled slots are reusable, cross-tenant references are rejected, and a 20-way concurrent booking race produced exactly one winner. |
| Backend build and boot | Compiles with JDK 21 and Maven, boots against PostgreSQL 16, and Flyway applies the migration (confirmed by running it locally). |
| Backend endpoints | **Not yet exercised end to end against the real backend.** The guest flow was only tested against a mock API. |
| Slot calculator | Unit tests written (9, including DST transition days). Run `mvn test` to confirm on your machine. |
| Frontend | Strict typecheck and production build pass. The guest booking flow, conflict handling, confirmation and cancel were exercised in Chromium against a mock API. |
| Clerk sign-in, manager dashboard UI | **Never run.** |
| Resend email delivery | **Never run.** Without a key, emails are written to the API log. |
| Docker image, `docker compose --profile full` | **Never run.** |
| API integration tests, frontend tests, ESLint | **Not written.** Testcontainers is the planned next step. |

## Repository layout

```
.
├── backend/                         Spring Boot API
│   ├── pom.xml
│   ├── Dockerfile                   Multi-stage build (Maven -> JRE 21)
│   └── src/
│       ├── main/java/com/bravoappointments/
│       │   ├── BravoApplication.java
│       │   ├── config/              Properties, security (Clerk JWT, CORS), clock
│       │   ├── common/              ApiException, GlobalExceptionHandler (error format)
│       │   ├── tenant/              Tenant, AppUser (manager), per-tenant advisory lock
│       │   ├── catalog/             Services, staff, staff<->service links
│       │   ├── scheduling/          SlotCalculator (pure Java), working hours, time off, availability
│       │   ├── appointment/         Appointment entity, BookingService (book / cancel), events
│       │   ├── notification/        EmailSender (logging or Resend), after-commit listener
│       │   ├── publicapi/           Guest endpoints under /api/public
│       │   └── manage/              Manager endpoints under /api/manage
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-dev.yml  Adds the demo seed to Flyway (dev profile only)
│       │   └── db/
│       │       ├── migration/V1__init.sql
│       │       └── dev/V1000__dev_seed.sql
│       └── test/java/.../scheduling/SlotCalculatorTest.java
├── frontend/                        React app (Vite)
│   └── src/
│       ├── App.tsx                  Routes
│       ├── main.tsx                 Providers (Router, React Query, Clerk)
│       ├── lib/                     api client, types, formatting, Clerk wiring
│       ├── components/ui.tsx        Small shared UI kit
│       └── pages/
│           ├── public/              BookingPage, ConfirmationPage
│           └── dashboard/           Layout, Onboarding, Appointments, Services, Staff, Settings
├── docker-compose.yml               Postgres 16 (+ optional API container)
├── .env.example                     Variables the API reads
└── README.md
```

## Getting started

### Prerequisites

- **JDK 21** and **Maven 3.9+** (there is no Maven wrapper yet; `mvn -N wrapper:wrapper` in `backend/` adds one)
- **Node 20+**
- **PostgreSQL 16**, either via Docker or installed locally

On macOS with Homebrew:

```bash
brew install --cask temurin@21
brew install maven
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # add to ~/.zshrc to keep it
java -version && mvn -v                            # both should report 21
```

Set `JAVA_HOME` explicitly. `brew install maven` can pull in a newer JDK as a dependency, and Maven would then
use that one. The project targets Java 21.

### 1. Database

**Option A: Docker**

```bash
docker compose up -d db
```

**Option B: local Postgres (no Docker)**

```bash
brew install postgresql@16 && brew services start postgresql@16
psql postgres -c "create role bravo login password 'bravo'"
createdb -O bravo bravo
```

Either way the API's defaults connect to `localhost:5432`, database `bravo`, user `bravo`, password `bravo`.
The first migration runs `CREATE EXTENSION btree_gist` as that user, which works for a database owner on
PostgreSQL 13 and later.

### 2. API

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

The `dev` profile also loads a demo business, `demo-barbers`, with services and two barbers. **Never use the
`dev` profile against a real database.**

Success looks like `Started BravoApplication` in the log. Check it:

```bash
# The demo shop: services and barbers
curl -s localhost:8080/api/public/demo-barbers

# Free slots for one day (take a service id from the response above)
curl -s "localhost:8080/api/public/demo-barbers/availability?serviceId=<SERVICE_ID>&from=2026-10-05&to=2026-10-05"

# Manager endpoints refuse requests without a token (expect 401)
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/api/manage/me
```

### 3. Web app

```bash
cd frontend
cp .env.example .env     # optional until you set up Clerk
npm install
npm run dev
```

Open <http://localhost:5173/b/demo-barbers> and book an appointment. No Clerk account is needed for guest
booking. The confirmation email appears in the API log.

## Configuration

### API environment variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `PORT` | `8080` | HTTP port. |
| `SPRING_PROFILES_ACTIVE` | none | `dev` loads the demo seed. Local development only. |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/bravo` | Runtime connection (use the pooled URL in production). |
| `DATABASE_DIRECT_URL` | falls back to `DATABASE_URL` | Direct, non-pooled connection used by Flyway. |
| `DATABASE_USER` | `bravo` | Database user (used for both connections). |
| `DATABASE_PASSWORD` | `bravo` | Database password (used for both connections). The default is for local development only; always set your own in production. |
| `PUBLIC_BASE_URL` | `http://localhost:5173` | Where the web app is served; used in email links. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated origins allowed to call the API. |
| `CLERK_ISSUER` | blank | Clerk Frontend API URL, e.g. `https://your-app.clerk.accounts.dev`. |
| `CLERK_JWKS_URL` | blank | `<issuer>/.well-known/jwks.json`. |
| `RESEND_API_KEY` | blank | Blank means emails are logged, not sent. |
| `EMAIL_FROM` | `Bravo Appointments <onboarding@resend.dev>` | Sender address. |

Booking rules live under `bravo.booking` in `application.yml`: a 15-minute slot grid, 60 minutes minimum notice,
and bookings up to 60 days ahead. Spring's relaxed binding lets you override them with environment variables
such as `BRAVO_BOOKING_MIN_NOTICE_MINUTES`. They are global for now, not per business.

If `CLERK_ISSUER` or `CLERK_JWKS_URL` is blank, guest booking still works and every manager endpoint returns 401.

### Web app environment variables (`frontend/.env`)

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_CLERK_PUBLISHABLE_KEY` | blank | Clerk publishable key. Without it the dashboard shows a "Clerk is not configured" notice. |
| `VITE_API_URL` | `/api` | Leave blank in dev (Vite proxies `/api` to `localhost:8080`). In production set it to the API's public URL including `/api`. |
| `VITE_CURRENCY` | `CAD` | ISO 4217 code used to display prices. |

## Manager dashboard and Clerk

1. Create a Clerk application. Put the publishable key in `frontend/.env` as `VITE_CLERK_PUBLISHABLE_KEY`.
2. Give the API the issuer and JWKS URL, then restart it:
   ```bash
   export CLERK_ISSUER=https://<your-app>.clerk.accounts.dev
   export CLERK_JWKS_URL=$CLERK_ISSUER/.well-known/jwks.json
   ```
3. Visit <http://localhost:5173/dashboard>, sign up, and complete the one-time business setup.

**Restrict sign-ups in Clerk (allowlist or invite-only) before deploying.** Any signed-in user can currently
create a business, because onboarding is open to anyone with a valid Clerk token.

The `app_user.email` column is filled only if your Clerk session token includes an `email` claim. It is nullable
and nothing depends on it yet.

## How it works

### Multi-tenancy

A tenant is one business. Every tenant-owned table has `tenant_id`, and child rows reference their parents with
composite `(id, tenant_id)` foreign keys, so the database itself rejects a booking that points at another
business's barber or service. Application code also scopes every query by tenant.

- **Guests:** the tenant comes from the URL slug (`/b/<slug>`) or from the booking token.
- **Managers:** the tenant is looked up server-side from the verified Clerk user id (the JWT `sub` claim) in the
  `app_user` table. It is never read from the token's other claims or from the request.

### Double-booking prevention

Two layers:

1. **Source of truth: a Postgres exclusion constraint**, `appointment_no_overlap`. It uses `btree_gist` and
   `tstzrange(start_at, end_at, '[)')` and applies `WHERE status <> 'CANCELLED'`, so one barber cannot hold two
   overlapping non-cancelled appointments no matter how many requests race. Cancelled slots become free again.
2. **A per-tenant advisory lock** (`pg_advisory_xact_lock`) taken inside the booking transaction, so that
   "validate the slot, pick a barber, insert" runs one booking at a time per business. It is transaction-scoped,
   so it is safe behind PgBouncer in transaction mode.

A request that loses a race gets `409 SLOT_UNAVAILABLE`.

### Slot calculation

`SlotCalculator` is pure Java with no Spring dependency, so it is easy to unit test.

- Working hours are **wall-clock times in the business's timezone**, and each day can have several windows
  (for example a lunch break).
- Each window is converted to instants in the business's timezone, so DST transition days work.
- Slots start on a 15-minute grid anchored at the window start. A slot must fit entirely inside the window,
  must not overlap time off or a non-cancelled appointment, and must respect the minimum notice.
- Ranges are half-open, so back-to-back appointments are fine.

### "Any available"

When a guest doesn't choose a barber, availability is the union of everyone's free slots. At booking time the
server assigns the free barber with the **fewest appointments that day**, with ties broken by sort order.

### Time handling

Appointments are stored as `timestamptz` (UTC instants). The API returns ISO-8601 instants plus the business's
timezone name. **The UI always formats times in the business's timezone**, not the visitor's, because "3 pm"
means 3 pm at the shop.

### Guest bookings

There are no guest accounts. Each booking has a `management_token` (32 random bytes, base64url). The link in the
confirmation email, `/b/<slug>/booking/<token>`, lets the guest view and cancel that booking. Treat the token as a
secret.

### Email

Confirmation and cancellation emails are sent asynchronously after the booking transaction commits
(`@TransactionalEventListener(AFTER_COMMIT)` plus `@Async`), so a mail failure never rolls back a booking.
`EmailSender` has two implementations: `LoggingEmailSender` (default) and `ResendEmailSender` (used when
`RESEND_API_KEY` is set).

### Auth and security

- `/api/public/**`, `/actuator/health` and `/error` are open.
- `/api/manage/**` requires a valid Clerk session token (verified against Clerk's JWKS, issuer checked).
- Everything else is denied. The API is stateless, and CSRF is disabled because it uses bearer tokens, not cookies.
- CORS is restricted to `CORS_ALLOWED_ORIGINS`.

### Errors

Errors raised by the API's own code (including validation) use one shape:

```json
{ "code": "VALIDATION_FAILED", "message": "Some fields are invalid", "fields": { "customerEmail": "…" } }
```

`fields` (field name to message) appears only on validation errors. Codes you will see include
`VALIDATION_FAILED`, `MALFORMED_REQUEST`, `INVALID_PARAMETER`, `INVALID_RANGE`, `RANGE_TOO_LARGE`,
`SLOT_UNAVAILABLE` (409, the slot was just taken), `CONFLICT` (409, a database constraint was hit),
`HTTP_<status>` for Spring's own errors such as an unknown route, and `INTERNAL_ERROR` (500).
An unknown business, service or booking returns `404`.

The exception is authentication: a missing or invalid token on `/api/manage/**` is rejected by Spring Security
with a plain `401` before the request reaches the API, so that response does not use this body.

## Data model

Defined in `backend/src/main/resources/db/migration/V1__init.sql`.

| Table | Purpose |
| --- | --- |
| `tenant` | A business: slug, name, status, timezone, profile, brand colour, logo. |
| `app_user` | A manager, linked to a tenant by Clerk user id. Role is `STORE_MANAGER` only for now. |
| `service` | A bookable service: duration (5-480 min), price in cents, active flag, sort order. |
| `staff` | A barber: display name, bio, photo URL, active flag, sort order. |
| `staff_service` | Which barber performs which service. |
| `working_hours` | Weekly windows per barber. `day_of_week` is ISO (1 = Monday ... 7 = Sunday). |
| `time_off` | One-off blocked ranges per barber. |
| `appointment` | A booking, with a snapshot of service name and price, customer details, status and guest token. |

Appointment status is one of `CONFIRMED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`.

Schema changes go in new `V<n>__description.sql` files. Never edit a migration that has been applied anywhere.
The dev seed is `db/dev/V1000__dev_seed.sql` and only runs under the `dev` profile.

## API reference

Base path `/api`. Times are ISO-8601 instants (UTC). Dates are `YYYY-MM-DD` in the business's timezone.

### Public (no authentication)

| Method and path | Description |
| --- | --- |
| `GET /public/{slug}` | Business profile, active services, active barbers (with the service ids each performs). |
| `GET /public/{slug}/availability?serviceId=&staffId=&from=&to=` | Free slots. `staffId` is optional (omit for "any available"). At most 14 days per request. |
| `POST /public/{slug}/appointments` | Book. Returns `201` with the booking. |
| `GET /public/bookings/{token}` | View a booking by its guest token. |
| `POST /public/bookings/{token}/cancel` | Cancel a booking by its guest token. |

Availability response:

```json
{
  "timezone": "America/Vancouver",
  "days": [{ "date": "2026-10-05", "slots": ["2026-10-05T16:00:00Z", "2026-10-05T16:15:00Z"] }]
}
```

Book request (omit `staffId` for "any available"):

```json
{
  "serviceId": "…",
  "staffId": null,
  "startAt": "2026-10-05T16:00:00Z",
  "customerName": "Jane Doe",
  "customerEmail": "jane@example.com",
  "customerPhone": "604-555-0100",
  "notes": "Optional"
}
```

The booking response includes the token, status, business details, service, barber, start and end, and customer
name and email.

### Manager (Clerk bearer token: `Authorization: Bearer <token>`)

| Method and path | Description |
| --- | --- |
| `GET /manage/me` | `{ onboarded, tenant }`. Safe to call before onboarding. |
| `POST /manage/onboarding` | Create the business: `name`, `slug` (3-40 chars: lowercase letters, numbers, hyphens), `timezone`. |
| `GET /manage/tenant`, `PUT /manage/tenant` | Read or update business details, brand colour (`#rrggbb`), logo URL. |
| `GET /manage/services`, `POST /manage/services`, `PUT /manage/services/{id}` | List, create, update. Deactivate with `active: false`; there is no delete. |
| `GET /manage/staff`, `POST /manage/staff`, `PUT /manage/staff/{id}` | List, create, update (including `serviceIds`). |
| `GET /manage/staff/{id}/hours`, `PUT /manage/staff/{id}/hours` | Read or **replace** weekly windows: `[{ dayOfWeek, startTime, endTime }]`. |
| `GET /manage/staff/{id}/time-off`, `POST ...`, `DELETE /manage/staff/{id}/time-off/{timeOffId}` | Time off. Without times, `startDate` to `endDate` (inclusive) blocks whole days. |
| `GET /manage/appointments?from=&to=&staffId=` | List appointments. All filters optional. |
| `PATCH /manage/appointments/{id}` | Change `status`, reassign `staffId`, or both. |

## Frontend routes

| Route | Page |
| --- | --- |
| `/` | Landing page |
| `/b/:slug` | Guest booking page |
| `/b/:slug/booking/:token` | Booking confirmation, with cancel |
| `/sign-in`, `/sign-up` | Clerk sign-in and sign-up |
| `/onboarding` | One-time business setup (signed in) |
| `/dashboard` | Appointments (signed in) |
| `/dashboard/services`, `/dashboard/staff`, `/dashboard/settings` | Manager pages |

## Testing

```bash
cd backend && mvn test          # SlotCalculator unit tests, including DST days
cd frontend && npm run build    # strict typecheck + production build
cd frontend && npm run typecheck
```

Planned next: Testcontainers integration tests for the booking race, tenant isolation and the cancel token, then
frontend tests and ESLint.

## Docker and deployment

`backend/Dockerfile` builds the API in a multi-stage image (Maven, then a JRE 21 runtime). For a local
all-in-one run, which also uses the `dev` profile and the demo seed:

```bash
docker compose --profile full up --build
```

**Production notes**

- Host: not decided yet. The API is a plain container listening on `PORT`.
- Planned database: PostgreSQL on Supabase, used as plain Postgres. Use two connection strings:
  `DATABASE_URL` (pooled, transaction mode, with `?prepareThreshold=0`) for the running app and
  `DATABASE_DIRECT_URL` (direct) for Flyway. Mixing them up is the classic failure with this setup.
- **Do not set `SPRING_PROFILES_ACTIVE=dev` in production.**
- Set `CLERK_ISSUER`, `CLERK_JWKS_URL`, `RESEND_API_KEY`, `EMAIL_FROM`, `PUBLIC_BASE_URL` and
  `CORS_ALLOWED_ORIGINS` (your real web origin).
- Build the web app with `VITE_API_URL` pointing at the API, and serve `frontend/dist` as a single-page app with a
  fallback to `index.html` so `/b/<slug>` deep links work.

## Known gaps and roadmap

**Before going public**
- Rate limiting or bot protection on the public booking endpoint.
- Restrict Clerk sign-ups (see [above](#manager-dashboard-and-clerk)).
- The single `DATABASE_USER` / `DATABASE_PASSWORD` is used for both the runtime and Flyway connections. Supabase's
  pooler and direct connections typically use different usernames, so separate Flyway credentials will probably be
  needed. Check this before deploying there.
- Error tracking (Sentry is planned but not wired in).

**Product**
- Manager-created and rescheduled appointments.
- Staff logins and dashboards.
- Portfolio and Instagram showcase, analytics, SMS, payments.
- Per-business currency (prices currently display in `VITE_CURRENCY`).
- Custom domains (booking pages are path-based, `/b/<slug>`, by design for now).

**Engineering**
- Testcontainers API tests, frontend tests, ESLint, CI.
- Maven wrapper.

## Development workflow

- Work on a branch and open a pull request into `main`.
- Before opening a PR: `cd backend && mvn test` and `cd frontend && npm run build`.
- Database changes are new Flyway migrations, never edits to an applied one.
- Keep tenant scoping on every query that touches tenant-owned data.

## License

No license has been chosen yet, so all rights are reserved by default.
