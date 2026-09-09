-- =========================
-- TABELA: ncm
-- =========================
CREATE TABLE ncm (
    codigo VARCHAR(8) PRIMARY KEY,
    descricao VARCHAR(255) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- =========================
-- PRODUTOS: ncm passa a ser referência ao cadastro de NCM
-- =========================
ALTER TABLE produtos DROP COLUMN ncm;

ALTER TABLE produtos ADD COLUMN ncm_codigo VARCHAR(8);

ALTER TABLE produtos ADD CONSTRAINT fk_produtos_ncm
    FOREIGN KEY (ncm_codigo)
    REFERENCES ncm(codigo);
