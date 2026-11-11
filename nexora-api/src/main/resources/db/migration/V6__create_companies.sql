-- Perfil institucional da empresa: um por conta (ESCOPO_MVP seção 6).
CREATE TABLE companies (
    id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid          NOT NULL REFERENCES users (id),
    name        varchar(150)  NOT NULL,
    sector      varchar(150),
    description varchar(2000),
    location    varchar(150),
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_companies_user_id UNIQUE (user_id)
);
