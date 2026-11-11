CREATE TABLE app_user (
    id            uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     uuid        NULL,
    email         text        NOT NULL,
    password_hash text        NOT NULL,
    display_name  text        NOT NULL,
    role          text        NOT NULL,
    activo        boolean     NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_app_user PRIMARY KEY (id),

    -- O operador da plataforma fica fora de qualquer tenant; todos os outros perfis têm tenant.
    CONSTRAINT ck_app_user_role CHECK (
        role IN ('OPERADOR_PLATAFORMA', 'ADMIN', 'RECEPCAO', 'PRESTADOR')
    ),
    CONSTRAINT ck_app_user_tenant CHECK (
        (role = 'OPERADOR_PLATAFORMA' AND tenant_id IS NULL)
        OR (role <> 'OPERADOR_PLATAFORMA' AND tenant_id IS NOT NULL)
    )
);

-- E-mail é único dentro do tenant. O operador da plataforma (tenant_id nulo) fica fora do
-- índice parcial para não colidir com o e-mail de um usuário de tenant.
CREATE UNIQUE INDEX uq_app_user_tenant_email
    ON app_user (tenant_id, lower(email))
    WHERE tenant_id IS NOT NULL;

CREATE UNIQUE INDEX uq_app_user_plataforma_email
    ON app_user (lower(email))
    WHERE tenant_id IS NULL;

CREATE INDEX ix_app_user_tenant ON app_user (tenant_id);
