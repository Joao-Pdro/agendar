CREATE TABLE customer (
    id         uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id  uuid        NOT NULL,
    name       text        NOT NULL,
    -- CPF ou CNPJ, só dígitos (a validação do dígito verificador é no domínio).
    document   text        NOT NULL,
    phone      text        NULL,
    email      text        NULL,
    deleted_at timestamptz NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_customer PRIMARY KEY (id),
    CONSTRAINT ck_customer_document_digitos CHECK (document ~ '^[0-9]{11}$|^[0-9]{14}$')
);

CREATE INDEX ix_customer_tenant ON customer (tenant_id);

-- Documento único por tenant entre os clientes não apagados. Índice parcial: depois do soft
-- delete o documento volta a ficar livre para um novo cadastro.
CREATE UNIQUE INDEX uq_customer_tenant_document
    ON customer (tenant_id, document)
    WHERE deleted_at IS NULL;

-- Busca por nome na lista: índice de prefixo em minúsculas.
CREATE INDEX ix_customer_tenant_nome ON customer (tenant_id, lower(name));
