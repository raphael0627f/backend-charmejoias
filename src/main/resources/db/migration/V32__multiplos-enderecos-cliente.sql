-- =========================
-- clientes: multiplos enderecos, com um marcado como principal
-- =========================
-- inverte a relacao: antes era clientes.endereco_id (1 endereco por cliente), agora e
-- enderecos.cliente_id (N enderecos por cliente), com exatamente um marcado como principal -
-- e esse principal que os relatorios/NFe/SPC devem usar (Cliente.getEnderecoPrincipal()).
-- a tabela enderecos continua compartilhada com empresas (empresas.endereco_id), que nao muda -
-- enderecos de empresa simplesmente ficam com cliente_id nulo.
ALTER TABLE enderecos ADD COLUMN cliente_id UUID;
ALTER TABLE enderecos ADD COLUMN principal BOOLEAN NOT NULL DEFAULT false;

UPDATE enderecos e
SET cliente_id = c.id, principal = true
FROM clientes c
WHERE c.endereco_id = e.id;

ALTER TABLE clientes DROP COLUMN endereco_id;

ALTER TABLE enderecos
    ADD CONSTRAINT fk_enderecos_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id);

CREATE INDEX idx_enderecos_cliente_id ON enderecos(cliente_id);
