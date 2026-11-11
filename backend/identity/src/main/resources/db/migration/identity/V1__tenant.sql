CREATE TABLE tenant (
    id                     uuid        NOT NULL DEFAULT gen_random_uuid(),
    name                   text        NOT NULL,
    slug                   text        NOT NULL,
    customer_self_booking  boolean     NOT NULL DEFAULT false,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_tenant PRIMARY KEY (id),
    CONSTRAINT uq_tenant_slug UNIQUE (slug)
);
