-- Vagas publicadas por empresas (ESCOPO_MVP 3.4 e seção 6). A empresa dona vem sempre do usuário autenticado.
CREATE TABLE jobs (
    id          uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  uuid           NOT NULL REFERENCES companies (id),
    title       varchar(150)   NOT NULL,
    description varchar(5000)  NOT NULL,
    area        varchar(100)   NOT NULL,
    location    varchar(150),
    work_model  varchar(20)    NOT NULL,
    level       varchar(20)    NOT NULL,
    status      varchar(20)    NOT NULL,
    created_at  timestamptz    NOT NULL DEFAULT now(),
    updated_at  timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT ck_jobs_work_model CHECK (work_model IN ('ON_SITE', 'REMOTE', 'HYBRID')),
    CONSTRAINT ck_jobs_level CHECK (level IN ('INTERNSHIP', 'TRAINEE', 'JUNIOR', 'MID', 'SENIOR')),
    CONSTRAINT ck_jobs_status CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE INDEX idx_jobs_company_id ON jobs (company_id);

-- Competências da vaga. A chave primária impede que a mesma competência seja obrigatória e desejável ao mesmo tempo.
CREATE TABLE job_skills (
    job_id      uuid         NOT NULL REFERENCES jobs (id),
    skill_id    uuid         NOT NULL REFERENCES skills (id),
    requirement varchar(20)  NOT NULL,

    PRIMARY KEY (job_id, skill_id),
    CONSTRAINT ck_job_skills_requirement CHECK (requirement IN ('REQUIRED', 'DESIRED'))
);

CREATE INDEX idx_job_skills_skill_id ON job_skills (skill_id);

