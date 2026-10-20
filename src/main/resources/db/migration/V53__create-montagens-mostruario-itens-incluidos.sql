-- =========================
-- registra quanto de cada item de pedido ja foi incluido (somado) numa consignacao da montagem.
-- Os produtos dos pedidos sao SOMADOS a consignacao (ex.: consignacao com 3 do produto + pedido com 4 = 7);
-- sem este registro nao ha como saber o que ja foi incluido para os pedidos e o que ainda falta.
-- =========================
CREATE TABLE montagens_mostruario_itens_incluidos (
    id BIGSERIAL PRIMARY KEY,
    montagem_id BIGINT NOT NULL REFERENCES montagens_mostruario (id) ON DELETE CASCADE,
    consignacao_id BIGINT NOT NULL REFERENCES consignacoes (id) ON DELETE CASCADE,
    pedido_produto_id BIGINT NOT NULL REFERENCES pedidos_produtos (id) ON DELETE CASCADE,
    quantidade INTEGER NOT NULL,
    created_at TIMESTAMP,
    CONSTRAINT uq_montagem_item_incluido UNIQUE (montagem_id, pedido_produto_id)
);

CREATE INDEX idx_montagens_itens_incluidos_montagem ON montagens_mostruario_itens_incluidos (montagem_id);
