-- Prestador é identificado por uuid; a constraint de exclusão precisa do operador de
-- igualdade com índice GiST, que a btree_gist fornece para tipos escalares.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE appointment (
    id           uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    uuid        NOT NULL,
    prestador_id uuid        NOT NULL,
    starts_at    timestamptz NOT NULL,
    ends_at      timestamptz NOT NULL,
    status       text        NOT NULL,
    version      bigint      NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_appointment PRIMARY KEY (id),

    CONSTRAINT ck_appointment_status CHECK (
        status IN ('PENDENTE', 'CONFIRMADO', 'EM_EXECUCAO', 'CONCLUIDO', 'CANCELADO', 'NO_SHOW')
    ),

    -- Intervalo sempre válido: início anterior ao fim.
    CONSTRAINT ck_appointment_periodo CHECK (starts_at < ends_at)
);

-- O intervalo é derivado das colunas, o que deixa o JPA trabalhar com timestamptz normal
-- e mantém a constraint de exclusão sobre um tstzrange real.
ALTER TABLE appointment
    ADD COLUMN during tstzrange
    GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED;

-- Dois agendamentos não cancelados do mesmo prestador não podem se sobrepor.
-- Cancelado sai do índice parcial, então libera o horário para um novo agendamento.
ALTER TABLE appointment
    ADD CONSTRAINT uq_appointment_prestador_during EXCLUDE USING gist (
        prestador_id WITH =,
        during WITH &&
    ) WHERE (status <> 'CANCELADO');

CREATE INDEX ix_appointment_tenant ON appointment (tenant_id);
