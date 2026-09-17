ALTER TABLE enderecos
    ADD COLUMN ponto_referencia VARCHAR(255),
    ADD COLUMN descricao VARCHAR(255),
    ADD COLUMN latitude NUMERIC(10, 7),
    ADD COLUMN longitude NUMERIC(10, 7);
