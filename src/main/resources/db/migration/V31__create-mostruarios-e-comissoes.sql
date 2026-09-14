-- =========================
-- TABELA: mostruarios
-- =========================
CREATE TABLE mostruarios (
    id BIGSERIAL PRIMARY KEY,
    descricao VARCHAR(255) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- =========================
-- TABELA: comissoes
-- =========================
CREATE TABLE comissoes (
    id BIGSERIAL PRIMARY KEY,
    percentual NUMERIC(5,2) NOT NULL,
    valor_minimo NUMERIC(10,2) NOT NULL,
    valor_maximo NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
