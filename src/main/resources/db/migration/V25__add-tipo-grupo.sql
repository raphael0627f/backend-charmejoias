-- =========================
-- GRUPOS: separa grupos de cliente e grupos de produto
-- =========================
ALTER TABLE grupos ADD COLUMN tipo VARCHAR(20);

UPDATE grupos g SET tipo = 'CLIENTE'
WHERE EXISTS (SELECT 1 FROM clientes_grupos cg WHERE cg.grupo_id = g.id);

UPDATE grupos g SET tipo = 'PRODUTO'
WHERE tipo IS NULL AND (
    EXISTS (SELECT 1 FROM produtos_grupos pg WHERE pg.grupo_id = g.id)
    OR EXISTS (SELECT 1 FROM joias_grupos jg WHERE jg.grupo_id = g.id)
);

-- grupos cadastrados mas ainda nao vinculados a nada: assume PRODUTO por padrao
UPDATE grupos SET tipo = 'PRODUTO' WHERE tipo IS NULL;

ALTER TABLE grupos ALTER COLUMN tipo SET NOT NULL;
