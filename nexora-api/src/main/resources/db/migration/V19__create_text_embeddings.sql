-- Reaproveitamento de embeddings do matching semântico: a chave é o hash SHA-256 do texto canônico
-- mais o modelo e a dimensão, então mudar o texto, o modelo ou a dimensão nunca reutiliza um vetor antigo.
-- Só guarda o vetor (4 bytes por valor, big-endian); o texto original não é gravado.
CREATE TABLE text_embeddings (
    content_hash CHAR(64)     NOT NULL,
    model        VARCHAR(100) NOT NULL,
    dimensions   INTEGER      NOT NULL,
    embedding    BYTEA        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_text_embeddings PRIMARY KEY (content_hash, model, dimensions),
    CONSTRAINT chk_text_embeddings_dimensions CHECK (dimensions > 0),
    CONSTRAINT chk_text_embeddings_size CHECK (octet_length(embedding) = dimensions * 4)
);
