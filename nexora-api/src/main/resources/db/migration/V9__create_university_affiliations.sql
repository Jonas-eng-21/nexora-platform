-- Histórico de pedidos e vínculos acadêmicos (ESCOPO_MVP 3.2). Nenhuma linha é apagada: o estado muda.
CREATE TABLE university_affiliations (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id    uuid         NOT NULL REFERENCES students (id),
    university_id uuid         NOT NULL REFERENCES universities (id),
    status        varchar(20)  NOT NULL,
    requested_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_university_affiliations_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REVOKED'))
);

-- No máximo uma solicitação pendente e um vínculo aprovado (não revogado) por estudante,
-- garantidos pelo banco mesmo sob requisições simultâneas.
CREATE UNIQUE INDEX uq_university_affiliations_one_pending
    ON university_affiliations (student_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_university_affiliations_one_approved
    ON university_affiliations (student_id) WHERE status = 'APPROVED';

CREATE INDEX idx_university_affiliations_student ON university_affiliations (student_id);
CREATE INDEX idx_university_affiliations_university_status ON university_affiliations (university_id, status);
