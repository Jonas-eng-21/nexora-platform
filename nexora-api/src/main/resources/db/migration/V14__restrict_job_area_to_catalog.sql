-- A área da vaga passa a vir do catálogo fixo de áreas (ProfessionalArea), para o matching poder compará-la
-- por igualdade com as áreas de interesse do estudante. Vagas antigas com texto livre viram 'OTHER'.
UPDATE jobs
SET area = 'OTHER'
WHERE area NOT IN ('BACKEND', 'FRONTEND', 'FULLSTACK', 'MOBILE', 'DATA', 'ARTIFICIAL_INTELLIGENCE', 'DEVOPS_CLOUD',
                   'INFRASTRUCTURE', 'SECURITY', 'QA', 'UX_UI', 'EMBEDDED_IOT', 'GAMES', 'PRODUCT_MANAGEMENT',
                   'SUPPORT', 'OTHER');

ALTER TABLE jobs
    ALTER COLUMN area TYPE varchar(40),
    ADD CONSTRAINT ck_jobs_area CHECK (area IN (
        'BACKEND', 'FRONTEND', 'FULLSTACK', 'MOBILE', 'DATA', 'ARTIFICIAL_INTELLIGENCE', 'DEVOPS_CLOUD',
        'INFRASTRUCTURE', 'SECURITY', 'QA', 'UX_UI', 'EMBEDDED_IOT', 'GAMES', 'PRODUCT_MANAGEMENT',
        'SUPPORT', 'OTHER'));

-- Apoia o filtro por área na busca de vagas abertas e as recomendações.
CREATE INDEX idx_jobs_area_open ON jobs (area) WHERE deleted_at IS NULL AND status = 'OPEN';
