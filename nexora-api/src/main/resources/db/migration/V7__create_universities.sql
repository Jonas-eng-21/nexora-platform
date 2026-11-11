-- Dados institucionais da universidade: um por conta, criados já no cadastro (ESCOPO_MVP seção 6).
-- A decisão de aprovação do administrador entra em migração posterior.
CREATE TABLE universities (
    id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid          NOT NULL REFERENCES users (id),
    name        varchar(150)  NOT NULL,
    acronym     varchar(30),
    location    varchar(150),
    website     varchar(300),
    description varchar(2000),
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_universities_user_id UNIQUE (user_id)
);
