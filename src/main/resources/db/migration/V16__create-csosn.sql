-- =========================
-- TABELA: csosn
-- =========================
CREATE TABLE csosn (
    codigo VARCHAR(3) PRIMARY KEY,
    descricao VARCHAR(255) NOT NULL
);

INSERT INTO csosn (codigo, descricao) VALUES
    ('101', 'Tributada pelo Simples Nacional com permissão de crédito'),
    ('102', 'Tributada pelo Simples Nacional sem permissão de crédito'),
    ('103', 'Isenção do ICMS no Simples Nacional para faixa de receita bruta'),
    ('201', 'Tributada pelo Simples Nacional com permissão de crédito e com cobrança do ICMS por substituição tributária'),
    ('202', 'Tributada pelo Simples Nacional sem permissão de crédito e com cobrança do ICMS por substituição tributária'),
    ('203', 'Isenção do ICMS no Simples Nacional para faixa de receita bruta e com cobrança do ICMS por substituição tributária'),
    ('300', 'Imune'),
    ('400', 'Não tributada pelo Simples Nacional'),
    ('500', 'ICMS cobrado anteriormente por substituição tributária ou por antecipação'),
    ('900', 'Outros');
