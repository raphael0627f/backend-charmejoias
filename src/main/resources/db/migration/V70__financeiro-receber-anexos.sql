-- Financeiro 6.1, fase 4 (Contas a Receber): anexos/comprovantes e índice da situação do cliente.
-- Aditiva: tabela e índice novos. O conteúdo dos arquivos fica em disco (subpasta receber/{idDoTítulo}); o banco guarda só os metadados.

CREATE TABLE conta_receber_anexo (
    id BIGSERIAL PRIMARY KEY,
    conta_receber_id BIGINT NOT NULL REFERENCES conta_receber (id),
    recebimento_id BIGINT REFERENCES conta_receber_recebimento (id),
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('DOCUMENTO', 'COMPROVANTE')),
    nome_original VARCHAR(120) NOT NULL,
    tipo_conteudo VARCHAR(100) NOT NULL,
    tamanho BIGINT NOT NULL CHECK (tamanho > 0),
    arquivo VARCHAR(255) NOT NULL,
    created_at TIMESTAMP,
    created_by_id UUID REFERENCES usuarios (id)
);
CREATE INDEX idx_conta_receber_anexo_conta ON conta_receber_anexo (conta_receber_id);
CREATE INDEX idx_conta_receber_anexo_recebimento ON conta_receber_anexo (recebimento_id);

-- situação do cliente: títulos em aberto por cliente e vencimento
CREATE INDEX idx_conta_receber_cliente_situacao ON conta_receber (cliente_id, situacao, vencimento);
