ALTER TABLE notas_fiscais
    ADD COLUMN data_saida_entrada TIMESTAMP,
    ADD COLUMN transportadora_id BIGINT,
    ADD COLUMN modalidade_frete INTEGER,
    ADD COLUMN informacoes_complementares TEXT;

ALTER TABLE notas_fiscais
    ADD CONSTRAINT fk_notas_fiscais_transportadora
    FOREIGN KEY (transportadora_id)
    REFERENCES transportadoras(id);
