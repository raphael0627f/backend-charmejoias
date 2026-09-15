ALTER TABLE acertos_consignacao
    ADD COLUMN pre_acerto_consignacao_id BIGINT,
    ADD CONSTRAINT fk_acerto_consignacao_pre_acerto
        FOREIGN KEY (pre_acerto_consignacao_id) REFERENCES pre_acertos_consignacao (id);
