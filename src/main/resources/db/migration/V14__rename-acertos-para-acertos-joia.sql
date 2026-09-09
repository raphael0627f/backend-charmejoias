-- =========================
-- Renomeia o modulo de Acerto para Acerto de Joia
-- =========================
ALTER TABLE acertos RENAME TO acertos_joia;
ALTER TABLE acertos_consignacoes RENAME TO acertos_joia_consignacoes;

ALTER TABLE acertos_joia_consignacoes RENAME COLUMN acerto_id TO acerto_joia_id;

ALTER TABLE acertos_joia_consignacoes RENAME CONSTRAINT fk_acertos_consignacoes_consignacao TO fk_acertos_joia_consignacoes_consignacao;
ALTER TABLE acertos_joia_consignacoes RENAME CONSTRAINT fk_acertos_consignacoes_acerto TO fk_acertos_joia_consignacoes_acerto_joia;
