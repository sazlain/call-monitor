-- ============================================================
-- CALL MONITOR — Script de inicializacion de base de datos
-- Spring Boot: spring.jpa.hibernate.ddl-auto=update
-- Este script solo crea indices y datos iniciales.
-- Las tablas las crea Hibernate automaticamente.
-- ============================================================

-- ── Indices para call_events ─────────────────────────────────────────────────
-- Indice principal para busquedas por extension y fecha (queries de stats)
CREATE INDEX IF NOT EXISTS idx_call_events_ext_date
    ON call_events (caller_extension, created_at);

-- Indice para agrupar el flujo de una llamada por callId
CREATE INDEX IF NOT EXISTS idx_call_events_call_id
    ON call_events (call_id);

-- Indice para busquedas por estado (CALLING, ANSWER, HANGUP, etc.)
CREATE INDEX IF NOT EXISTS idx_call_events_status
    ON call_events (call_status, created_at);

-- Indice para busquedas de llamadas activas en tiempo real
CREATE INDEX IF NOT EXISTS idx_call_events_ext_status
    ON call_events (caller_extension, call_status);

-- ── Indices para users ───────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_users_email
    ON users (email);

CREATE INDEX IF NOT EXISTS idx_users_active
    ON users (active);

-- ── Indices para user_roles ──────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_user_roles_user_id
    ON user_roles (user_id);

CREATE INDEX IF NOT EXISTS idx_user_roles_role
    ON user_roles (role);

-- ── Indices para agents ──────────────────────────────────────────────────────
-- Busqueda por extension (vinculo con call_events)
CREATE INDEX IF NOT EXISTS idx_agents_extension
    ON agents (extension);

CREATE INDEX IF NOT EXISTS idx_agents_user_id
    ON agents (user_id);

CREATE INDEX IF NOT EXISTS idx_agents_group_id
    ON agents (group_id);

-- ── Indices para agent_groups ────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_agent_groups_admin_id
    ON agent_groups (admin_id);

-- ── Indices para leads ───────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_leads_owner_id
    ON leads (owner_id);

CREATE INDEX IF NOT EXISTS idx_leads_assigned_agent_id
    ON leads (assigned_agent_id);

CREATE INDEX IF NOT EXISTS idx_leads_status
    ON leads (status);

-- Indice para callbacks por fecha
CREATE INDEX IF NOT EXISTS idx_leads_callback_date
    ON leads (callback_date, status);

-- Indice para busquedas por periodo de carga
CREATE INDEX IF NOT EXISTS idx_leads_lead_date
    ON leads (lead_date, owner_id);

-- ── Indices para call_typifications ─────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_call_typifications_call_id
    ON call_typifications (call_id);

CREATE INDEX IF NOT EXISTS idx_call_typifications_agent_id
    ON call_typifications (agent_id, created_at);

CREATE INDEX IF NOT EXISTS idx_call_typifications_lead_id
    ON call_typifications (lead_id);

CREATE INDEX IF NOT EXISTS idx_call_typifications_result
    ON call_typifications (result);

-- ── Métodos de pago iniciales ────────────────────────────────────────────────
INSERT INTO payment_methods (name, details, active)
SELECT 'Transferencia bancaria',
       'Banco: [Nombre del banco] — Cuenta: [Número de cuenta] — Titular: [Nombre del titular]',
       true
WHERE NOT EXISTS (SELECT 1 FROM payment_methods WHERE name = 'Transferencia bancaria');

INSERT INTO payment_methods (name, details, active)
SELECT 'Nequi',
       'Número Nequi: [300-XXX-XXXX] — Titular: [Nombre del titular]',
       true
WHERE NOT EXISTS (SELECT 1 FROM payment_methods WHERE name = 'Nequi');

-- ============================================================
-- FIN DEL SCRIPT
-- ============================================================
