-- Financeiro 6.1, fase 3 (Contas a Receber): recebimentos e ligação das movimentações do banco ao título a receber.
-- Aditiva: tabela nova e duas colunas anuláveis em movimentacao_banco (dados existentes do Contas a Pagar intactos).
-- valor_total = valor_principal + juros + multa - desconto (entra no banco); somente o principal abate o título.

CREATE TABLE conta_receber_recebimento (
    id BIGSERIAL PRIMARY KEY,
    conta_receber_id BIGINT NOT NULL REFERENCES conta_receber (id),
    data_recebimento DATE NOT NULL,
    valor_principal NUMERIC(15, 2) NOT NULL CHECK (valor_principal > 0),
    juros NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (juros >= 0),
    multa NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (multa >= 0),
    desconto NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (desconto >= 0),
    valor_total NUMERIC(15, 2) NOT NULL CHECK (valor_total > 0),
    banco_id BIGINT NOT NULL REFERENCES banco (id),
    forma_pagamento_id BIGINT NOT NULL REFERENCES forma_pagamento (id),
    observacao VARCHAR(500),
    estornada BOOLEAN NOT NULL DEFAULT FALSE,
    estorno_motivo VARCHAR(255),
    estornada_em TIMESTAMP,
    estornada_por_id UUID REFERENCES usuarios (id),
    created_at TIMESTAMP,
    created_by_id UUID REFERENCES usuarios (id)
);
CREATE INDEX idx_conta_receber_recebimento_conta ON conta_receber_recebimento (conta_receber_id);
CREATE INDEX idx_conta_receber_recebimento_banco ON conta_receber_recebimento (banco_id);
CREATE INDEX idx_conta_receber_recebimento_data ON conta_receber_recebimento (data_recebimento);

ALTER TABLE movimentacao_banco ADD COLUMN conta_receber_id BIGINT;
ALTER TABLE movimentacao_banco ADD COLUMN recebimento_id BIGINT;
ALTER TABLE movimentacao_banco
    ADD CONSTRAINT fk_movimentacao_banco_conta_receber FOREIGN KEY (conta_receber_id) REFERENCES conta_receber (id);
ALTER TABLE movimentacao_banco
    ADD CONSTRAINT fk_movimentacao_banco_recebimento FOREIGN KEY (recebimento_id) REFERENCES conta_receber_recebimento (id);
CREATE INDEX idx_movimentacao_banco_recebimento ON movimentacao_banco (recebimento_id);
