-- O contrato (#9) exige cliente e serviço no agendamento; a #6 criou só o necessário para a
-- constraint de conflito. Aqui entram os vínculos e a chave de idempotência.
ALTER TABLE appointment
    ADD COLUMN customer_id uuid NULL,
    ADD COLUMN service_id  uuid NULL;

-- Idempotência: mesma chave do mesmo tenant devolve o mesmo agendamento, nunca cria outro.
-- A chave é por tenant porque dois tenants podem usar o mesmo valor sem se enxergarem.
ALTER TABLE appointment
    ADD COLUMN idempotency_key varchar(128) NULL;

-- Só os agendamentos criados por POST têm chave; os antigos (criados antes desta migration) ficam nulos.
CREATE UNIQUE INDEX uq_appointment_tenant_idempotency
    ON appointment (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Data/hora da requisição que usou a chave, para expirar a chave depois de 24h.
ALTER TABLE appointment
    ADD COLUMN idempotency_created_at timestamptz NULL;

CREATE INDEX ix_appointment_prestador_periodo
    ON appointment (prestador_id, starts_at, ends_at);

CREATE INDEX ix_appointment_tenant_periodo
    ON appointment (tenant_id, starts_at, ends_at);
