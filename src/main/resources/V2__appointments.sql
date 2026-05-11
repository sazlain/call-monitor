-- Migración: Módulo de citas
-- Ejecutar en la BD de producción

-- 1. Nueva tabla appointments
CREATE TABLE IF NOT EXISTS appointments (
    id                BIGSERIAL PRIMARY KEY,
    lead_id           BIGINT NOT NULL REFERENCES leads(id),
    agent_id          BIGINT NOT NULL REFERENCES agents(id),
    call_id           VARCHAR(100),
    appointment_date  DATE NOT NULL,
    appointment_time  TIME,
    address           VARCHAR(300),
    attendees         INTEGER,
    notes             TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Nuevo estado APPOINTMENT en leads (ya es VARCHAR, solo documentar)
-- El enum LeadStatus ahora incluye: NEW, PENDING, CALLED, CONTACTED,
-- INTERESTED, APPOINTMENT, CONVERTED, CALLBACK, DISCARDED

-- 3. Nuevos CallResult (ya es VARCHAR, solo documentar)
-- APPOINTMENT, APPOINTMENT_RESCHEDULE, APPOINTMENT_CANCEL
