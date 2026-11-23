-- Financeiro 3B, fase 2: modo automatico por link e configuracao do receptor (Cloudflare).
-- Colunas novas e anulaveis/com padrao seguro: links existentes seguem MANUAL; a baixa automatica nasce DESLIGADA.
-- Sem janela de horario: a busca da fila roda a cada N minutos sempre que o servidor estiver ligado.

-- link: modalidade, banco de destino (automatico) e transacao que o pagou; novo status PAGO_AUTOMATICO
ALTER TABLE conta_receber_link_pagamento ADD COLUMN modo VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE conta_receber_link_pagamento ADD CONSTRAINT ck_link_pagamento_modo CHECK (modo IN ('MANUAL', 'AUTOMATICO'));
ALTER TABLE conta_receber_link_pagamento ADD COLUMN banco_id BIGINT REFERENCES banco (id);
ALTER TABLE conta_receber_link_pagamento ADD CONSTRAINT ck_link_pagamento_banco_automatico CHECK (modo = 'MANUAL' OR banco_id IS NOT NULL);
ALTER TABLE conta_receber_link_pagamento ADD COLUMN transaction_nsu VARCHAR(120);
CREATE INDEX idx_link_pagamento_modo_status ON conta_receber_link_pagamento (modo, status);

-- o CHECK do status foi criado sem nome na V73: localiza pela definicao e recria com PAGO_AUTOMATICO
DO $$
DECLARE nome TEXT;
BEGIN
    FOR nome IN SELECT conname FROM pg_constraint
                WHERE conrelid = 'conta_receber_link_pagamento'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%PAGO_MANUAL%'
    LOOP
        EXECUTE format('ALTER TABLE conta_receber_link_pagamento DROP CONSTRAINT %I', nome);
    END LOOP;
END $$;
ALTER TABLE conta_receber_link_pagamento ADD CONSTRAINT ck_link_pagamento_status
    CHECK (status IN ('ATIVO', 'PAGO_MANUAL', 'PAGO_AUTOMATICO', 'ENCERRADO_TITULO_RECEBIDO', 'TITULO_CANCELADO', 'NAO_UTILIZAR'));

-- configuracao: interruptor da baixa automatica (desligado), receptor e mapeamento do meio de captura para a forma de pagamento
ALTER TABLE infinitepay_configuracao ADD COLUMN baixa_automatica_ativa BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE infinitepay_configuracao ADD COLUMN modo_automatico_padrao BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE infinitepay_configuracao ADD COLUMN receptor_url VARCHAR(200);
ALTER TABLE infinitepay_configuracao ADD COLUMN receptor_token_entrada_criptografado TEXT;
ALTER TABLE infinitepay_configuracao ADD COLUMN receptor_segredo_erp_criptografado TEXT;
ALTER TABLE infinitepay_configuracao ADD COLUMN intervalo_fila_minutos INTEGER NOT NULL DEFAULT 2 CHECK (intervalo_fila_minutos BETWEEN 1 AND 60);
ALTER TABLE infinitepay_configuracao ADD COLUMN banco_padrao_id BIGINT REFERENCES banco (id);
ALTER TABLE infinitepay_configuracao ADD COLUMN forma_pix_id BIGINT REFERENCES forma_pagamento (id);
ALTER TABLE infinitepay_configuracao ADD COLUMN forma_cartao_id BIGINT REFERENCES forma_pagamento (id);
