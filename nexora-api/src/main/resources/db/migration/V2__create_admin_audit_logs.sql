-- Rastro de ações administrativas críticas: quem fez, o quê, sobre qual alvo e quando (RF-25).
CREATE TABLE admin_audit_logs (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id    uuid         NOT NULL REFERENCES users (id),
    action      varchar(50)  NOT NULL,
    -- O alvo pode ser de vários tipos (usuário, vínculo, vaga, candidatura): sem FK, identificado por tipo + id.
    target_type varchar(30)  NOT NULL,
    target_id   uuid         NOT NULL,
    reason      varchar(500),
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_audit_logs_created_at ON admin_audit_logs (created_at DESC);
