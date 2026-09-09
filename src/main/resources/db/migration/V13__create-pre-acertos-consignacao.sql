-- =========================
-- TABELA: pre_acertos_consignacao
-- =========================
CREATE TABLE pre_acertos_consignacao (
    id BIGSERIAL PRIMARY KEY,

    consignacao_id BIGINT UNIQUE,
    cliente_id UUID NOT NULL,
    observacoes VARCHAR(500),

    created_at TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_pre_acertos_consignacao_consignacao
        FOREIGN KEY (consignacao_id)
        REFERENCES consignacoes(id),

    CONSTRAINT fk_pre_acertos_consignacao_cliente
        FOREIGN KEY (cliente_id)
        REFERENCES clientes(id)
);

-- =========================
-- TABELA: pre_acertos_consignacao_produtos
-- =========================
CREATE TABLE pre_acertos_consignacao_produtos (
    id BIGSERIAL PRIMARY KEY,

    quantidade INTEGER,
    preco NUMERIC(10,2),

    pre_acerto_id BIGINT NOT NULL,
    produto_id UUID,
    produto_variacao_id BIGINT,
    joia_id UUID,

    CONSTRAINT fk_pre_acertos_consignacao_produtos_pre_acerto
        FOREIGN KEY (pre_acerto_id)
        REFERENCES pre_acertos_consignacao(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_pre_acertos_consignacao_produtos_produto
        FOREIGN KEY (produto_id)
        REFERENCES produtos(id),

    CONSTRAINT fk_pre_acertos_consignacao_produtos_produto_variacao
        FOREIGN KEY (produto_variacao_id)
        REFERENCES produtos_variacoes(id),

    CONSTRAINT fk_pre_acertos_consignacao_produtos_joia
        FOREIGN KEY (joia_id)
        REFERENCES joias(id)
);
