-- =========================
-- TABELA: consignacoes
-- =========================
CREATE TABLE consignacoes (
    id BIGSERIAL PRIMARY KEY,
    descricao VARCHAR(255),

    cliente_id UUID,

    created_at TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_consignacoes_cliente
        FOREIGN KEY (cliente_id)
        REFERENCES clientes(id)
);

-- =========================
-- TABELA: consignacoes_produtos
-- =========================
CREATE TABLE consignacoes_produtos (
    id BIGSERIAL PRIMARY KEY,

    quantidade INTEGER,
    preco NUMERIC(10,2),

    consignacao_id BIGINT,
    produto_id UUID,

    CONSTRAINT fk_consignacoes_produtos_consignacao
        FOREIGN KEY (consignacao_id)
        REFERENCES consignacoes(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_consignacoes_produtos_produto
        FOREIGN KEY (produto_id)
        REFERENCES produtos(id)
);