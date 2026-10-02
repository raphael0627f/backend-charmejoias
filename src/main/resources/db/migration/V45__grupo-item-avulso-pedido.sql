-- grupo (departamento) explícito para itens avulsos (sem produto/joia cadastrado) do
-- pedido, usado no relatório de separação em vez de cair sempre em "Sem categoria"
ALTER TABLE pedidos_produtos ADD COLUMN grupo_id BIGINT REFERENCES grupos(id);
