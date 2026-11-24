# Versão 6.7 — Fluxo de Caixa e Indicadores de Consignação: contrato para o Frontend

Branch `feat/fluxo-caixa-6-7`. Sem prefixo `/api`. Erros: `{ "menssage": "..." }` (dois "s"), textos formais. Valores monetários em número JSON com 2 casas; datas `yyyy-MM-dd`; períodos mensais `yyyy-MM`; semanais pela data da segunda-feira (`yyyy-MM-dd`).
Legenda: 🔜 a implementar (contrato fechado; qualquer mudança será avisada).

---

## PARTE 1 — Respostas às dúvidas do levantamento (conferidas no código)

1. **Fonte do REALIZADO (sem duplicar).**
   - Cada recebimento bancário (`ContaReceberRecebimento`, origem `BANCO` ou `INFINITEPAY`) gera **uma** `MovimentacaoBanco` ENTRADA (`recebimentoId`, valor = `valorTotal`, data = `dataRecebimento`); cada pagamento (`ContaPagarPagamento`) gera **uma** SAÍDA (`pagamentoId`, valor = `valorTotal`). Recebimento e movimentação são o **mesmo fato**: somar os dois duplicaria.
   - **Fonte do realizado = as tabelas de recebimento e pagamento** (`conta_receber_recebimento`, `conta_pagar_pagamento`), porque só elas chegam à categoria, ao centro de custo, ao cliente e ao favorecido (a movimentação não tem categoria). Filtros: `estornada = false`; recebimento com `origem <> CREDITO_CLIENTE`; título não cancelado não é necessário filtrar (cancelamento exige estorno antes).
   - **Estorno:** marca `estornada = true` no recebimento/pagamento (sai do realizado) e cria uma movimentação inversa (`ESTORNO_*`); por isso o realizado exclui os estornados e **não** usa movimentação.
   - **Transferências entre bancos:** só existem como `MovimentacaoBanco` (origem `TRANSFERENCIA`); como o realizado não parte da movimentação, ficam naturalmente fora de entradas/saídas. Também fora: `SALDO_INICIAL` e `AJUSTE`.
   - **Saldos:** vêm de `MovimentacaoBanco` (única fonte de saldo, já usada em Bancos). Conciliação: `saldoFinal = saldoInicial + entradas realizadas − saídas realizadas + outrasMovimentações`, onde `outrasMovimentações` = transferências líquidas + ajustes + estornos manuais do período (campo próprio no resumo, para o front não "perder" diferença).
   - **Crédito do cliente:** aplicar crédito cria recebimento origem `CREDITO_CLIENTE` **sem banco e sem movimentação** (fora do caixa) → **excluído** do realizado (não entra dinheiro). Devolução ao cliente é um título a pagar comum (categoria "Devoluções a clientes"): entra como saída quando paga.
   - **Valor real:** `valorTotal` = principal + juros + multa − desconto; a taxa InfinitePay do cliente (`valorTaxaCliente`) está fora.
2. **Recorrência de Contas a Pagar:** é **materializada** com janela de **12 meses** (`RecorrenciaCalculo.MESES_JANELA = 12`); o agendador diário (03:30) só completa a janela. Logo, os horizontes de 30/60/90 dias (e até 12 meses) já estão como títulos a pagar reais (`ContaPagar` com `recorrenciaId`, inclusive `estimado = true` para valor variável): **a projeção usa os títulos existentes**, sem simular regras. Horizonte além de 12 meses mostrará só o que existir (documentado no resumo: `horizonteMaximoConhecido`).
3. **`AcertoConsignacao.desconto` é VALOR em R$** (`BigDecimal(10,2)`), não percentual; é a **comissão** do acerto. Valor vendido = `getValorTotalProdutosAcertados()` (produtos da consignação menos os devolvidos). Já existe `DocumentoValoresService` (`valorVendido`, `valorComissao`, `valorAPagar`) feito na 6.6: **será reutilizado** (inclusive para pré-acerto). Percentual do desconto = `comissão ÷ vendido × 100` (calculado). Devolvido = `AcertoConsignacaoProduto` (itens devolvidos) × preço do item. **Acerto feito e pago** = `confirmado = true` **e** todos os títulos não cancelados do acerto `RECEBIDO` (e ao menos um título).
4. **Pré-acerto × acerto:** `ContaReceber` tem `preAcerto` e `acerto`. Regra única (mesma da 6.6): por consignação, o **documento é o ACERTO se existir, senão o PRÉ-ACERTO**; títulos do pré-acerto de uma consignação que já tem acerto **não entram** nos indicadores (nunca contar os dois). Nos indicadores de **vendas/comissão/desconto** a base são os **acertos confirmados e pagos**; na **inadimplência**, os títulos (vencidos, não pagos) do documento vigente de cada consignação (acerto; pré-acerto só se não houver acerto).
5. **Cidade/rota:** o cliente não tem FK de cidade: vem do **endereço principal** (`Endereco.ibge` / `municipio`). O filtro `cidadeId` usa `Cidade.ibge` e casa com o `ibge` do endereço principal do cliente; `rotaId` (`AgendaRota`, que agrupa `cidades`) expande para as cidades da rota. Grupo do cliente existe (`clientes_grupos`) mas **não é filtro** da 6.7 (usuário não marcou tipo/grupo).
6. **Permissões:** semeadas por migration em `perfis_permissoes` (padrão V80) e verificadas por `@PreAuthorize("hasAuthority(...)")`. Migration **V84** cria `FLUXO_CAIXA_VISUALIZAR` e `INDICADOR_CONSIGNACAO_VISUALIZAR` para o perfil ADMIN (e inclui as constantes no enum `Permissao`). As permissões vão **dentro do JWT** (`claims.permissoes`): **o usuário precisa sair e entrar de novo** (ou renovar o token) para enxergar. **SaldoVisibilidade** é respeitada: sem permissão de saldo, o fluxo **não** devolve saldos: campos de saldo vêm `null`, `porBanco` vem `null` e `saldosVisiveis = false` (o front oculta/não calcula linha de saldo; entradas e saídas continuam).
7. **Excel/PDF:** Excel com **Apache POI** (`poi-ooxml`), padrão do `PlanejadorRotaXlsxService`; PDF com **JasperReports** (padrão dos relatórios de consignação, `.jrxml` em `resources/reports`). Proposta: um serviço `FluxoCaixaExportacaoService` — XLSX (abas Resumo, Série, Detalhe) e PDF (cabeçalho com período e filtros, cartões, tabela por período e detalhe). Sem biblioteca nova.

Observação (fornecedor): `ContaPagar` não possui chave de fornecedor, só `favorecido` (texto). O filtro é **`favorecido`** (texto, ignora maiúsculas e acentos), com a lista de nomes em `GET /contas-pagar/favorecidos` (já existe). `fornecedorId` **não** é suportado.

---

## PARTE 2 — Fluxo de Caixa 🔜

Permissão: `FLUXO_CAIXA_VISUALIZAR` em todas as rotas. Sem permissão → **403**.
Parâmetros comuns (todos opcionais, exceto onde indicado): `inicio` e `fim` (padrão: 1º e último dia do mês atual; `fim ≥ inicio`; máximo 366 dias), `bancoIds`, `categoriaReceitaIds`, `categoriaDespesaIds`, `centroCustoIds` (listas, repetir o parâmetro ou separar por vírgula), `clienteId` (UUID), `favorecido` (texto).
Atalhos de projeção são do front: 30/60/90 dias = `inicio = hoje`, `fim = hoje + N`.
**Erros 400:** `inicio` posterior a `fim` ("A data inicial não pode ser posterior à data final."), período maior que 366 dias, `visao` inválida ("Visão inválida. Use DIARIO, SEMANAL ou MENSAL."), ids inválidos, `sort` inválido ("Campo de ordenação inválido.").

### Regras de cálculo
- **Realizado:** recebimentos/pagamentos não estornados com data **dentro** do período (valor real; crédito do cliente e transferências fora). Cartão/InfinitePay = data da baixa no ERP.
- **Previsto:** **saldo em aberto** (parcial = só o que falta) dos títulos não cancelados com `vencimento` no período **e ≥ hoje**; títulos estimados entram no previsto e são sinalizados (`estimado`).
- **Atrasados:** títulos com vencimento **anterior a hoje** e saldo em aberto aparecem **em HOJE** como campo próprio (`atrasados`), nunca diluídos nos dias passados e **não** somados ao previsto. Só aparecem se o período contiver **hoje**; caso contrário, `atrasados` vem zerado com a base informada (o resumo de atrasados é sempre a posição de hoje, independente do período, no cartão `atrasados`).
- **Séries** cobrem todos os períodos do intervalo (sem movimento = zeros).
- **Filtro de banco:** aplica ao realizado (banco do recebimento/pagamento) e aos saldos; o **previsto não tem banco** — com `bancoIds` informado, o previsto vem com `previstoSemBanco = true` e os valores previstos **não filtram por banco** (o front deve avisar).

### `GET /fluxo-caixa` — resumo, série, bancos e categorias
`visao` = `DIARIO` (padrão) | `SEMANAL` | `MENSAL`.

```json
{
  "periodo": { "inicio": "2026-11-01", "fim": "2026-11-30", "visao": "DIARIO", "hoje": "2026-11-24" },
  "saldosVisiveis": true,
  "previstoSemBanco": false,
  "resumo": {
    "saldoInicial": 12000.00,
    "entradasRealizadas": 8500.00,
    "entradasPrevistas": 4200.00,
    "saidasRealizadas": 6100.00,
    "saidasPrevistas": 2300.00,
    "outrasMovimentacoes": 0.00,
    "resultadoRealizado": 2400.00,
    "resultadoPrevisto": 1900.00,
    "saldoFinalRealizado": 14400.00,
    "saldoFinalProjetado": 16300.00,
    "atrasadosReceber": { "valor": 950.00, "quantidade": 4 },
    "atrasadosPagar":   { "valor": 300.00, "quantidade": 1 },
    "saidasPrevistasEstimadas": 300.00
  },
  "serie": [
    { "periodo": "2026-11-01", "rotulo": "01/11", "entradasRealizadas": 0.00, "entradasPrevistas": 0.00, "saidasRealizadas": 0.00, "saidasPrevistas": 0.00,
      "atrasadosReceber": 0.00, "atrasadosPagar": 0.00, "saldoAcumuladoRealizado": 12000.00, "saldoAcumuladoProjetado": 12000.00 }
  ],
  "porBanco": [ { "bancoId": 1, "nome": "Banco A", "saldoInicial": 5000.00, "entradasRealizadas": 3000.00, "saidasRealizadas": 1000.00, "outrasMovimentacoes": 0.00, "saldoFinalRealizado": 7000.00 } ],
  "porCategoria": {
    "receitas": [ { "categoriaId": 3, "nome": "Vendas", "realizado": 8000.00, "previsto": 4000.00, "atrasado": 500.00, "percentualDoTotal": 94.1 } ],
    "despesas": [ { "categoriaId": 9, "nome": "Aluguel", "realizado": 3000.00, "previsto": 0.00, "atrasado": 0.00, "percentualDoTotal": 49.2 } ]
  },
  "horizonteMaximoConhecido": "2027-11-24"
}
```
Notas: sem permissão de saldo → `saldosVisiveis=false`, `saldoInicial/saldoFinal*/outrasMovimentacoes` e `saldoAcumulado*` = `null`, `porBanco = null`. `saidasPrevistasEstimadas` = parte das saídas previstas que vem de títulos de valor estimado (recorrência variável). `saldoAcumuladoProjetado` = saldo realizado até hoje + previsto dos dias futuros (atrasados **não** entram no acumulado; ficam em campo próprio). `percentualDoTotal` sobre (realizado + previsto + atrasado) da lista. Categorias seguem o filtro de categoria; "sem categoria" não existe (categoria é obrigatória no título).

### `GET /fluxo-caixa/detalhe` — o que compõe um valor
Parâmetros: filtros comuns **mais** `periodo` (obrigatório: `yyyy-MM-dd` do dia, da segunda-feira da semana ou `yyyy-MM`, conforme `visao`), `visao`, `tipo` (`ENTRADA`|`SAIDA`, obrigatório), `situacao` (`REALIZADO`|`PREVISTO`|`ATRASADO`, obrigatório), `page` (padrão 0), `size` (padrão 20, máximo 100), `sort` (`data` padrão, `valor`, `numero`, `pessoa`, `categoria`; `sort=campo,asc|desc`).
```json
{
  "page": 0, "size": 20, "totalElementos": 37, "totalPaginas": 2, "totalValor": 4200.00,
  "itens": [
    { "tipo": "ENTRADA", "situacao": "PREVISTO", "titulo": { "tipoTitulo": "RECEBER", "id": 1180, "numero": 1180 },
      "descricao": "Consignação 8349 - parcela 1/2", "pessoa": "CAMILA", "categoria": "Vendas", "centroCusto": null,
      "banco": null, "formaPagamento": null, "valor": 100.40, "data": "2026-11-30", "estimado": false, "diasEmAtraso": null }
  ]
}
```
`titulo.tipoTitulo` (`RECEBER`|`PAGAR`) + `id` permitem abrir `/financeiro/contas-receber/{id}` ou `/contas-pagar/{id}`. Em `REALIZADO`, `data` = data do recebimento/pagamento, `banco` e `formaPagamento` preenchidos e `valor` = valor real; em `PREVISTO`/`ATRASADO`, `data` = vencimento, `valor` = saldo em aberto e `diasEmAtraso` (só atrasados).
**Erros:** 400 (`tipo`/`situacao`/`periodo` ausentes ou inválidos, sort inválido).

### `GET /fluxo-caixa/exportar?formato=xlsx|pdf`
Mesmos filtros do resumo + `visao`. Resposta: arquivo (`Content-Disposition: attachment; filename="fluxo-de-caixa_<inicio>_<fim>.xlsx|pdf"`). Conteúdo: cabeçalho (título, período, visão, filtros aplicados, data/hora e usuário), cartões, série por período, quebra por categoria e detalhe (limitado a 5.000 linhas, com aviso no arquivo se truncado). Sem saldos para quem não pode ver. **Erros:** 400 (`formato` inválido: "Formato inválido. Use xlsx ou pdf."), 403.

---

## PARTE 3 — Indicadores de Consignação 🔜

Permissão: `INDICADOR_CONSIGNACAO_VISUALIZAR` em todas as rotas.
Filtros comuns: `inicio`, `fim` (padrão: últimos 12 meses completos até hoje; período aplicado à **data do acerto** nas vendas/comissão/desconto/giro e à **data de pagamento** no prazo/meios; inadimplência é **posição de hoje**, com vencimento até hoje), `clienteId` (UUID), `cidadeId` (Long), `rotaId` (Long).
**Base de cálculo:** todo indicador traz `base` (`{ "acertos": n, "titulos": n }` conforme o caso). Sem dados: valor `null` e base 0 (nunca divisão por zero; o front exibe "dados insuficientes").
**Erros 400:** período inválido, `clienteId`/`cidadeId`/`rotaId` inexistentes ("Cliente não encontrado." etc. → 404), `sort` inválido.

### `GET /indicadores-consignacao/resumo`
```json
{
  "periodo": { "inicio": "2025-12-01", "fim": "2026-11-24" },
  "vendas": {
    "valorVendidoTotal": 52000.00, "comissaoTotal": 9100.00,
    "descontoMedioPercentual": 17.5, "comissaoMediaValor": 455.00, "comissaoMediaPercentual": 17.5,
    "valorVendidoMedioPorAcerto": 2600.00,
    "base": { "acertos": 20 }
  },
  "inadimplencia": {
    "valorTotal": 3200.00, "quantidadeTitulos": 6, "percentualDoReceberVencimento": null,
    "faixas": [ { "faixa": "1-30", "valor": 1200.00, "quantidade": 3 }, { "faixa": "31-60", "valor": 1500.00, "quantidade": 2 }, { "faixa": ">60", "valor": 500.00, "quantidade": 1 } ],
    "base": { "titulos": 6 }
  },
  "prazoRecebimento": { "diasMedioAposVencimento": 4.2, "atrasoMedioDosPagos": 9.8, "pagosNoPrazoPercentual": 62.5, "base": { "titulos": 48 } },
  "giro": { "valorVendido": 52000.00, "valorDevolvido": 18000.00, "percentualVendido": 74.3, "quantidadeVendida": 410, "quantidadeDevolvida": 140, "base": { "acertos": 20 } }
}
```
Definições: **ponderadas pelo vendido** — `descontoMedioPercentual = Σcomissão ÷ Σvendido × 100`, `comissaoMediaPercentual` idem; `comissaoMediaValor = Σcomissão ÷ nº de acertos`; `valorVendidoMedioPorAcerto = Σvendido ÷ nº de acertos` (acertos confirmados e **pagos**). **Inadimplência:** títulos do documento vigente com vencimento < hoje e saldo em aberto; valor = saldo (parcial só o que falta); faixa pelos dias desde o vencimento. **Prazo:** `diasMedioAposVencimento` = média de (data de pagamento − vencimento) dos títulos quitados (pode ser negativo = adiantado), `atrasoMedioDosPagos` = média só dos pagos com atraso (> 0), `pagosNoPrazoPercentual` = % quitados até o vencimento. **Giro:** vendido × devolvido dos acertos do período (`percentualVendido = vendido ÷ (vendido + devolvido)`).

### `GET /indicadores-consignacao/ranking-clientes`
Parâmetros extras: `ordenar` (`vendido` padrão, `inadimplencia`, `prazoMedio`, `cliente`), `direcao` (`desc` padrão), `top` (padrão 10, máximo 100).
```json
{ "itens": [ { "clienteId": "…", "cliente": "CAMILA", "cidade": "Cidade X", "valorVendido": 8000.00, "acertos": 4, "inadimplenciaValor": 300.00, "inadimplenciaTitulos": 1, "prazoMedioDias": 3.5, "baseTitulosPagos": 6 } ], "totalClientes": 25 }
```
### `GET /indicadores-consignacao/evolucao-mensal`
```json
{ "meses": [ { "mes": "2026-10", "valorVendido": 5000.00, "comissao": 900.00, "descontoPercentual": 18.0, "inadimplenciaValor": 400.00, "inadimplenciaTitulos": 2, "acertos": 3 } ], "base": { "acertos": 20 } }
```
Meses sem dados vêm com zeros (série contínua); `inadimplenciaValor` do mês = saldo em aberto hoje dos títulos **com vencimento naquele mês**.
### `GET /indicadores-consignacao/concentracao`
```json
{ "top5": [ { "clienteId": "…", "cliente": "CAMILA", "valorVendido": 8000.00, "percentualDoTotal": 15.4 } ], "percentualTop5": 52.0, "valorVendidoTotal": 52000.00, "base": { "acertos": 20 } }
```
### `GET /indicadores-consignacao/produtos-categorias`
Parâmetros extras: `top` (padrão 5, máximo 50).
```json
{ "produtos": { "melhores": [ { "produtoId": 10, "nome": "Brinco X", "quantidade": 40, "valorVendido": 2400.00 } ], "piores": [ … ] },
  "categorias": { "melhores": [ { "categoriaId": 2, "nome": "Brincos", "quantidade": 120, "valorVendido": 7200.00 } ], "piores": [ … ] }, "base": { "acertos": 20 } }
```
(`piores` = menores valores vendidos entre os que tiveram venda.)
### `GET /indicadores-consignacao/meios-pagamento`
```json
{ "meios": [ { "formaPagamentoId": 2, "nome": "PIX", "quantidade": 30, "valor": 20000.00, "percentualDoValor": 55.0, "taxaClienteAbsorvida": 0.00 },
             { "formaPagamentoId": 5, "nome": "Cartão de crédito", "quantidade": 10, "valor": 9000.00, "percentualDoValor": 25.0, "taxaClienteAbsorvida": 312.50 } ],
  "taxaClienteTotal": 312.50, "base": { "titulos": 48 } }
```
Recebimentos dos títulos de acerto no período (não estornados, banco; crédito do cliente aparece como "Crédito do cliente" com valor e sem taxa). `taxaClienteAbsorvida` = `valor_taxa_cliente` (paga pelo cliente, fora do caixa).

---

## PARTE 4 — Cronograma e pendências
- Etapa 0 (este documento) + migration V84 de permissões.
- Etapa 1: Fluxo (resumo/série/categorias/detalhe/exportação). Etapa 2: Indicadores. O contrato será atualizado com ✅ conforme cada etapa.
- Dúvida registrada: o filtro "fornecedor" é `favorecido` (texto) — ver PARTE 1.
