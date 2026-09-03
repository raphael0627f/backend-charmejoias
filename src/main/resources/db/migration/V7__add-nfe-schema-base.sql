-- =========================
-- enderecos: codigo IBGE do municipio
-- =========================
ALTER TABLE enderecos ADD COLUMN ibge VARCHAR(10);

-- =========================
-- produtos: dados fiscais
-- =========================
ALTER TABLE produtos ADD COLUMN ncm VARCHAR(8);
ALTER TABLE produtos ADD COLUMN origem_produto INTEGER;
ALTER TABLE produtos ADD COLUMN unidade_comercial VARCHAR(10);
ALTER TABLE produtos ADD COLUMN codigo_situacao_tributaria_icms VARCHAR(10);
ALTER TABLE produtos ADD COLUMN codigo_situacao_tributaria_pis VARCHAR(10);
ALTER TABLE produtos ADD COLUMN codigo_situacao_tributaria_cofins VARCHAR(10);
