-- Ordenacao da busca de cidades por relevancia (nome igual / comeca com / demais): precisa do nome da cidade
-- sozinho, minusculo e sem acento (nome_busca tem tambem o municipio pai, no distrito).
-- translate cobre todos os caracteres acentuados presentes nos nomes do IBGE.
ALTER TABLE cidades ADD COLUMN nome_normalizado VARCHAR(255);

UPDATE cidades SET nome_normalizado = lower(translate(nome,
    'ÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇÑáàâãäéèêëíìîïóòôõöúùûüçñ',
    'AAAAAEEEEIIIIOOOOOUUUUCNaaaaaeeeeiiiiooooouuuucn'));

ALTER TABLE cidades ALTER COLUMN nome_normalizado SET NOT NULL;
