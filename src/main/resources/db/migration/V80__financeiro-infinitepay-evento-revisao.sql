-- Financeiro 3B (baixa automatica InfinitePay), fase 2: eventos recebidos do receptor e fila de revisao.
-- Somente tabela nova e permissoes novas para o perfil ADMIN; nada existente e alterado.
-- Cada evento (webhook) e guardado com o payload bruto (diagnostico) e e idempotente por (transaction_nsu, evento).

CREATE TABLE infinitepay_evento (
    id BIGSERIAL PRIMARY KEY,
    transaction_nsu VARCHAR(120) NOT NULL,
    evento VARCHAR(40) NOT NULL,
    order_nsu VARCHAR(60),
    link_id BIGINT REFERENCES conta_receber_link_pagamento (id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('AGUARDANDO', 'RECEBIDO', 'PROCESSADO', 'REVISAO', 'IGNORADO', 'ERRO_TEMPORARIO')),
    motivo VARCHAR(500),
    payload VARCHAR(16000),
    amount_centavos BIGINT,
    paid_amount_centavos BIGINT,
    capture_method VARCHAR(40),
    recebimento_id BIGINT REFERENCES conta_receber_recebimento (id),
    tentativas INTEGER NOT NULL DEFAULT 0,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processado_em TIMESTAMP,
    resolvido_por_id UUID REFERENCES usuarios (id),
    resolvido_em TIMESTAMP,
    resolucao VARCHAR(500),
    CONSTRAINT uk_infinitepay_evento UNIQUE (transaction_nsu, evento)
);
CREATE INDEX idx_infinitepay_evento_status ON infinitepay_evento (status);
CREATE INDEX idx_infinitepay_evento_order_nsu ON infinitepay_evento (order_nsu);
CREATE INDEX idx_infinitepay_evento_link ON infinitepay_evento (link_id);

INSERT INTO perfis_permissoes (perfil_id, permissao)
SELECT id, 'INFINITEPAY_REVISAO_VISUALIZAR' FROM perfis WHERE perfis.nome = 'ADMIN';
INSERT INTO perfis_permissoes (perfil_id, permissao)
SELECT id, 'INFINITEPAY_REVISAO_RESOLVER' FROM perfis WHERE perfis.nome = 'ADMIN';
