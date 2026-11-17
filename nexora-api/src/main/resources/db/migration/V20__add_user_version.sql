-- Controle de concorrência otimista da conta (users.version, mapeada com @Version em User).
-- Impede que uma transação que partiu de um estado anterior da conta sobrescreva uma alteração já confirmada,
-- por exemplo uma suspensão administrativa. Migração aditiva: contas existentes recebem a versão 0 e nada é
-- reescrito; o DEFAULT 0 também mantém válido qualquer INSERT que não informe a coluna.
ALTER TABLE users ADD COLUMN version bigint NOT NULL DEFAULT 0;
