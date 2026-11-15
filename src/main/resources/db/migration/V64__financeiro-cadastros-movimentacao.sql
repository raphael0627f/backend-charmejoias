-- Financeiro 6.0, fase 1: cadastros financeiros e movimentacao de contas.
-- Aditiva: apenas tabelas novas, sementes (categorias, formas de pagamento e o banco "Caixa") e permissoes para o perfil ADMIN.
-- Contas a pagar, pagamentos, anexos, eventos e recorrencias ficam para a fase 2 (pagamento_id em movimentacao_banco ainda nao tem FK).

CREATE TABLE banco (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    tipo VARCHAR(30) NOT NULL CHECK (tipo IN ('CONTA_CORRENTE', 'POUPANCA', 'CAIXA', 'CARTAO', 'CARTEIRA_DIGITAL')),
    codigo_banco VARCHAR(10),
    agencia VARCHAR(20),
    conta VARCHAR(30),
    saldo_inicial NUMERIC(15, 2) NOT NULL DEFAULT 0,
    data_saldo_inicial DATE NOT NULL,
    observacao VARCHAR(500),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by_id UUID REFERENCES usuarios (id),
    updated_by_id UUID REFERENCES usuarios (id)
);
CREATE UNIQUE INDEX uk_banco_nome ON banco (LOWER(nome));

CREATE TABLE categoria_despesa (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    pai_id BIGINT REFERENCES categoria_despesa (id),
    tipo VARCHAR(30) NOT NULL CHECK (tipo IN ('CUSTO', 'DESPESA_FIXA', 'DESPESA_VARIAVEL', 'IMPOSTO', 'PESSOAL', 'OUTROS')),
    ordem INTEGER NOT NULL DEFAULT 0,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by_id UUID REFERENCES usuarios (id),
    updated_by_id UUID REFERENCES usuarios (id)
);
-- nome unico dentro do mesmo pai (grupos compartilham o "pai" nulo)
CREATE UNIQUE INDEX uk_categoria_despesa_pai_nome ON categoria_despesa (COALESCE(pai_id, 0), LOWER(nome));
CREATE INDEX idx_categoria_despesa_pai ON categoria_despesa (pai_id);

CREATE TABLE forma_pagamento (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by_id UUID REFERENCES usuarios (id),
    updated_by_id UUID REFERENCES usuarios (id)
);
CREATE UNIQUE INDEX uk_forma_pagamento_nome ON forma_pagamento (LOWER(nome));

CREATE TABLE centro_custo (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by_id UUID REFERENCES usuarios (id),
    updated_by_id UUID REFERENCES usuarios (id)
);
CREATE UNIQUE INDEX uk_centro_custo_nome ON centro_custo (LOWER(nome));

CREATE TABLE transferencia_banco (
    id BIGSERIAL PRIMARY KEY,
    banco_origem_id BIGINT NOT NULL REFERENCES banco (id),
    banco_destino_id BIGINT NOT NULL REFERENCES banco (id),
    valor NUMERIC(15, 2) NOT NULL CHECK (valor > 0),
    data DATE NOT NULL,
    descricao VARCHAR(255),
    estornada BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    created_by_id UUID REFERENCES usuarios (id),
    CHECK (banco_origem_id <> banco_destino_id)
);

CREATE TABLE movimentacao_banco (
    id BIGSERIAL PRIMARY KEY,
    banco_id BIGINT NOT NULL REFERENCES banco (id),
    data DATE NOT NULL,
    tipo VARCHAR(10) NOT NULL CHECK (tipo IN ('ENTRADA', 'SAIDA')),
    valor NUMERIC(15, 2) NOT NULL CHECK (valor > 0),
    origem VARCHAR(30) NOT NULL CHECK (origem IN ('SALDO_INICIAL', 'PAGAMENTO_CONTA_PAGAR', 'ESTORNO_PAGAMENTO', 'TRANSFERENCIA', 'AJUSTE', 'ESTORNO_MANUAL')),
    descricao VARCHAR(255),
    pagamento_id BIGINT,
    transferencia_id BIGINT REFERENCES transferencia_banco (id),
    estornada BOOLEAN NOT NULL DEFAULT FALSE,
    estorno_de_id BIGINT REFERENCES movimentacao_banco (id),
    motivo_estorno VARCHAR(255),
    estornada_em TIMESTAMP,
    estornada_por_id UUID REFERENCES usuarios (id),
    usuario_id UUID REFERENCES usuarios (id),
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_movimentacao_banco_banco_data ON movimentacao_banco (banco_id, data, id);
CREATE INDEX idx_movimentacao_banco_transferencia ON movimentacao_banco (transferencia_id);

-- ---------- sementes (editaveis pelo usuario) ----------

INSERT INTO forma_pagamento (nome, created_at, updated_at) VALUES
    ('Dinheiro', NOW(), NOW()), ('PIX', NOW(), NOW()), ('Boleto', NOW(), NOW()), ('Transferência', NOW(), NOW()),
    ('Cartão de crédito', NOW(), NOW()), ('Cartão de débito', NOW(), NOW()), ('Débito automático', NOW(), NOW()), ('Cheque', NOW(), NOW());

INSERT INTO banco (nome, tipo, saldo_inicial, data_saldo_inicial, created_at, updated_at)
VALUES ('Caixa', 'CAIXA', 0, CURRENT_DATE, NOW(), NOW());
-- saldo inicial zero nao gera movimentacao (o saldo e a soma das movimentacoes)

-- grupos (nivel 1)
INSERT INTO categoria_despesa (nome, pai_id, tipo, ordem, created_at, updated_at) VALUES
    ('Despesas com pessoal', NULL, 'PESSOAL', 1, NOW(), NOW()),
    ('Despesas operacionais', NULL, 'DESPESA_FIXA', 2, NOW(), NOW()),
    ('Impostos e taxas', NULL, 'IMPOSTO', 3, NOW(), NOW()),
    ('Custos de mercadoria', NULL, 'CUSTO', 4, NOW(), NOW()),
    ('Marketing', NULL, 'DESPESA_VARIAVEL', 5, NOW(), NOW()),
    ('Despesas financeiras', NULL, 'DESPESA_VARIAVEL', 6, NOW(), NOW()),
    ('Outras', NULL, 'OUTROS', 7, NOW(), NOW());

-- subgrupos (nivel 2): (grupo, nome, tipo, ordem)
INSERT INTO categoria_despesa (nome, pai_id, tipo, ordem, created_at, updated_at)
SELECT s.nome, g.id, s.tipo, s.ordem, NOW(), NOW()
FROM (VALUES
    ('Despesas com pessoal', 'Salários e ordenados', 'PESSOAL', 1),
    ('Despesas com pessoal', 'Comissões de vendedores', 'PESSOAL', 2),
    ('Despesas com pessoal', 'Encargos sociais (INSS e FGTS)', 'PESSOAL', 3),
    ('Despesas com pessoal', 'Férias e décimo terceiro', 'PESSOAL', 4),
    ('Despesas com pessoal', 'Pró-labore', 'PESSOAL', 5),
    ('Despesas com pessoal', 'Vale-transporte e alimentação', 'PESSOAL', 6),
    ('Despesas operacionais', 'Aluguel', 'DESPESA_FIXA', 1),
    ('Despesas operacionais', 'Energia elétrica', 'DESPESA_FIXA', 2),
    ('Despesas operacionais', 'Água e esgoto', 'DESPESA_FIXA', 3),
    ('Despesas operacionais', 'Telefone e internet', 'DESPESA_FIXA', 4),
    ('Despesas operacionais', 'Serviços contábeis', 'DESPESA_FIXA', 5),
    ('Despesas operacionais', 'Sistemas e assinaturas', 'DESPESA_FIXA', 6),
    ('Despesas operacionais', 'Seguros', 'DESPESA_FIXA', 7),
    ('Despesas operacionais', 'Manutenção e conservação', 'DESPESA_VARIAVEL', 8),
    ('Despesas operacionais', 'Material de escritório e limpeza', 'DESPESA_VARIAVEL', 9),
    ('Despesas operacionais', 'Embalagens e etiquetas', 'DESPESA_VARIAVEL', 10),
    ('Despesas operacionais', 'Frete e correios', 'DESPESA_VARIAVEL', 11),
    ('Despesas operacionais', 'Combustível e deslocamento de rotas', 'DESPESA_VARIAVEL', 12),
    ('Despesas operacionais', 'Viagens e hospedagem', 'DESPESA_VARIAVEL', 13),
    ('Impostos e taxas', 'Simples Nacional (DAS)', 'IMPOSTO', 1),
    ('Impostos e taxas', 'ICMS', 'IMPOSTO', 2),
    ('Impostos e taxas', 'ISS', 'IMPOSTO', 3),
    ('Impostos e taxas', 'IPTU e taxas municipais', 'IMPOSTO', 4),
    ('Impostos e taxas', 'Licenças e alvarás', 'IMPOSTO', 5),
    ('Custos de mercadoria', 'Compra de joias e semijoias', 'CUSTO', 1),
    ('Custos de mercadoria', 'Compra de componentes e insumos', 'CUSTO', 2),
    ('Custos de mercadoria', 'Banho e acabamento (terceirizados)', 'CUSTO', 3),
    ('Custos de mercadoria', 'Frete sobre compras', 'CUSTO', 4),
    ('Custos de mercadoria', 'Perdas e diferenças de consignação', 'CUSTO', 5),
    ('Marketing', 'Anúncios e mídia paga', 'DESPESA_VARIAVEL', 1),
    ('Marketing', 'Fotografia e criação de conteúdo', 'DESPESA_VARIAVEL', 2),
    ('Marketing', 'Catálogos e material impresso', 'DESPESA_VARIAVEL', 3),
    ('Marketing', 'Mostruários e expositores', 'DESPESA_VARIAVEL', 4),
    ('Marketing', 'Brindes e amostras', 'DESPESA_VARIAVEL', 5),
    ('Despesas financeiras', 'Tarifas bancárias', 'DESPESA_VARIAVEL', 1),
    ('Despesas financeiras', 'Juros e multas pagos', 'DESPESA_VARIAVEL', 2),
    ('Despesas financeiras', 'Taxas de cartão e intermediadores', 'DESPESA_VARIAVEL', 3),
    ('Despesas financeiras', 'IOF', 'IMPOSTO', 4),
    ('Despesas financeiras', 'Juros de empréstimos e financiamentos', 'DESPESA_VARIAVEL', 5),
    ('Outras', 'Equipamentos e móveis', 'OUTROS', 1),
    ('Outras', 'Distribuição de lucros', 'OUTROS', 2),
    ('Outras', 'Despesas diversas', 'OUTROS', 3)
) AS s (grupo, nome, tipo, ordem)
JOIN categoria_despesa g ON g.nome = s.grupo AND g.pai_id IS NULL;

-- ---------- permissoes (somente o perfil ADMIN; relogar apos conceder) ----------

INSERT INTO perfis_permissoes (perfil_id, permissao)
SELECT p.id, perm.nome
FROM perfis p
CROSS JOIN (VALUES
    ('CONTA_PAGAR_VISUALIZAR'), ('CONTA_PAGAR_GERENCIAR'), ('CONTA_PAGAR_PAGAR'), ('CONTA_PAGAR_ESTORNAR'),
    ('CADASTRO_FINANCEIRO_GERENCIAR'), ('MOVIMENTACAO_VISUALIZAR'), ('MOVIMENTACAO_GERENCIAR')
) AS perm (nome)
WHERE p.nome = 'ADMIN';
