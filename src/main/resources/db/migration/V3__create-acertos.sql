-- =========================
-- TABELA: acertos
-- =========================
CREATE TABLE acertos (
    id BIGSERIAL PRIMARY KEY,

    valor_consignacoes NUMERIC(10,2),
    valor_maleta NUMERIC(10,2),
    valor_acessorios NUMERIC(10,2),

    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- =========================
-- TABELA: acertos_consignacoes
-- =========================
CREATE TABLE acertos_consignacoes (
    id BIGSERIAL PRIMARY KEY,

    descricao VARCHAR(255),
    quantidade_produtos INTEGER,

    tipo VARCHAR(50),

    valor_total NUMERIC(10,2),

    consignacao_id BIGINT,
    acerto_id BIGINT,

    CONSTRAINT fk_acertos_consignacoes_consignacao
        FOREIGN KEY (consignacao_id)
        REFERENCES consignacoes(id),

    CONSTRAINT fk_acertos_consignacoes_acerto
        FOREIGN KEY (acerto_id)
        REFERENCES acertos(id)
        ON DELETE CASCADE
);