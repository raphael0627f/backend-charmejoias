-- A busca de cidades passa a tratar hifen/apostrofo/pontuacao como espaco ("Mogi-Guacu" acha "Mogi Guacu",
-- "D'Oeste" vira "d oeste"): nome_busca e nome_normalizado sao recalculados com a mesma regra do codigo
-- (TextoUtils.normalizarBusca): sem acento, minusculo, tudo que nao e letra/digito vira um espaco, sem espacos nas pontas.
UPDATE cidades SET
    nome_normalizado = trim(regexp_replace(lower(translate(nome,
        'ÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇÑáàâãäéèêëíìîïóòôõöúùûüçñ',
        'AAAAAEEEEIIIIOOOOOUUUUCNaaaaaeeeeiiiiooooouuuucn')), '[^a-z0-9]+', ' ', 'g')),
    nome_busca = trim(regexp_replace(lower(translate(nome || ' ' || coalesce(municipio_nome, ''),
        'ÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇÑáàâãäéèêëíìîïóòôõöúùûüçñ',
        'AAAAAEEEEIIIIOOOOOUUUUCNaaaaaeeeeiiiiooooouuuucn')), '[^a-z0-9]+', ' ', 'g'));
