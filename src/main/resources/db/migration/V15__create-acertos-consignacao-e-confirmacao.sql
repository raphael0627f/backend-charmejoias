-- =========================
-- CONFIRMAÇÃO DA CONSIGNAÇÃO
-- =========================
ALTER TABLE consignacoes
    ADD COLUMN confirmada BOOLEAN NOT NULL DEFAULT FALSE;

-- =========================
-- TABELA: acertos_consignacao
-- =========================
CREATE TABLE acertos_consignacao (
    id BIGSERIAL PRIMARY KEY,

    consignacao_id BIGINT NOT NULL UNIQUE,
    desconto NUMERIC(10,2),
    observacoes VARCHAR(500),
    confirmado BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_acertos_consignacao_consignacao
        FOREIGN KEY (consignacao_id)
        REFERENCES consignacoes(id)
);

-- =========================
-- TABELA: acertos_consignacao_produtos
-- =========================
CREATE TABLE acertos_consignacao_produtos (
    id BIGSERIAL PRIMARY KEY,

    quantidade INTEGER NOT NULL,

    acerto_consignacao_id BIGINT NOT NULL,
    produto_id UUID,
    produto_variacao_id BIGINT,
    joia_id UUID,

    CONSTRAINT fk_acertos_consignacao_produtos_acerto
        FOREIGN KEY (acerto_consignacao_id)
        REFERENCES acertos_consignacao(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_acertos_consignacao_produtos_produto
        FOREIGN KEY (produto_id)
        REFERENCES produtos(id),

    CONSTRAINT fk_acertos_consignacao_produtos_produto_variacao
        FOREIGN KEY (produto_variacao_id)
        REFERENCES produtos_variacoes(id),

    CONSTRAINT fk_acertos_consignacao_produtos_joia
        FOREIGN KEY (joia_id)
        REFERENCES joias(id)
);
