-- Paso 2/3 del reto: soporte para la saga distribuida y consumo idempotente de eventos.
-- Los schemas transfers.* y notifications.* quedan congelados (fase "contract" de la
-- migración expand/migrate/contract): sus módulos fueron extraídos como microservicios.

-- Idempotency Key del endpoint interno de movimientos (previene dobles débitos/créditos)
CREATE TABLE accounts.processed_movements (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    movement_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Consumo idempotente de eventos del broker por el módulo audit
CREATE TABLE audit.processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
