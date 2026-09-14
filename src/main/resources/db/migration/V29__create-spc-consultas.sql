-- =========================
-- TABELA: spc_consultas
-- =========================
-- Auditoria de consultas de credito feitas no SPC. So guarda metadados de indexacao
-- (cliente, produto, protocolo, se achou alguma restricao); o resultado completo fica
-- no XML bruto salvo em disco (SpcXmlStorageService) e e reprocessado sob demanda pelo
-- mesmo parser usado na hora da consulta.
CREATE TABLE spc_consultas (
    id UUID PRIMARY KEY,
    cliente_id UUID NOT NULL,
    tipo_pessoa VARCHAR(1) NOT NULL CHECK (tipo_pessoa IN ('F', 'J')),
    codigo_produto INTEGER NOT NULL,
    protocolo_numero BIGINT,
    protocolo_digito INTEGER,
    insumos_opcionais_solicitados VARCHAR(200),
    possui_restricao BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_spc_consultas_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id),
    CONSTRAINT fk_spc_consultas_produto FOREIGN KEY (codigo_produto) REFERENCES spc_produtos(codigo)
);

CREATE INDEX idx_spc_consultas_cliente ON spc_consultas(cliente_id);
