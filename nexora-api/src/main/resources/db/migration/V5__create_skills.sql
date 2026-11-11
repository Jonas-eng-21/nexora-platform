-- Catálogo padronizado de competências, compartilhado por estudantes e vagas (ESCOPO_MVP seção 6).
CREATE TABLE skills (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name            varchar(80)  NOT NULL,
    -- Nome em minúsculas, sem acentos e com espaços simples: impede duplicatas lógicas ("Java" x "java").
    normalized_name varchar(80)  NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_skills_normalized_name UNIQUE (normalized_name)
);

-- Competências técnicas do currículo-base do estudante.
CREATE TABLE student_skills (
    student_id uuid NOT NULL REFERENCES students (id),
    skill_id   uuid NOT NULL REFERENCES skills (id),

    PRIMARY KEY (student_id, skill_id)
);

CREATE INDEX idx_student_skills_skill_id ON student_skills (skill_id);

-- Catálogo inicial. A normalização abaixo equivale à feita pela aplicação (Skill.normalize).
INSERT INTO skills (name, normalized_name)
SELECT name, translate(lower(name), 'áàâãéêíóôõúç', 'aaaaeeiooouc')
FROM (VALUES
    ('Java'), ('Spring Boot'), ('Python'), ('Django'), ('Flask'), ('FastAPI'),
    ('JavaScript'), ('TypeScript'), ('Node.js'), ('React'), ('Vue.js'), ('Angular'), ('Next.js'),
    ('HTML'), ('CSS'), ('Tailwind CSS'),
    ('C'), ('C++'), ('C#'), ('.NET'), ('PHP'), ('Laravel'), ('Go'), ('Rust'), ('Ruby'), ('Kotlin'), ('Swift'),
    ('Flutter'), ('React Native'), ('Android'),
    ('SQL'), ('PostgreSQL'), ('MySQL'), ('MongoDB'), ('Redis'), ('Firebase'),
    ('Docker'), ('Kubernetes'), ('Linux'), ('Git'), ('CI/CD'), ('AWS'), ('Azure'), ('Google Cloud'),
    ('APIs REST'), ('GraphQL'), ('Testes Automatizados'), ('Scrum'),
    ('Análise de Dados'), ('Machine Learning'), ('Pandas'), ('Power BI'), ('Excel'),
    ('Redes de Computadores'), ('Segurança da Informação'), ('UX/UI Design'), ('Figma')
) AS seed (name);
