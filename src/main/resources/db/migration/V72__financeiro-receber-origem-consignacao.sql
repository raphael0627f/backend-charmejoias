-- Financeiro 6.2 (Contas a Receber): título gerado a partir do pré-acerto ou do acerto de consignação.
-- Aditiva: três colunas anuláveis (títulos existentes ficam sem origem), restrições, índices e a categoria de receita Vendas > Consignação.

ALTER TABLE conta_receber ADD COLUMN consignacao_id BIGINT REFERENCES consignacoes (id);
ALTER TABLE conta_receber ADD COLUMN pre_acerto_id BIGINT REFERENCES pre_acertos_consignacao (id);
ALTER TABLE conta_receber ADD COLUMN acerto_id BIGINT REFERENCES acertos_consignacao (id);

-- a origem é o pré-acerto OU o acerto (nunca os dois) e, havendo um deles, a consignação é obrigatória
ALTER TABLE conta_receber ADD CONSTRAINT ck_conta_receber_origem CHECK (
    NOT (pre_acerto_id IS NOT NULL AND acerto_id IS NOT NULL)
    AND ((pre_acerto_id IS NULL AND acerto_id IS NULL) OR consignacao_id IS NOT NULL)
);

CREATE INDEX idx_conta_receber_consignacao ON conta_receber (consignacao_id);
CREATE INDEX idx_conta_receber_pre_acerto ON conta_receber (pre_acerto_id);
CREATE INDEX idx_conta_receber_acerto ON conta_receber (acerto_id);

-- rede de segurança: no máximo um grupo ativo por documento (somente a primeira parcela, ou o título simples, é única entre os não cancelados)
CREATE UNIQUE INDEX uk_conta_receber_pre_acerto_grupo_ativo ON conta_receber (pre_acerto_id)
    WHERE pre_acerto_id IS NOT NULL AND COALESCE(parcela_numero, 1) = 1 AND situacao <> 'CANCELADO';
CREATE UNIQUE INDEX uk_conta_receber_acerto_grupo_ativo ON conta_receber (acerto_id)
    WHERE acerto_id IS NOT NULL AND COALESCE(parcela_numero, 1) = 1 AND situacao <> 'CANCELADO';

-- categoria de receita fixa Vendas > Consignação (idempotente; cria o grupo Vendas apenas se não existir)
INSERT INTO categoria_receita (nome, pai_id, ordem, created_at, updated_at)
SELECT 'Vendas', NULL, COALESCE((SELECT MAX(ordem) FROM categoria_receita WHERE pai_id IS NULL), 0) + 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM categoria_receita WHERE pai_id IS NULL AND LOWER(nome) = 'vendas');

INSERT INTO categoria_receita (nome, pai_id, ordem, created_at, updated_at)
SELECT 'Consignação', g.id, COALESCE((SELECT MAX(c.ordem) FROM categoria_receita c WHERE c.pai_id = g.id), 0) + 1, NOW(), NOW()
FROM categoria_receita g
WHERE g.pai_id IS NULL AND LOWER(g.nome) = 'vendas'
  AND NOT EXISTS (SELECT 1 FROM categoria_receita c WHERE c.pai_id = g.id AND LOWER(c.nome) = LOWER('Consignação'));
