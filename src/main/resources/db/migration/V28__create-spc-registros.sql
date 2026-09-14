-- =========================
-- TABELA: spc_registros
-- =========================
-- Auditoria de inclusoes e exclusoes de devedor feitas no SPC a partir do sistema.
-- Nao ha vinculo obrigatorio entre exclusao e uma inclusao previa feita pelo app
-- (o cliente pode ter sido incluido manualmente fora do sistema).
CREATE TABLE spc_registros (
    id UUID PRIMARY KEY,
    cliente_id UUID NOT NULL,
    tipo_operacao VARCHAR(20) NOT NULL CHECK (tipo_operacao IN ('INCLUSAO', 'EXCLUSAO')),
    tipo_pessoa VARCHAR(1) NOT NULL CHECK (tipo_pessoa IN ('F', 'J')),
    numero_contrato VARCHAR(100) NOT NULL,
    data_compra DATE,
    data_vencimento DATE NOT NULL,
    codigo_tipo_devedor VARCHAR(1) NOT NULL,
    valor_debito NUMERIC(10,2),
    natureza_inclusao_id INTEGER,
    motivo_exclusao_id INTEGER,
    mensagem_retorno VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_spc_registros_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id),
    CONSTRAINT fk_spc_registros_natureza_inclusao FOREIGN KEY (natureza_inclusao_id) REFERENCES spc_naturezas_inclusao(id),
    CONSTRAINT fk_spc_registros_motivo_exclusao FOREIGN KEY (motivo_exclusao_id) REFERENCES spc_motivos_exclusao(id)
);

CREATE INDEX idx_spc_registros_cliente ON spc_registros(cliente_id);
