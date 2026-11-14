# Financeiro 6.0 — Fase 2: núcleo do Contas a Pagar

Contrato para o Frontend. Sem prefixo `/api`. Erros no padrão do projeto (`RestErrorMenssage`, campo **`menssage`**), mensagens formais.
Datas `yyyy-MM-dd`; valores monetários em número JSON com 2 casas; `competencia` em texto `yyyy-MM`.
Migration: `V65__financeiro-contas-pagar.sql`. Baixa, estorno, recorrência, anexos e lançamento em lote **não** fazem parte desta fase.

## Permissões

- Leitura (GET): `CONTA_PAGAR_VISUALIZAR`, `CONTA_PAGAR_GERENCIAR` ou `CONTA_PAGAR_BAIXAR`.
- Escrita (POST/PUT/PATCH): `CONTA_PAGAR_GERENCIAR`.
- Sem permissão: **403** `"Acesso negado."`.
- **Não existe exclusão física** de título: o título é **cancelado** (fica registrado).

## Enums

- `situacao` (gravada): `ABERTO`, `PAGO_PARCIAL`, `PAGO`, `CANCELADO`.
- `situacaoExibicao`: as mesmas mais **`VENCIDO`** (calculada: `ABERTO` ou `PAGO_PARCIAL` com vencimento anterior a hoje; nunca gravada).
- `intervalo` do parcelamento: `MENSAL`, `QUINZENAL`, `SEMANAL`, `PERSONALIZADO`.
- `evento` do histórico: `CRIACAO`, `EDICAO`, `VENCIMENTO_ALTERADO`, `CANCELAMENTO` (a fase 3 acrescenta os de baixa).

## Item da lista / resumo (`ContaPagarResumoDTO`)

```json
{ "id": 12, "numero": 7, "descricao": "Aluguel loja 2/12", "favorecido": "Imobiliária Centro",
  "categoria": { "id": 8, "nome": "Aluguel", "grupoId": 2, "grupoNome": "Despesas operacionais" },
  "centroCusto": { "id": 1, "nome": "Loja" }, "formaPrevista": { "id": 3, "nome": "Boleto" },
  "valor": 1500.00, "valorPago": 0.00, "valorAberto": 1500.00,
  "emissao": "2026-11-14", "competencia": "2026-12", "vencimento": "2026-12-10", "documento": "NF 123",
  "situacao": "ABERTO", "situacaoExibicao": "ABERTO",
  "grupoParcelamentoId": "e0b1...", "parcelaNumero": 2, "parcelaTotal": 12 }
```
`centroCusto`, `formaPrevista`, `favorecido`, `documento` e os campos de parcela são `null` quando não se aplicam.
`categoria.grupoId/grupoNome` vêm `null` quando a categoria escolhida é um grupo (sem pai).

## `POST /contas-pagar` (201)

Título simples:
```json
{ "descricao": "Energia elétrica", "favorecido": "Companhia de Energia", "categoriaId": 9, "centroCustoId": null,
  "valor": 389.90, "emissao": "2026-11-14", "competencia": "2026-11", "vencimento": "2026-11-25",
  "documento": "Fatura 884", "formaPrevistaId": 3, "linhaDigitavel": "23790.50400 41000.000019 00000.000042 1 85270000010000",
  "observacao": "..." }
```
Obrigatórios: `descricao` (até 200), `categoriaId`, `valor` > 0 (até 2 casas), `vencimento` (ou `parcelamento`, abaixo).
Opcionais: `favorecido` (150), `centroCustoId`, `documento` (60), `formaPrevistaId`, `linhaDigitavel`, `observacao` (1000).
Padrões: `emissao` = hoje; `competencia` = mês do vencimento. Categoria, centro e forma devem **existir e estar ativos** (senão 400).
A categoria pode ser grupo ou subgrupo.

Título parcelado — acrescente o bloco `parcelamento` (`valor` é o **total**):
```json
{ "descricao": "Compra de mostruários", "categoriaId": 28, "valor": 1000.00,
  "parcelamento": { "quantidade": 12, "primeiroVencimento": "2026-12-31", "intervalo": "MENSAL" } }
```
- `quantidade`: 2 a 120.
- `MENSAL`: mesmo dia do mês, ajustando o fim de mês (dia 31 vira 30/28 conforme o mês, sem “acumular” o ajuste).
- `QUINZENAL`: a cada 15 dias. `SEMANAL`: a cada 7 dias.
- `PERSONALIZADO`: informe `datas` (exatamente `quantidade` datas); `primeiroVencimento` é ignorado.
- `valores` (opcional): exatamente `quantidade` valores > 0 cuja **soma é igual ao total**. Omitido: valores iguais (arredondados para baixo em 2 casas)
  e a **diferença de centavos vai para a última parcela** (ex.: 1000,00 em 12x = 11 × 83,33 + 83,37).
- Cada parcela é um título; a descrição ganha o sufixo `" n/N"` (se a descrição já terminar em `n/N`, o sufixo é **substituído**, não duplicado).
  Todas ficam ligadas por `grupoParcelamentoId`.
- `competencia` informada vale para todas as parcelas; omitida, cada parcela usa o mês do seu vencimento.
- `linhaDigitavel` só é aceita em título simples (400 no parcelado); `documento` e `favorecido` valem para todas as parcelas.

Resposta (simples ou parcelado):
```json
{ "grupoParcelamentoId": null, "titulos": [ { ...ContaPagarResumoDTO... } ] }
```

## `GET /contas-pagar` (lista paginada)

Parâmetros (todos opcionais; os de data em `yyyy-MM-dd`):
`vencimentoDe`, `vencimentoAte`, `emissaoDe`, `emissaoAte`, `pagamentoDe`, `pagamentoAte` (datas das baixas ativas; sem baixas ainda, devolve vazio),
`situacao` (repetível: `situacao=ABERTO&situacao=VENCIDO`), `busca` (descrição, documento, favorecido ou **número**), `categoriaId`
(se for um grupo, **inclui os subgrupos**), `centroCustoId`, `formaPrevistaId`, `bancoId` (banco das baixas ativas), `valorMin`, `valorMax`,
`ordenar` (`vencimento` padrão, `valor`, `numero`, `favorecido`, `situacao`), `direcao` (`asc` padrão, `desc`), `page` (0), `size` (20, máx. 200).

- **Período padrão:** se nenhum filtro de data (vencimento, emissão ou pagamento), `busca` nem `situacao` for informado, aplica-se o **mês atual** no vencimento.
  O período efetivamente aplicado volta em `periodoVencimento`.
- **Situação:** os valores `ABERTO`, `PAGO_PARCIAL`, `PAGO`, `CANCELADO` filtram pela situação gravada (um título aberto e atrasado também aparece em `ABERTO`);
  `VENCIDO` filtra os abertos/parciais com vencimento anterior a hoje. Vários valores se combinam com “ou”.
- **Atalhos do front:** Hoje = `vencimentoDe=vencimentoAte=hoje`; 7/30 dias = `vencimentoDe=hoje&vencimentoAte=hoje+N`; Vencidas = `situacao=VENCIDO` (sem período, ou seja, de qualquer data).

Resposta:
```json
{ "content": [ {ResumoDTO}, ... ], "page": 0, "size": 20, "totalElements": 31, "totalPages": 2,
  "periodoVencimento": { "de": "2026-11-01", "ate": "2026-11-30" },
  "totais": { "quantidade": 31, "valorTotal": 12500.00, "valorPago": 0.00, "valorAberto": 12500.00, "valorVencido": 800.00 } }
```
Os **totais valem para todo o filtro** (não só para a página). `valorTotal` e `valorAberto` desconsideram os cancelados; `valorAberto` = valor − pago
dos títulos `ABERTO`/`PAGO_PARCIAL`; `valorVencido` = parte do aberto com vencimento anterior a hoje; `quantidade` conta todos os títulos filtrados.

## `GET /contas-pagar/{id}`

```json
{ "id": 12, "numero": 7, "descricao": "...", "favorecido": "...", "categoria": {...}, "centroCusto": {...}, "formaPrevista": {...},
  "valor": 1500.00, "valorPago": 0.00, "valorAberto": 1500.00, "emissao": "...", "competencia": "2026-12", "vencimento": "...",
  "documento": "...", "linhaDigitavel": "23790504004100000001900000000421852700000100", "observacao": "...",
  "situacao": "ABERTO", "situacaoExibicao": "VENCIDO",
  "grupoParcelamentoId": "...", "parcelaNumero": 2, "parcelaTotal": 12, "recorrenciaId": null,
  "cancelamento": { "motivo": "...", "em": "2026-11-14T10:00:00", "por": {usuario} },
  "baixas": [], "parcelas": [ {ResumoDTO das parcelas do mesmo grupo, em ordem} ],
  "eventos": [ { "id": 1, "evento": "CRIACAO", "detalhe": "...", "usuario": {...}, "dataHora": "..." } ],
  "createdAt": "...", "updatedAt": "...", "createdBy": {...}, "updatedBy": {...} }
```
`cancelamento` é `null` se o título não foi cancelado. `baixas` vem **vazia** nesta fase (a fase 3 define o formato de cada item). `parcelas` vem vazia para título sem grupo.
`linhaDigitavel` é devolvida **normalizada** (somente dígitos). `eventos` em ordem cronológica.

## `PUT /contas-pagar/{id}`

Mesmo corpo do POST simples (sem `parcelamento`). Regras:
- Sem baixas: edição livre.
- Com baixa (parcial): `valor` não pode ficar abaixo do valor já pago (400). Se o novo valor igualar o pago, o título passa a `PAGO`.
- `PAGO`: só a `observacao` pode mudar; qualquer outro campo diferente → 400. `CANCELADO` não pode ser editado (400).
- Editar uma parcela não altera as demais. Cada edição gera evento `EDICAO` com os campos alterados (antes → depois).

## `PATCH /contas-pagar/{id}/vencimento`

`{ "novoVencimento": "2026-12-20", "motivo": "Negociação com o fornecedor" }` — motivo com pelo menos 5 caracteres; não se aplica a `PAGO` nem `CANCELADO` (400);
mesma data (400). Gera evento `VENCIMENTO_ALTERADO` (data antiga, nova e motivo). Resposta: o detalhe atualizado.

## `POST /contas-pagar/{id}/cancelar`

`{ "motivo": "Lançamento em duplicidade" }` (mínimo 5). Só título **sem baixas ativas** (400: estorne antes) e não cancelado. Fica `CANCELADO`, com motivo, data e usuário.
Resposta: o detalhe.

## `POST /contas-pagar/cancelar-lote`

`{ "ids": [12, 13, 14], "motivo": "Compra cancelada" }` (até 200 ids). O lote **nunca falha inteiro**:
```json
{ "cancelados": 2, "recusados": 1,
  "resultados": [ { "id": 12, "numero": 7, "cancelado": true, "menssage": null },
                  { "id": 14, "numero": 9, "cancelado": false, "menssage": "O título já está cancelado." } ] }
```
Motivo inválido ou lista vazia → 400 para a requisição inteira.

## `GET /contas-pagar/favorecidos?busca=`

Nomes de favorecidos já usados (contém, sem diferenciar maiúsculas), ordenados por quantidade de uso e depois por nome, **no máximo 10**: `["Companhia de Energia", ...]`.
Sem `busca`, devolve os 10 mais usados.

## Linha digitável (opcional)

Aceita somente dígitos, pontos e espaços (qualquer outro caractere → 400). É normalizada para dígitos e validada:
**47 dígitos** = boleto bancário (dígitos verificadores dos 3 campos e o geral); **48 dígitos** = arrecadação (começa em 8; dígito verificador de cada bloco).
Outro tamanho, ou dígito verificador incorreto → 400 com mensagem formal. Em branco = não informada.

## Erros

| Situação | HTTP |
|---|---|
| Campo obrigatório ausente, valor ≤ 0, categoria/centro/forma inativos ou inexistentes, parcelamento inválido, linha digitável inválida, motivo curto, regra de edição/cancelamento violada | 400 |
| Sem permissão | 403 |
| Título inexistente | 404 |

Exemplo: `{ "timestamp": "...", "status": "400 BAD_REQUEST", "menssage": "A soma dos valores das parcelas deve ser igual ao valor total.", "path": "..." }`.
