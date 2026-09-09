ALTER TABLE empresas
    ADD COLUMN telefone VARCHAR(20);

ALTER TABLE ncm
    ADD COLUMN aliquota_aproximada_tributos NUMERIC(5,2);
