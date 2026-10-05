-- mostruario associado ao pedido e opcional: pedidos existentes (e pedidos que nao sao de mostruario)
-- ficam sem
ALTER TABLE pedidos ADD COLUMN mostruario_id BIGINT;

ALTER TABLE pedidos
    ADD CONSTRAINT fk_pedidos_mostruario FOREIGN KEY (mostruario_id) REFERENCES mostruarios(id);
