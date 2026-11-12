-- Exclusão lógica de vagas (ESCOPO_MVP 3.4 e SYSTEM_DESIGN seção 5): preserva candidaturas e histórico.
ALTER TABLE jobs
    ADD COLUMN deleted_at timestamptz;

CREATE INDEX idx_jobs_company_active
    ON jobs (company_id, created_at DESC) WHERE deleted_at IS NULL;

