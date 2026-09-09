-- =========================
-- pedidos_produtos: referencia opcional a uma combinacao especifica (ProdutoVariacao)
-- =========================
ALTER TABLE pedidos_produtos ADD COLUMN produto_variacao_id BIGINT;
ALTER TABLE pedidos_produtos
    ADD CONSTRAINT fk_pedidos_produtos_produto_variacao
        FOREIGN KEY (produto_variacao_id) REFERENCES produtos_variacoes(id);

-- =========================
-- consignacoes_produtos: referencia opcional a uma combinacao especifica (ProdutoVariacao)
-- =========================
ALTER TABLE consignacoes_produtos ADD COLUMN produto_variacao_id BIGINT;
ALTER TABLE consignacoes_produtos
    ADD CONSTRAINT fk_consignacoes_produtos_produto_variacao
        FOREIGN KEY (produto_variacao_id) REFERENCES produtos_variacoes(id);
