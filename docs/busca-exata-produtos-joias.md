# Endpoint de busca exata — produtos e joias

## Contexto

Hoje, as telas que bipam código de barras (Consignações, Pré-acertos, Nota Fiscal, Pedidos, Etiquetas) carregam **todo** o catálogo de produtos e joias de uma vez no frontend (`GET /produtos` e `GET /joias` paginados internamente até trazer tudo — no ambiente atual isso é ~9.600 produtos, quase 100 requisições) só pra poder fazer uma busca exata por código/SKU/referência inteiramente no navegador. Isso deixa o campo de bipagem travado por vários segundos toda vez que a tela abre.

A ideia é mover essa busca exata para o backend, mantendo **exatamente o mesmo comportamento** que já existe hoje no frontend (`handleBuscarProduto` em `pre-acerto-form.tsx`, `consignacao-form.tsx`, `nota-fiscal-form.tsx`, `pedido-form.tsx`, `etiqueta-form.tsx` — todas têm uma cópia quase idêntica dessa função).

## Comportamento atual (a ser replicado)

Dado um termo digitado/bipado (`termo`), comparado **case-insensitive**, contra produtos e variações **ativos**:

1. Busca por `sku` (do produto simples OU da variação) **OU** `codigoBarras` (do produto simples OU da variação) — comparação exata (`==`, não `LIKE`).
   - 1 resultado → usa direto.
   - >1 resultado → devolve todos, o frontend mostra uma lista pro usuário escolher (isso pode acontecer com produtos do tipo "com variação", onde cada variação tem seu próprio SKU/código de barras, mas o termo bipado bateu em mais de um por algum motivo).
   - 0 resultados → passo 2.
2. Busca por `referencia` do produto — comparação exata. Como várias variações de um mesmo produto (ou até produtos diferentes) podem compartilhar a mesma referência, isso costuma dar **múltiplos resultados** — é o caso normal de "bipei a referência genérica, agora escolhe a variação certa (tamanho/cor)".
   - 1 resultado → usa direto.
   - >1 resultado → lista pro usuário escolher.
   - 0 resultados → nenhum produto/joia encontrado, mostra erro.

O mesmo vale pra **joias** (`Joia`), só que joias não têm variação nem código de barras — só `codigo` e `referencia`.

## Endpoint proposto

### `GET /produtos/busca-exata?termo={termo}`

- `termo` (obrigatório): string digitada/bipada.
- Filtra só produtos **ativos** (e variações **ativas**, quando for produto com variação).
- Ordem de match, parando no primeiro nível que achar algo:
  1. `sku` do produto simples, ou `sku`/`codigoBarras` da variação, ou `codigoBarras` do produto simples — igualdade exata, case-insensitive.
  2. Se nada no passo 1: `referencia` do produto — igualdade exata, case-insensitive.
- Resposta: **lista** de itens (pode vir vazia, com 1, ou com vários) — o frontend decide o que fazer com 0/1/N, igual já faz hoje.
- Cada item da lista precisa ter os mesmos dados que o frontend já usa pra montar o catálogo local (`ItemCatalogo`), ou seja, reaproveitar `ProdutoResponseDTO` (com o array `variacoes[]` completo) é suficiente — o frontend já sabe "achatar" isso (`flattenProdutos`). Não precisa criar um DTO novo, só filtrar quais produtos entram na resposta.

Exemplo de resposta (reaproveitando o shape de `GET /produtos/{id}`, mas como lista):
```json
[
  {
    "id": "uuid-do-produto",
    "sku": "0014Z",
    "nome": "CJCD CIRCULO ZIRC COLORIDO",
    "referencia": "1234",
    "codigoBarras": null,
    "tipo": "SIMPLES",
    "precoVenda": 149.90,
    "quantidadeEstoque": 12,
    "ativo": true,
    "categoria": { "id": 1, "nome": "CONJUNTO" },
    "variacoes": []
  }
]
```
Para produto com variação (`tipo: "COM_VARIACAO"`), o array `variacoes` vem preenchido normalmente (como já vem hoje em `GET /produtos/{id}`), incluindo `sku`, `codigoBarras`, `precoVenda`, `quantidadeEstoque`, `ativo`, `itensVariacao` de cada variação — isso é o que a tela usa pra montar a lista de desempate.

### `GET /joias/busca-exata?termo={termo}`

Mesma ideia, só que sem a etapa de `codigoBarras` (joia não tem):
1. `codigo` da joia — igualdade exata, case-insensitive.
2. Se nada: `referencia` da joia — igualdade exata, case-insensitive.

Resposta: lista de `JoiaResponseDTO`.

## Fora de escopo

- Não precisa paginação (o volume de matches exatos é sempre pequeno — no máximo algumas variações de um mesmo produto).
- Não precisa mudar o endpoint de listagem paginada existente (`GET /produtos?busca=X`, `GET /joias?busca=X`) — aquele continua servindo pra outras telas que fazem busca "fuzzy" por nome/sku/referência parcial (ex.: telas de cadastro). Este é um endpoint novo, específico pra bipagem.
- Convenção de nome do path seguindo o padrão já usado no controller (`/produtos/proximo-sku`, `/produtos/sku-disponivel`) → por isso `/produtos/busca-exata` e `/joias/busca-exata`.

## O que muda no frontend depois

Depois que esses dois endpoints existirem, eu troco `handleBuscarProduto` (em todos os 5 formulários que têm essa lógica) pra chamar `GET /produtos/busca-exata?termo=X` (ou `/joias/busca-exata`, dependendo do tipo) em vez de filtrar o catálogo já carregado, e removo o `produtoHooks.useListAll()` / `joiaHooks.useListAll()` que hoje trava o campo até carregar tudo. O campo de bipagem passa a ficar liberado assim que a tela abre.
