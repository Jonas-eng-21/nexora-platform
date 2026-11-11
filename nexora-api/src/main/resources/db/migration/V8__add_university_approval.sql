-- Análise de aprovação do cadastro da universidade (ESCOPO_MVP 3.1): decisão, motivo, autor e data.
-- É um estado próprio da instituição, distinto do status da conta e dos estados de vínculo acadêmico.
ALTER TABLE universities
    ADD COLUMN approval_status     varchar(20)  NOT NULL DEFAULT 'PENDING',
    ADD COLUMN approval_reason     varchar(500),
    ADD COLUMN approval_decided_by uuid         REFERENCES users (id),
    ADD COLUMN approval_decided_at timestamptz,
    ADD CONSTRAINT ck_universities_approval_status
        CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'));

CREATE INDEX idx_universities_approval_status ON universities (approval_status);

-- Conta de universidade rejeitada: não opera e não volta a ficar pendente nem ativa por reativação.
ALTER TABLE users DROP CONSTRAINT ck_users_status;
ALTER TABLE users ADD CONSTRAINT ck_users_status
    CHECK (status IN ('ACTIVE', 'PENDING_APPROVAL', 'SUSPENDED', 'REJECTED'));
