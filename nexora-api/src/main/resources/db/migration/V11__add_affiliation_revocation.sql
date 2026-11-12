-- Revogação do vínculo aprovado: autor (universidade responsável ou administrador), data e motivo.
-- Colunas próprias, separadas da decisão original, para a revogação não reescrever o histórico.
ALTER TABLE university_affiliations
    ADD COLUMN revoked_by        uuid REFERENCES users (id),
    ADD COLUMN revoked_at        timestamptz,
    ADD COLUMN revocation_reason varchar(500);
