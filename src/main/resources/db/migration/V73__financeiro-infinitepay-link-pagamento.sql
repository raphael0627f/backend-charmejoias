-- Financeiro 3A (Contas a Receber): link de pagamento InfinitePay, modalidade manual.
-- Aditiva: duas tabelas novas e uma permissao nova para o perfil ADMIN. A chave da API fica criptografada (AES-GCM) e nunca sai pela API.

-- configuracao: UMA linha (singleton, id = 1)
CREATE TABLE infinitepay_configuracao (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    handle VARCHAR(60),
    api_key_criptografada TEXT,
    validade_dias INTEGER NOT NULL DEFAULT 7 CHECK (validade_dias BETWEEN 0 AND 365),
    pagamento_unico BOOLEAN NOT NULL DEFAULT TRUE,
    assured_installments INTEGER NOT NULL DEFAULT 0 CHECK (assured_installments BETWEEN 0 AND 12),
    pix_habilitado BOOLEAN NOT NULL DEFAULT TRUE,
    desconto_pix NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (desconto_pix >= 0 AND desconto_pix < 100),
    desconto_cartao NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (desconto_cartao >= 0 AND desconto_cartao < 100),
    exige_endereco BOOLEAN NOT NULL DEFAULT FALSE,
    texto_whatsapp VARCHAR(2000),
    texto_email VARCHAR(2000),
    atualizado_em TIMESTAMP,
    atualizado_por_id UUID REFERENCES usuarios (id)
);

CREATE TABLE conta_receber_link_pagamento (
    id BIGSERIAL PRIMARY KEY,
    conta_receber_id BIGINT NOT NULL REFERENCES conta_receber (id),
    order_nsu VARCHAR(60) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    valor NUMERIC(15, 2) NOT NULL CHECK (valor >= 1),
    descricao VARCHAR(200) NOT NULL,
    validade DATE,
    pagamento_unico BOOLEAN NOT NULL,
    assured_installments INTEGER NOT NULL CHECK (assured_installments BETWEEN 0 AND 12),
    pix_habilitado BOOLEAN NOT NULL,
    desconto_pix NUMERIC(5, 2) NOT NULL DEFAULT 0,
    desconto_cartao NUMERIC(5, 2) NOT NULL DEFAULT 0,
    exige_endereco BOOLEAN NOT NULL,
    cliente_nome VARCHAR(200),
    cliente_email VARCHAR(200),
    cliente_telefone VARCHAR(20),
    status VARCHAR(40) NOT NULL CHECK (status IN ('ATIVO', 'PAGO_MANUAL', 'ENCERRADO_TITULO_RECEBIDO', 'TITULO_CANCELADO', 'OBSOLETO')),
    recebimento_id BIGINT REFERENCES conta_receber_recebimento (id),
    resposta_bruta VARCHAR(1000),
    criado_por_id UUID REFERENCES usuarios (id),
    criado_em TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP,
    CONSTRAINT uk_link_pagamento_order_nsu UNIQUE (order_nsu)
);

CREATE INDEX idx_link_pagamento_titulo ON conta_receber_link_pagamento (conta_receber_id, status);

-- so o perfil ADMIN (mesmo padrao da configuracao de e-mail): a configuracao guarda a chave da API
INSERT INTO perfis_permissoes (perfil_id, permissao)
SELECT id, 'INFINITEPAY_GERENCIAR'
FROM perfis
WHERE perfis.nome = 'ADMIN';
