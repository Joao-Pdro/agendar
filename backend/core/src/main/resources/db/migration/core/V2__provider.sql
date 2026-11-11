CREATE TABLE provider (
    id         uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id  uuid        NOT NULL,
    name       text        NOT NULL,
    -- Referência ao usuário do módulo identity, sem FK: módulos não compartilham chave.
    user_id    uuid        NULL,
    deleted_at timestamptz NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_provider PRIMARY KEY (id)
);

CREATE INDEX ix_provider_tenant ON provider (tenant_id);

-- Um mesmo usuário não pode estar cadastrado como dois prestadores no mesmo tenant.
-- Índice parcial: prestador excluído libera o usuário para um novo cadastro.
CREATE UNIQUE INDEX uq_provider_tenant_user
    ON provider (tenant_id, user_id)
    WHERE deleted_at IS NULL AND user_id IS NOT NULL;

-- O mesmo nome não se repete entre prestadores ativos do tenant.
CREATE UNIQUE INDEX uq_provider_tenant_nome_ativo
    ON provider (tenant_id, lower(name))
    WHERE deleted_at IS NULL;
