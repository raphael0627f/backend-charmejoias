-- =========================
-- pedidos: suporte a joias
-- =========================
ALTER TABLE pedidos ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'PRODUTO';
ALTER TABLE pedidos ALTER COLUMN tipo DROP DEFAULT;

ALTER TABLE pedidos_produtos ADD COLUMN joia_id UUID;
ALTER TABLE pedidos_produtos
    ADD CONSTRAINT fk_pedidos_produtos_joia
        FOREIGN KEY (joia_id) REFERENCES joias(id);
ALTER TABLE pedidos_produtos
    ADD CONSTRAINT chk_pedidos_produtos_item
        CHECK (produto_id IS NULL OR joia_id IS NULL);

-- =========================
-- consignacoes: suporte a joias
-- =========================
ALTER TABLE consignacoes ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'PRODUTO';
ALTER TABLE consignacoes ALTER COLUMN tipo DROP DEFAULT;

ALTER TABLE consignacoes_produtos ADD COLUMN joia_id UUID;
ALTER TABLE consignacoes_produtos ALTER COLUMN produto_id DROP NOT NULL;
ALTER TABLE consignacoes_produtos
    ADD CONSTRAINT fk_consignacoes_produtos_joia
        FOREIGN KEY (joia_id) REFERENCES joias(id);
ALTER TABLE consignacoes_produtos
    ADD CONSTRAINT chk_consignacoes_produtos_item
        CHECK ((produto_id IS NOT NULL AND joia_id IS NULL) OR (produto_id IS NULL AND joia_id IS NOT NULL));
