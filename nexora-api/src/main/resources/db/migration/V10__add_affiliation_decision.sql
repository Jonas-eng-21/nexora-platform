-- Decisão sobre o pedido de vínculo: quem decidiu (universidade destinatária ou administrador), quando e por quê.
ALTER TABLE university_affiliations
    ADD COLUMN decided_by      uuid REFERENCES users (id),
    ADD COLUMN decided_at      timestamptz,
    ADD COLUMN decision_reason varchar(500);
