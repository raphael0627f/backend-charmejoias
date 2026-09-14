-- =========================
-- TABELA: produtos
-- =========================
-- Codigo de barras (NanoId) tambem para produto simples, mesmo padrao ja usado nas
-- variacoes (produtos_variacoes.codigo_barras). Nullable porque produtos existentes
-- nao tem esse codigo ainda - sao preenchidos sob demanda na proxima edicao.
ALTER TABLE produtos ADD COLUMN codigo_barras VARCHAR(30);
ALTER TABLE produtos ADD CONSTRAINT uq_produtos_codigo_barras UNIQUE (codigo_barras);
