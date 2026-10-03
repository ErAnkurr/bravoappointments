-- DEV ONLY. Loaded only when the "dev" Spring profile is active (see application-dev.yml).
-- Gives you a bookable demo shop at /b/demo-barbers without needing Clerk.

INSERT INTO tenant (id, slug, name, timezone, description, address, phone, primary_color)
VALUES ('11111111-1111-4111-8111-111111111111', 'demo-barbers', 'Demo Barbers', 'America/Vancouver',
        'Classic cuts and clean shaves. Walk-ins welcome, appointments preferred.',
        '123 Main St, Vancouver, BC', '604-555-0100', '#b45309');

INSERT INTO service (id, tenant_id, name, description, duration_minutes, price_cents, sort_order) VALUES
    ('21111111-1111-4111-8111-111111111111', '11111111-1111-4111-8111-111111111111', 'Haircut', 'Wash, cut and style.', 30, 3500, 1),
    ('22222222-2222-4222-8222-222222222222', '11111111-1111-4111-8111-111111111111', 'Beard trim', 'Shape-up with hot towel.', 20, 2000, 2),
    ('23333333-3333-4333-8333-333333333333', '11111111-1111-4111-8111-111111111111', 'Cut + beard', 'The full treatment.', 45, 5000, 3);

INSERT INTO staff (id, tenant_id, display_name, bio, sort_order) VALUES
    ('31111111-1111-4111-8111-111111111111', '11111111-1111-4111-8111-111111111111', 'Alex', 'Fades and classic cuts, 10 years behind the chair.', 1),
    ('32222222-2222-4222-8222-222222222222', '11111111-1111-4111-8111-111111111111', 'Sam', 'Beard specialist.', 2);

INSERT INTO staff_service (tenant_id, staff_id, service_id) VALUES
    ('11111111-1111-4111-8111-111111111111', '31111111-1111-4111-8111-111111111111', '21111111-1111-4111-8111-111111111111'),
    ('11111111-1111-4111-8111-111111111111', '31111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222'),
    ('11111111-1111-4111-8111-111111111111', '31111111-1111-4111-8111-111111111111', '23333333-3333-4333-8333-333333333333'),
    ('11111111-1111-4111-8111-111111111111', '32222222-2222-4222-8222-222222222222', '21111111-1111-4111-8111-111111111111'),
    ('11111111-1111-4111-8111-111111111111', '32222222-2222-4222-8222-222222222222', '22222222-2222-4222-8222-222222222222');

-- Alex: Tue-Sat, 09:00-12:30 and 13:30-17:00.
INSERT INTO working_hours (tenant_id, staff_id, day_of_week, start_time, end_time)
SELECT '11111111-1111-4111-8111-111111111111', '31111111-1111-4111-8111-111111111111', d, t.s, t.e
FROM generate_series(2, 6) AS d,
     (VALUES ('09:00'::time, '12:30'::time), ('13:30'::time, '17:00'::time)) AS t (s, e);

-- Sam: Wed-Sun, 10:00-18:00.
INSERT INTO working_hours (tenant_id, staff_id, day_of_week, start_time, end_time)
SELECT '11111111-1111-4111-8111-111111111111', '32222222-2222-4222-8222-222222222222', d, '10:00', '18:00'
FROM generate_series(3, 7) AS d;
