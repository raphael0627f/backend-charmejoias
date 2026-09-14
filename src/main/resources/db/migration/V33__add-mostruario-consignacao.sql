-- consignacao passa a exigir um mostruario (regra aplicada na camada de validacao/DTO, ja que
-- consignacoes existentes nao tem mostruario associado e nao da pra inventar um retroativamente) -
-- a coluna fica nullable pra nao quebrar o historico
ALTER TABLE consignacoes ADD COLUMN mostruario_id BIGINT;

ALTER TABLE consignacoes
    ADD CONSTRAINT fk_consignacoes_mostruario FOREIGN KEY (mostruario_id) REFERENCES mostruarios(id);
