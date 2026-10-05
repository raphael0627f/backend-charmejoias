-- permissão de backup do banco: só o perfil ADMIN (o dump contém todos os dados do sistema)
INSERT INTO perfis_permissoes (perfil_id, permissao)
SELECT id, 'BACKUP_GERENCIAR'
FROM perfis
WHERE perfis.nome = 'ADMIN';
