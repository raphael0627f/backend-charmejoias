-- Financeiro 6.1, fase 1 (Contas a Receber): categorias de receita, origens de recebimento no extrato e permissões do módulo.
-- Aditiva: tabela nova, novo conjunto de origens aceito pelo CHECK (sem alterar dados) e permissões para o perfil ADMIN.
-- Título a receber, recebimento e a coluna de ligação da movimentação ao título ficam para as fases 2 e 3.

-- categoria_receita: mesma estrutura de categoria_despesa (grupo > subgrupo, no máximo 2 níveis), sem a classificação "tipo"
CREATE TABLE categoria_receita (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    pai_id BIGINT REFERENCES categoria_receita (id),
    ordem INTEGER NOT NULL DEFAULT 0,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by_id UUID REFERENCES usuarios (id),
    updated_by_id UUID REFERENCES usuarios (id)
);
-- nome único dentro do mesmo pai (grupos compartilham o "pai" nulo)
CREATE UNIQUE INDEX uk_categoria_receita_pai_nome ON categoria_receita (COALESCE(pai_id, 0), LOWER(nome));
CREATE INDEX idx_categoria_receita_pai ON categoria_receita (pai_id);

-- ---------- sementes (editáveis pelo usuário) ----------

INSERT INTO categoria_receita (nome, pai_id, ordem, created_at, updated_at) VALUES
    ('Vendas', NULL, 1, NOW(), NOW()),
    ('Consignação', NULL, 2, NOW(), NOW()),
    ('Serviços', NULL, 3, NOW(), NOW()),
    ('Receitas financeiras', NULL, 4, NOW(), NOW()),
    ('Outras receitas', NULL, 5, NOW(), NOW());

-- subgrupos (nível 2): (grupo, nome, ordem)
INSERT INTO categoria_receita (nome, pai_id, ordem, created_at, updated_at)
SELECT s.nome, g.id, s.ordem, NOW(), NOW()
FROM (VALUES
    ('Vendas', 'Vendas de joias', 1),
    ('Vendas', 'Vendas de semijoias', 2),
    ('Vendas', 'Vendas de acessórios', 3),
    ('Consignação', 'Acertos de consignação', 1),
    ('Consignação', 'Diferenças de acerto', 2),
    ('Serviços', 'Reparos e consertos', 1),
    ('Serviços', 'Personalização e gravação', 2),
    ('Serviços', 'Outros serviços', 3),
    ('Receitas financeiras', 'Juros recebidos', 1),
    ('Receitas financeiras', 'Multas recebidas', 2),
    ('Receitas financeiras', 'Rendimentos de aplicações', 3),
    ('Outras receitas', 'Recuperação de despesas', 1),
    ('Outras receitas', 'Venda de bens do ativo', 2),
    ('Outras receitas', 'Receitas diversas', 3)
) AS s (grupo, nome, ordem)
JOIN categoria_receita g ON g.nome = s.grupo AND g.pai_id IS NULL;

-- ---------- origens de movimentação do banco ----------
-- recria o CHECK de origem incluindo as origens do recebimento (os dados existentes continuam válidos)
ALTER TABLE movimentacao_banco DROP CONSTRAINT movimentacao_banco_origem_check;
ALTER TABLE movimentacao_banco ADD CONSTRAINT movimentacao_banco_origem_check CHECK (origem IN
    ('SALDO_INICIAL', 'PAGAMENTO_CONTA_PAGAR', 'ESTORNO_PAGAMENTO', 'TRANSFERENCIA', 'AJUSTE', 'ESTORNO_MANUAL',
     'RECEBIMENTO_CONTA_RECEBER', 'ESTORNO_RECEBIMENTO'));

-- ---------- permissões (somente o perfil ADMIN; relogar após conceder) ----------

INSERT INTO perfis_permissoes (perfil_id, permissao)
SELECT p.id, perm.nome
FROM perfis p
CROSS JOIN (VALUES
    ('CONTA_RECEBER_VISUALIZAR'), ('CONTA_RECEBER_GERENCIAR'), ('CONTA_RECEBER_RECEBER'), ('CONTA_RECEBER_ESTORNAR')
) AS perm (nome)
WHERE p.nome = 'ADMIN';
