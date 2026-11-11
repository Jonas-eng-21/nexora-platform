-- Credenciais, papel e situação da conta (ESCOPO_MVP seção 6).
CREATE TABLE users (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         varchar(254) NOT NULL,
    password_hash varchar(100) NOT NULL,
    role          varchar(20)  NOT NULL,
    status        varchar(20)  NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email UNIQUE (email),
    -- E-mail sempre em minúsculas: torna a unicidade insensível a maiúsculas/minúsculas.
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('STUDENT', 'COMPANY', 'UNIVERSITY', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'PENDING_APPROVAL', 'SUSPENDED'))
);
