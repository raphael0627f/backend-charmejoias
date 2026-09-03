-- =========================
-- TABELA: empresas
-- =========================
CREATE TABLE empresas (
    id BIGSERIAL PRIMARY KEY,
    razao_social VARCHAR(255),
    nome_fantasia VARCHAR(255),
    cnpj VARCHAR(14),
    inscricao_estadual VARCHAR(20),
    cnae VARCHAR(10),

    regime_tributario VARCHAR(40),

    endereco_id BIGINT,

    contora_company_id VARCHAR(100),
    certificado_enviado BOOLEAN NOT NULL DEFAULT FALSE,
    certificado_enviado_em TIMESTAMP,

    proximo_numero_nfe BIGINT,
    serie_nfe INTEGER,

    created_at TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_empresas_endereco
        FOREIGN KEY (endereco_id) REFERENCES enderecos(id)
);

CREATE UNIQUE INDEX uq_empresas_cnpj ON empresas(cnpj);
