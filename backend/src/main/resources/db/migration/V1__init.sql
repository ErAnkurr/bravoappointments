-- BravoAppointments v1 schema.
-- Multi-tenancy: every tenant-owned table carries tenant_id. Child rows reference their parents with
-- composite (id, tenant_id) foreign keys, so the database itself rejects a row that points at another
-- tenant's staff/service. The application still scopes every query by tenant_id.

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE tenant (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug          varchar(40)  NOT NULL UNIQUE,
    name          varchar(120) NOT NULL,
    status        varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    timezone      varchar(64)  NOT NULL DEFAULT 'America/Vancouver',
    description   text,
    address       varchar(255),
    phone         varchar(40),
    primary_color varchar(9)   NOT NULL DEFAULT '#111827',
    logo_url      varchar(500),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT tenant_status_chk CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT tenant_slug_chk CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$')
);

-- Managers. Staff logins are out of scope for the MVP, so STORE_MANAGER is the only role for now.
CREATE TABLE app_user (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid         NOT NULL REFERENCES tenant (id),
    auth_provider_id varchar(255) NOT NULL UNIQUE, -- Clerk user id (JWT "sub")
    email            varchar(320),
    role             varchar(20)  NOT NULL DEFAULT 'STORE_MANAGER',
    created_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT app_user_role_chk CHECK (role IN ('STORE_MANAGER'))
);
CREATE INDEX app_user_tenant_idx ON app_user (tenant_id);

CREATE TABLE service (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid         NOT NULL REFERENCES tenant (id),
    name             varchar(120) NOT NULL,
    description      text,
    duration_minutes int          NOT NULL,
    price_cents      int          NOT NULL,
    active           boolean      NOT NULL DEFAULT true,
    sort_order       int          NOT NULL DEFAULT 0,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT service_id_tenant_uq UNIQUE (id, tenant_id),
    CONSTRAINT service_duration_chk CHECK (duration_minutes BETWEEN 5 AND 480),
    CONSTRAINT service_price_chk CHECK (price_cents >= 0)
);
CREATE INDEX service_tenant_idx ON service (tenant_id);

CREATE TABLE staff (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid        NOT NULL REFERENCES tenant (id),
    display_name varchar(80) NOT NULL,
    bio          text,
    photo_url    varchar(500),
    active       boolean     NOT NULL DEFAULT true,
    sort_order   int         NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT staff_id_tenant_uq UNIQUE (id, tenant_id)
);
CREATE INDEX staff_tenant_idx ON staff (tenant_id);

-- Which staff member performs which service.
CREATE TABLE staff_service (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  uuid NOT NULL,
    staff_id   uuid NOT NULL,
    service_id uuid NOT NULL,
    CONSTRAINT staff_service_uq UNIQUE (staff_id, service_id),
    CONSTRAINT staff_service_staff_fk FOREIGN KEY (staff_id, tenant_id)
        REFERENCES staff (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT staff_service_service_fk FOREIGN KEY (service_id, tenant_id)
        REFERENCES service (id, tenant_id) ON DELETE CASCADE
);
CREATE INDEX staff_service_service_idx ON staff_service (service_id);

-- Recurring weekly working windows. day_of_week is ISO: 1 = Monday ... 7 = Sunday.
-- Wall-clock times in the tenant's timezone. A day may have several windows (e.g. a lunch break).
CREATE TABLE working_hours (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid     NOT NULL,
    staff_id    uuid     NOT NULL,
    day_of_week smallint NOT NULL,
    start_time  time     NOT NULL,
    end_time    time     NOT NULL,
    CONSTRAINT working_hours_dow_chk CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT working_hours_order_chk CHECK (end_time > start_time),
    CONSTRAINT working_hours_staff_fk FOREIGN KEY (staff_id, tenant_id)
        REFERENCES staff (id, tenant_id) ON DELETE CASCADE
);
CREATE INDEX working_hours_staff_idx ON working_hours (staff_id);

-- One-off blocked ranges (holidays, appointments outside the system, breaks).
CREATE TABLE time_off (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  uuid        NOT NULL,
    staff_id   uuid        NOT NULL,
    start_at   timestamptz NOT NULL,
    end_at     timestamptz NOT NULL,
    reason     varchar(200),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT time_off_order_chk CHECK (end_at > start_at),
    CONSTRAINT time_off_staff_fk FOREIGN KEY (staff_id, tenant_id)
        REFERENCES staff (id, tenant_id) ON DELETE CASCADE
);
CREATE INDEX time_off_staff_idx ON time_off (staff_id, start_at);

CREATE TABLE appointment (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid         NOT NULL,
    staff_id         uuid         NOT NULL,
    service_id       uuid         NOT NULL,
    service_name     varchar(120) NOT NULL, -- snapshot, so later service edits don't rewrite history
    price_cents      int          NOT NULL, -- snapshot
    customer_name    varchar(120) NOT NULL,
    customer_email   varchar(320) NOT NULL,
    customer_phone   varchar(40)  NOT NULL,
    notes            text,
    start_at         timestamptz  NOT NULL,
    end_at           timestamptz  NOT NULL,
    status           varchar(20)  NOT NULL DEFAULT 'CONFIRMED',
    management_token varchar(64)  NOT NULL UNIQUE, -- guest link: view / cancel without an account
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT appointment_order_chk CHECK (end_at > start_at),
    CONSTRAINT appointment_status_chk CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    CONSTRAINT appointment_staff_fk FOREIGN KEY (staff_id, tenant_id)
        REFERENCES staff (id, tenant_id),
    CONSTRAINT appointment_service_fk FOREIGN KEY (service_id, tenant_id)
        REFERENCES service (id, tenant_id),
    -- Source of truth for double-booking: one staff member cannot hold two overlapping,
    -- non-cancelled appointments, no matter how many requests race.
    CONSTRAINT appointment_no_overlap EXCLUDE USING gist (
        staff_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status <> 'CANCELLED')
);
CREATE INDEX appointment_tenant_start_idx ON appointment (tenant_id, start_at);
CREATE INDEX appointment_staff_start_idx ON appointment (staff_id, start_at);
