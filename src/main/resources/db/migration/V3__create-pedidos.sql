-- =========================
-- TABELA: pedidos
-- =========================
CREATE TABLE pedidos (
    id BIGSERIAL PRIMARY KEY,

    observacao TEXT,
    data_entrega DATE,

    status VARCHAR(50),

    cliente_id UUID,

    created_at TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_pedidos_cliente
        FOREIGN KEY (cliente_id)
        REFERENCES clientes(id)
);

-- =========================
-- TABELA: pedidos_produtos
-- =========================
CREATE TABLE pedidos_produtos (
    id BIGSERIAL PRIMARY KEY,

    descricao VARCHAR(255),
    variacao VARCHAR(255),
    quantidade INTEGER,

    preco NUMERIC(10,2),

    produto_id UUID,
    pedido_id BIGINT,

    CONSTRAINT fk_pedidos_produtos_produto
        FOREIGN KEY (produto_id)
        REFERENCES produtos(id),

    CONSTRAINT fk_pedidos_produtos_pedido
        FOREIGN KEY (pedido_id)
        REFERENCES pedidos(id)
        ON DELETE CASCADE
);