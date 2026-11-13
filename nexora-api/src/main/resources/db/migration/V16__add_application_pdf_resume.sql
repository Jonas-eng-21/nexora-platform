-- Currículo em PDF (ESCOPO_MVP 3.5, RNF-11/12). O arquivo fica em armazenamento privado do servidor;
-- aqui ficam só a chave interna (nome gerado pelo servidor) e os metadados.
ALTER TABLE applications
    ADD COLUMN storage_key       varchar(100),
    ADD COLUMN original_filename varchar(150),
    ADD COLUMN file_size         bigint,
    ADD COLUMN content_type      varchar(100);

-- Escolha exclusiva: ou snapshot (PLATFORM) ou arquivo (PDF), nunca os dois nem nenhum.
-- O limite de 5 MiB (5.242.880 bytes) também é garantido pelo banco.
ALTER TABLE applications DROP CONSTRAINT ck_applications_platform_has_snapshot;
ALTER TABLE applications ADD CONSTRAINT ck_applications_resume_exclusive CHECK (
    (resume_type = 'PLATFORM'
        AND resume_snapshot IS NOT NULL
        AND storage_key IS NULL AND original_filename IS NULL AND file_size IS NULL AND content_type IS NULL)
    OR
    (resume_type = 'PDF'
        AND resume_snapshot IS NULL
        AND storage_key IS NOT NULL AND original_filename IS NOT NULL AND content_type IS NOT NULL
        AND file_size > 0 AND file_size <= 5242880)
);

CREATE UNIQUE INDEX uq_applications_storage_key ON applications (storage_key) WHERE storage_key IS NOT NULL;
