-- Perfil do estudante: um por conta (ESCOPO_MVP seção 6). O currículo-base estruturado entra em migração posterior.
CREATE TABLE students (
    id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid          NOT NULL REFERENCES users (id),
    full_name   varchar(150)  NOT NULL,
    course      varchar(150),
    institution varchar(150),
    location    varchar(150),
    summary     varchar(2000),
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_students_user_id UNIQUE (user_id)
);
