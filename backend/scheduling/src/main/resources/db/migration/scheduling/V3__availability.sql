-- Expediente semanal. Férias, bloqueios e exceções pontuais ficam fora do MVP.
--
-- Os horários são guardados como hora local (sem fuso) mais o fuso do tenant, e não em UTC:
-- o expediente se repete toda semana, e um valor em UTC deslocaria a grade quando o horário de
-- verão mudasse. A conversão para UTC acontece no cálculo do slot.
--
-- `weekday` segue ISO-8601: 1 = segunda, 7 = domingo.

-- Fuso do tenant em tabela própria: existe mesmo antes de o expediente ser configurado, e é o
-- que a grade consulta para saber em que fuso exibir.
CREATE TABLE tenant_timezone (
    tenant_id uuid NOT NULL,
    timezone  text NOT NULL DEFAULT 'America/Sao_Paulo',

    CONSTRAINT pk_tenant_timezone PRIMARY KEY (tenant_id)
);

-- Expediente padrão do tenant, usado por quem não tem expediente próprio.
CREATE TABLE tenant_availability (
    tenant_id  uuid     NOT NULL,
    weekday    smallint NOT NULL,
    start_time time     NOT NULL,
    end_time   time     NOT NULL,

    CONSTRAINT pk_tenant_availability PRIMARY KEY (tenant_id, weekday),
    CONSTRAINT ck_tenant_availability_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT ck_tenant_availability_periodo CHECK (start_time < end_time)
);

-- Expediente próprio do prestador. Se existir qualquer linha aqui, ela substitui o do tenant.
CREATE TABLE provider_availability (
    id          uuid     NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   uuid     NOT NULL,
    -- Só o ID: nenhuma FK entre módulos nem entre schemas.
    provider_id uuid     NOT NULL,
    weekday     smallint NOT NULL,
    start_time  time     NOT NULL,
    end_time    time     NOT NULL,

    CONSTRAINT pk_provider_availability PRIMARY KEY (id),
    CONSTRAINT ck_provider_availability_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT ck_provider_availability_periodo CHECK (start_time < end_time)
);

-- Um intervalo por dia por prestador no MVP (sem divisão de almoço).
CREATE UNIQUE INDEX uq_provider_availability_dia
    ON provider_availability (provider_id, weekday);

CREATE INDEX ix_provider_availability_tenant ON provider_availability (tenant_id);
