-- Financeiro 6.0, fase 2: núcleo do Contas a Pagar (títulos, histórico e a tabela de baixas, que a fase 3 passa a usar).
-- Aditiva: tabelas novas e uma chave estrangeira em movimentacao_banco.baixa_id (coluna da V64, ainda vazia).
-- A situação VENCIDO é calculada e nunca gravada. recorrencia_id é coluna simples (a tabela de recorrência vem na fase 4).

CREATE SEQUENCE conta_pagar_numero_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE conta_pagar (
    id BIGSERIAL PRIMARY KEY,
    numero BIGINT NOT NULL,
    descricao VARCHAR(200) NOT NULL,
    favorecido VARCHAR(150),
    categoria_id BIGINT NOT NULL REFERENCES categoria_despesa (id),
    centro_custo_id BIGINT REFERENCES centro_custo (id),
    valor NUMERIC(15, 2) NOT NULL CHECK (valor > 0),
    emissao DATE NOT NULL,
    competencia VARCHAR(7) NOT NULL CHECK (competencia ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    vencimento DATE NOT NULL,
    documento VARCHAR(60),
    forma_prevista_id BIGINT REFERENCES forma_pagamento (id),
    linha_digitavel VARCHAR(60),
    observacao VARCHAR(1000),
    situacao VARCHAR(20) NOT NULL DEFAULT 'ABERTO' CHECK (situacao IN ('ABERTO', 'PAGO_PARCIAL', 'PAGO', 'CANCELADO')),
    valor_pago NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (valor_pago >= 0),
    grupo_parcelamento_id UUID,
    parcela_numero INTEGER,
    parcela_total INTEGER,
    recorrencia_id BIGINT,
    cancelamento_motivo VARCHAR(255),
    cancelamento_em TIMESTAMP,
    cancelado_por_id UUID REFERENCES usuarios (id),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by_id UUID REFERENCES usuarios (id),
    updated_by_id UUID REFERENCES usuarios (id),
    CONSTRAINT uk_conta_pagar_numero UNIQUE (numero)
);
CREATE INDEX idx_conta_pagar_vencimento ON conta_pagar (vencimento);
CREATE INDEX idx_conta_pagar_situacao ON conta_pagar (situacao);
CREATE INDEX idx_conta_pagar_categoria ON conta_pagar (categoria_id);
CREATE INDEX idx_conta_pagar_grupo ON conta_pagar (grupo_parcelamento_id);
CREATE INDEX idx_conta_pagar_favorecido ON conta_pagar (LOWER(favorecido));

CREATE TABLE conta_pagar_evento (
    id BIGSERIAL PRIMARY KEY,
    conta_pagar_id BIGINT NOT NULL REFERENCES conta_pagar (id),
    evento VARCHAR(40) NOT NULL,
    detalhe VARCHAR(2000),
    usuario_id UUID REFERENCES usuarios (id),
    data_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_conta_pagar_evento_conta ON conta_pagar_evento (conta_pagar_id, id);

-- baixas: só a tabela nesta fase (sem endpoints de escrita); valor_total = principal + juros + multa - desconto
CREATE TABLE conta_pagar_baixa (
    id BIGSERIAL PRIMARY KEY,
    conta_pagar_id BIGINT NOT NULL REFERENCES conta_pagar (id),
    data_pagamento DATE NOT NULL,
    valor_principal NUMERIC(15, 2) NOT NULL CHECK (valor_principal > 0),
    juros NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (juros >= 0),
    multa NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (multa >= 0),
    desconto NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (desconto >= 0),
    valor_total NUMERIC(15, 2) NOT NULL,
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
CREATE INDEX idx_conta_pagar_baixa_conta ON conta_pagar_baixa (conta_pagar_id);
CREATE INDEX idx_conta_pagar_baixa_banco ON conta_pagar_baixa (banco_id);

ALTER TABLE movimentacao_banco
    ADD CONSTRAINT fk_movimentacao_banco_baixa FOREIGN KEY (baixa_id) REFERENCES conta_pagar_baixa (id);
