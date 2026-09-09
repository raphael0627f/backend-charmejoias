-- =========================
-- TABELA: consignacoes
-- =========================
ALTER TABLE consignacoes
    ADD COLUMN data_entrega DATE,
    ADD COLUMN data_pre_acerto_consignacao DATE,
    ADD COLUMN data_acerto_consignacao DATE;
