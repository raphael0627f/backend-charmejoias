-- =========================
-- TABELA: transportadoras
-- =========================
CREATE TABLE transportadoras (
    id BIGSERIAL PRIMARY KEY,

    razao_social VARCHAR(255) NOT NULL,
    documento VARCHAR(20),
    inscricao_estadual VARCHAR(20),

    endereco VARCHAR(255),
    municipio VARCHAR(255),
    uf CHAR(2),

    placa_veiculo VARCHAR(10),
    uf_veiculo CHAR(2),
    rntc VARCHAR(20),

    ativo BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
