-- =========================
-- SEED: perfil ADMIN com todas as permissões + usuario "admin" de bootstrap
-- =========================
-- resolve o problema do "primeiro usuário": a partir desta migration a API exige login pra
-- gerenciar usuários (USUARIO_GERENCIAR), então precisa já existir um admin funcional antes da
-- aplicação servir a primeira requisição.
--
-- *** ATENÇÃO: TROQUE ESSA SENHA ASSIM QUE FIZER O PRIMEIRO LOGIN ***
-- usuario: admin / senha: admin123 (hash bcrypt abaixo corresponde a essa senha)
INSERT INTO perfis (nome, created_at, updated_at)
VALUES ('ADMIN', now(), now());

INSERT INTO perfis_permissoes (perfil_id, permissao)
SELECT id, permissao
FROM perfis
CROSS JOIN (VALUES
    ('USUARIO_GERENCIAR'), ('CLIENTE_GERENCIAR'), ('PRODUTO_GERENCIAR'), ('JOIA_GERENCIAR'),
    ('CATEGORIA_GERENCIAR'), ('FORNECEDOR_GERENCIAR'), ('GRUPO_GERENCIAR'), ('NCM_GERENCIAR'),
    ('TRANSPORTADORA_GERENCIAR'), ('EMPRESA_GERENCIAR'), ('MOSTRUARIO_GERENCIAR'), ('COMISSAO_GERENCIAR'),
    ('CONSIGNACAO_GERENCIAR'), ('ACERTO_CONSIGNACAO_GERENCIAR'), ('ACERTO_JOIA_GERENCIAR'),
    ('NOTA_FISCAL_GERENCIAR'), ('NOTA_FISCAL_EMITIR'), ('NOTA_FISCAL_CANCELAR'), ('NOTA_FISCAL_CORRIGIR'),
    ('SPC_CONSULTAR'), ('SPC_REGISTRAR')
) AS todas_permissoes(permissao)
WHERE perfis.nome = 'ADMIN';

INSERT INTO usuarios (id, usuario, senha, perfil_id, ativo, created_at, updated_at)
SELECT gen_random_uuid(), 'admin', '$2b$12$dWb4NSFETNyOIeWHHFJ4UegXfgROa1ngepa2wyrPc.bmWTR.HEe5u', id, true, now(), now()
FROM perfis
WHERE perfis.nome = 'ADMIN';
