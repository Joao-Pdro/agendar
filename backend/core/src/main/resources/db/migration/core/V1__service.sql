CREATE TABLE service (
    id               uuid          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        uuid          NOT NULL,
    name             text          NOT NULL,
    duration_minutes integer       NOT NULL,
    base_price       numeric(12,2) NOT NULL,
    deleted_at       timestamptz   NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_service PRIMARY KEY (id),

    CONSTRAINT ck_service_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_service_price CHECK (base_price >= 0)
);

CREATE INDEX ix_service_tenant ON service (tenant_id);

-- O mesmo nome não se repete entre serviços ativos do tenant. Depois do soft delete, o nome
-- volta a ficar livre.
CREATE UNIQUE INDEX uq_service_tenant_nome_ativo
    ON service (tenant_id, lower(name))
    WHERE deleted_at IS NULL;
