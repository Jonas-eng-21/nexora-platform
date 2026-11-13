-- Decisão sobre o andamento da candidatura (US-31): motivo informado por quem mudou o status.
ALTER TABLE applications ADD COLUMN status_reason varchar(500);

-- A auditoria passa a registrar, para ações que mudam um estado, o valor anterior e o novo.
-- Colunas opcionais: as ações já existentes continuam sem preenchê-las.
ALTER TABLE admin_audit_logs
    ADD COLUMN previous_value varchar(50),
    ADD COLUMN new_value      varchar(50);
