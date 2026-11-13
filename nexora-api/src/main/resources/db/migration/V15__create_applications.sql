-- Candidaturas (ESCOPO_MVP 3.5). Uma por par estudante-vaga; nenhuma linha é apagada, o estado muda.
-- Aqui entra o currículo da plataforma (PLATFORM): o snapshot JSONB é gravado na candidatura e nunca mais
-- alterado. O PDF (resume_type = 'PDF') ganha suas colunas e a checagem de exclusividade na história própria.
CREATE TABLE applications (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      uuid         NOT NULL REFERENCES students (id),
    job_id          uuid         NOT NULL REFERENCES jobs (id),
    resume_type     varchar(20)  NOT NULL,
    status          varchar(20)  NOT NULL,
    resume_snapshot jsonb,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_applications_student_job UNIQUE (student_id, job_id),
    CONSTRAINT ck_applications_resume_type CHECK (resume_type IN ('PLATFORM', 'PDF')),
    CONSTRAINT ck_applications_status
        CHECK (status IN ('SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT ck_applications_platform_has_snapshot
        CHECK (resume_type <> 'PLATFORM' OR resume_snapshot IS NOT NULL)
);

CREATE INDEX idx_applications_job ON applications (job_id);
CREATE INDEX idx_applications_student ON applications (student_id);
