-- Financeiro 6.0 fase 4: recorrência de contas a pagar e anexos (documento, boleto e comprovante de pagamento).

CREATE TABLE conta_pagar_recorrencia (
    id BIGSERIAL PRIMARY KEY,
    periodicidade VARCHAR(10) NOT NULL CHECK (periodicidade IN ('SEMANAL', 'MENSAL', 'ANUAL')),
    data_inicio DATE NOT NULL,
    data_fim DATE,
    descricao VARCHAR(200) NOT NULL,
    favorecido VARCHAR(150),
    categoria_id BIGINT NOT NULL REFERENCES categoria_despesa (id),
    centro_custo_id BIGINT REFERENCES centro_custo (id),
    valor NUMERIC(15, 2) NOT NULL CHECK (valor > 0),
    forma_prevista_id BIGINT REFERENCES forma_pagamento (id),
    documento VARCHAR(60),
    observacao VARCHAR(1000),
    -- valor_variavel: contas de valor e vencimento diferentes a cada mês (água, luz, telefone); as ocorrências nascem estimadas
    valor_variavel BOOLEAN NOT NULL DEFAULT FALSE,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    ultima_geracao TIMESTAMP,
    encerrada_em TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by_id UUID REFERENCES usuarios (id),
    updated_by_id UUID REFERENCES usuarios (id),
    CONSTRAINT ck_recorrencia_periodo CHECK (data_fim IS NULL OR data_fim >= data_inicio)
);
CREATE INDEX idx_conta_pagar_recorrencia_ativa ON conta_pagar_recorrencia (ativa);

CREATE TABLE conta_pagar_recorrencia_evento (
    id BIGSERIAL PRIMARY KEY,
    recorrencia_id BIGINT NOT NULL REFERENCES conta_pagar_recorrencia (id),
    evento VARCHAR(40) NOT NULL,
    detalhe VARCHAR(2000),
    usuario_id UUID REFERENCES usuarios (id),
    data_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_conta_pagar_recorrencia_evento ON conta_pagar_recorrencia_evento (recorrencia_id, id);

-- ocorrencia_data = data original da ocorrência na série (não muda se o vencimento do título for alterado): garante a idempotência da geração
ALTER TABLE conta_pagar ADD COLUMN ocorrencia_data DATE;
ALTER TABLE conta_pagar ADD CONSTRAINT fk_conta_pagar_recorrencia FOREIGN KEY (recorrencia_id) REFERENCES conta_pagar_recorrencia (id);
CREATE UNIQUE INDEX uk_conta_pagar_recorrencia_ocorrencia ON conta_pagar (recorrencia_id, ocorrencia_data) WHERE recorrencia_id IS NOT NULL;

-- estimado = valor e vencimento ainda não confirmados (título de recorrência de valor variável); passa a false na confirmação
ALTER TABLE conta_pagar ADD COLUMN estimado BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_conta_pagar_estimado ON conta_pagar (vencimento) WHERE estimado;

-- arquivo = nome do arquivo no diretório de anexos (app.storage.financeiro-anexos-dir), no padrão dos demais arquivos do projeto
CREATE TABLE conta_pagar_anexo (
    id BIGSERIAL PRIMARY KEY,
    conta_pagar_id BIGINT NOT NULL REFERENCES conta_pagar (id),
    pagamento_id BIGINT REFERENCES conta_pagar_pagamento (id),
    tipo VARCHAR(12) NOT NULL CHECK (tipo IN ('DOCUMENTO', 'BOLETO', 'COMPROVANTE')),
    nome_original VARCHAR(120) NOT NULL,
    tipo_conteudo VARCHAR(60) NOT NULL,
    tamanho BIGINT NOT NULL CHECK (tamanho > 0),
    arquivo VARCHAR(200) NOT NULL,
    created_at TIMESTAMP,
    created_by_id UUID REFERENCES usuarios (id)
);
CREATE INDEX idx_conta_pagar_anexo_conta ON conta_pagar_anexo (conta_pagar_id, id);
CREATE INDEX idx_conta_pagar_anexo_pagamento ON conta_pagar_anexo (pagamento_id);
