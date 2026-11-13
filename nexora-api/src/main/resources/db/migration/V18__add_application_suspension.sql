-- Suspensão automática de candidaturas por perda da validação acadêmica (US-32). Guarda o necessário para a
-- reativação explícita futura: o estado anterior, o motivo (código, nunca texto livre) e o instante.
-- status_reason (V17) continua sendo a última decisão ordinária da empresa ou do admin e não é tocado.
ALTER TABLE applications
    ADD COLUMN suspended_from_status varchar(20),
    ADD COLUMN suspension_reason     varchar(50),
    ADD COLUMN suspended_at          timestamptz;

ALTER TABLE applications ADD CONSTRAINT ck_applications_suspended_from_status
    CHECK (suspended_from_status IS NULL OR suspended_from_status IN ('SUBMITTED', 'IN_REVIEW'));
ALTER TABLE applications ADD CONSTRAINT ck_applications_suspension_reason
    CHECK (suspension_reason IS NULL OR suspension_reason IN ('ACADEMIC_AFFILIATION_REVOKED', 'UNIVERSITY_SUSPENDED'));
-- Toda candidatura SUSPENDED explica a si mesma. A volta (limpar os campos ao reativar) é decisão da US-33.
ALTER TABLE applications ADD CONSTRAINT ck_applications_suspended_has_metadata
    CHECK (status <> 'SUSPENDED'
           OR (suspended_from_status IS NOT NULL AND suspension_reason IS NOT NULL AND suspended_at IS NOT NULL));
