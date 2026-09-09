-- =========================
-- TABELA: notas_fiscais_correcoes
-- =========================
CREATE TABLE notas_fiscais_correcoes (
    id BIGSERIAL PRIMARY KEY,

    nota_fiscal_id BIGINT NOT NULL,
    sequencia INTEGER NOT NULL,
    texto TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    protocolo VARCHAR(50),
    motivo_rejeicao TEXT,

    created_at TIMESTAMP,

    CONSTRAINT fk_notas_fiscais_correcoes_nota_fiscal
        FOREIGN KEY (nota_fiscal_id)
        REFERENCES notas_fiscais(id)
        ON DELETE CASCADE
);
