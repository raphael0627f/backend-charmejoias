-- Versao 6.7 (fluxo de caixa e indicadores de consignacao): permissoes de visualizacao (somente leitura), concedidas ao perfil ADMIN
INSERT INTO perfis_permissoes (perfil_id, permissao)
SELECT id, 'FLUXO_CAIXA_VISUALIZAR' FROM perfis WHERE perfis.nome = 'ADMIN';
INSERT INTO perfis_permissoes (perfil_id, permissao)
SELECT id, 'INDICADOR_CONSIGNACAO_VISUALIZAR' FROM perfis WHERE perfis.nome = 'ADMIN';

-- o fluxo de caixa agrega os pagamentos por data (o recebimento ja tem indice por data)
CREATE INDEX IF NOT EXISTS idx_conta_pagar_pagamento_data ON conta_pagar_pagamento (data_pagamento);
