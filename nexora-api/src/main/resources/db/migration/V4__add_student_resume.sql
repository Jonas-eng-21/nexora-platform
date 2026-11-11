-- Currículo-base estruturado: seções repetíveis em JSONB validado pela aplicação (ESCOPO_MVP 3.3).
-- As competências técnicas ficam em tabelas próprias (catálogo de skills), não neste documento.
ALTER TABLE students
    ADD COLUMN resume jsonb NOT NULL DEFAULT '{}'::jsonb;
