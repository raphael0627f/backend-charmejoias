-- Desempenho dos indicadores de consignação e do fluxo de caixa (6.7): sem estes índices a consulta de acertos fazia varredura completa
-- de acertos_consignacao_produtos (~31 mil linhas) para cada item de consignação (~30 mil): 31 s em uma cópia dos dados de produção.
-- Só índices: não altera dados.
CREATE INDEX IF NOT EXISTS idx_acertos_consignacao_produtos_acerto ON acertos_consignacao_produtos (acerto_consignacao_id);
CREATE INDEX IF NOT EXISTS idx_consignacoes_produtos_consignacao ON consignacoes_produtos (consignacao_id);
