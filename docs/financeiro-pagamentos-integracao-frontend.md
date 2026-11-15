# Financeiro 6.0 — Fase 3: pagamento, estorno e movimentação gerada pelo pagamento

Contrato para o Frontend. Sem prefixo `/api`. Erros no padrão do projeto (campo **`menssage`**), mensagens formais.
Datas `yyyy-MM-dd`; valores monetários com 2 casas. **Não há migration nova**: as tabelas `conta_pagar_pagamento` (V65) e `movimentacao_banco` (V64) já existiam.
Complementa `financeiro-contas-pagar-integracao-frontend.md` e `financeiro-cadastros-integracao-frontend.md`.

## ATENÇÃO: troca de vocabulário "baixa" → "pagamento" (mapa antigo → novo)

A ação "baixar" passou a se chamar **pagar** e o registro "baixa" passou a se chamar **pagamento**. As migrations V64 a V66 ainda não haviam sido
publicadas e foram ajustadas diretamente. A 8080 de desenvolvimento já está com o novo contrato. **Nada além dos nomes mudou** (regras, valores e fluxo são os mesmos).

**Permissão:** `CONTA_PAGAR_BAIXAR` → `CONTA_PAGAR_PAGAR`. O perfil ADMIN do banco de desenvolvimento já foi atualizado; **é preciso fazer login novamente**
para o token trazer a permissão nova. `CONTA_PAGAR_ESTORNAR` permanece. **Observação:** `CONTA_PAGAR_ESTORNAR` isolada **não concede acesso às telas** de Contas a Pagar
(as consultas exigem `CONTA_PAGAR_VISUALIZAR`, `CONTA_PAGAR_GERENCIAR` ou `CONTA_PAGAR_PAGAR`); conceda-a sempre junto com uma dessas.
As permissões de Movimentação (`MOVIMENTACAO_VISUALIZAR`/`MOVIMENTACAO_GERENCIAR`) e suas rotas **não mudam**.

| Antes | Depois |
|---|---|
| `POST /contas-pagar/{id}/baixas` | `POST /contas-pagar/{id}/pagamentos` |
| `POST /contas-pagar/baixas/{baixaId}/estornar` | `POST /contas-pagar/pagamentos/{pagamentoId}/estornar` |
| `POST /contas-pagar/baixas-lote` | `POST /contas-pagar/pagamentos-lote` |
| `POST /contas-pagar/baixas/{baixaId}/anexos` | `POST /contas-pagar/pagamentos/{pagamentoId}/anexos` |
| `GET /contas-pagar/{id}` → `baixas: [...]` | `pagamentos: [...]` |
| resposta do pagamento: `{ "baixa": {...}, "titulo": {...}, "aviso" }` | `{ "pagamento": {...}, "titulo": {...}, "aviso" }` |
| anexo: `baixaId` | `pagamentoId` |
| extrato (`GET /movimentacoes-banco`): `baixaId` | `pagamentoId` |
| lote: `baixados`, `recusados`, `totalPago` | `pagos`, `recusados`, `totalPago` |
| item do lote: `baixado`, `baixaId`, `valorBaixado` | `pago`, `pagamentoId`, `valorPago` |
| origem da movimentação `BAIXA_CONTA_PAGAR` | `PAGAMENTO_CONTA_PAGAR` |
| origem da movimentação `ESTORNO_BAIXA` | `ESTORNO_PAGAMENTO` |
| evento do título `BAIXA` | `PAGAMENTO_REGISTRADO` |
| evento do título `ESTORNO_BAIXA` | `PAGAMENTO_ESTORNADO` |

Permanecem: `dataPagamento`, `valorPrincipal`, `juros`, `multa`, `desconto`, `valorTotal`, `estornada`/`estornoMotivo`/`estornadaEm`/`estornadaPor`, `movimentacaoId`,
as situações do título (`ABERTO`, `PAGO_PARCIAL`, `PAGO`, `CANCELADO`), o tipo de anexo `COMPROVANTE`, os filtros `pagamentoDe`/`pagamentoAte`/`bancoId` e o download `GET /contas-pagar/anexos/{id}`.
As mensagens de erro trocam "baixa" por "pagamento" (ex.: "O valor principal não pode ser superior ao valor em aberto (R$ 500,00).").
O texto e os exemplos abaixo já estão no vocabulário novo.

## Permissões

| Ação | Permissão |
|---|---|
| Pagar (individual e em lote) | `CONTA_PAGAR_PAGAR` |
| Estornar pagamento | `CONTA_PAGAR_ESTORNAR` |
| Consultar (lista, detalhe, extrato) | as de sempre (`CONTA_PAGAR_VISUALIZAR/GERENCIAR/PAGAR`; extrato: `MOVIMENTACAO_*`) |

As duas permissões são independentes (quem paga não estorna, e vice-versa). Sem permissão: **403** `"Acesso negado."`.

## Regras do pagamento

- Título `ABERTO` ou `PAGO_PARCIAL`. `CANCELADO` e `PAGO` são recusados (400, mensagem formal).
- `valorPrincipal` > 0 e **não maior que o valor em aberto** do título. `juros`, `multa` e `desconto` são opcionais (≥ 0; padrão 0).
- `desconto` não pode superar `principal + juros + multa`.
- **`valorTotal = principal + juros + multa − desconto`** e precisa ser > 0. É o valor que sai do banco.
- **Somente o principal abate o título** (`valorPago` do título soma o principal; juros, multa e desconto não alteram o valor do título).
  O título passa a `PAGO_PARCIAL` ou, quando o principal quita o saldo, `PAGO`.
- `dataPagamento` **não pode ser futura**. Banco **ativo** e forma de pagamento **ativa** são obrigatórios.
- Pagamento dividido (várias formas/bancos) = **vários pagamentos** no mesmo título.
- Na mesma transação: grava o pagamento, atualiza o título, cria a **movimentação de SAÍDA** no banco (data = `dataPagamento`, valor = `valorTotal`,
  origem `PAGAMENTO_CONTA_PAGAR`, descrição com número e descrição do título) e registra o evento `PAGAMENTO_REGISTRADO` no histórico.
- **Saldo negativo é permitido:** se o banco ficar negativo, a resposta traz o campo `aviso` (texto formal) e o pagamento **é concluído**.

## `POST /contas-pagar/{id}/pagamentos` (201)

```json
{ "dataPagamento": "2026-11-14", "valorPrincipal": 1000.00, "juros": 12.50, "multa": 20.00, "desconto": 5.00,
  "bancoId": 1, "formaPagamentoId": 2, "observacao": "Pago no balcão" }
```
Obrigatórios: `dataPagamento`, `valorPrincipal`, `bancoId`, `formaPagamentoId`. `observacao` até 500 caracteres.

Resposta:
```json
{ "pagamento": { "id": 7, "dataPagamento": "2026-11-14", "valorPrincipal": 1000.00, "juros": 12.50, "multa": 20.00, "desconto": 5.00,
             "valorTotal": 1027.50, "banco": {"id":1,"nome":"Caixa"}, "formaPagamento": {"id":2,"nome":"PIX"}, "observacao": "Pago no balcão",
             "estornada": false, "estornoMotivo": null, "estornadaEm": null, "estornadaPor": null,
             "createdAt": "...", "createdBy": {...}, "movimentacaoId": 31 },
  "titulo": { ...detalhe completo do título, atualizado (situacao, valorPago, valorAberto, pagamentos, eventos)... },
  "aviso": "Atenção: após esta operação, o saldo do banco \"Caixa\" ficou negativo (R$ -27,50)." }
```
`aviso` só existe quando o saldo do banco fica negativo (exiba como alerta, não como erro).

## `POST /contas-pagar/pagamentos/{pagamentoId}/estornar` (200)

`{ "motivo": "Pagamento lançado em duplicidade" }` — motivo com pelo menos 5 caracteres.

- Só pagamento **não estornado** (400 se já estornado).
- Marca o pagamento como estornado (motivo, data/hora e usuário), **reduz o `valorPago`** do título pelo principal do pagamento, recalcula a situação
  (`ABERTO` ou `PAGO_PARCIAL`), cria a movimentação **inversa (ENTRADA, origem `ESTORNO_PAGAMENTO`)** e marca a movimentação original como estornada.
  Registra o evento `PAGAMENTO_ESTORNADO`.
- **Decisão:** o estorno é **permitido mesmo com o banco desativado** (corrige o histórico). O pagamento nunca é apagado.
- Resposta: `{ "pagamento": {...estornado...}, "titulo": {...detalhe atualizado...} }`.

## `POST /contas-pagar/pagamentos-lote` (200)

```json
{ "ids": [12, 13, 14], "dataPagamento": "2026-11-14", "bancoId": 1, "formaPagamentoId": 2, "observacao": "Pagamento do dia" }
```
Paga o **saldo integral em aberto** de cada título (sem juros, multa ou desconto), cada título em transação própria (até 200 ids, repetidos são ignorados).
Um título recusado **nunca interrompe o lote**:
```json
{ "pagos": 2, "recusados": 1, "totalPago": 2300.00,
  "resultados": [
    { "id": 12, "numero": 7, "pago": true, "pagamentoId": 20, "valorPago": 1500.00, "valorAberto": 0.00, "situacao": "PAGO", "menssage": null },
    { "id": 14, "numero": 9, "pago": false, "pagamentoId": null, "valorPago": null, "valorAberto": null, "situacao": null,
      "menssage": "O título cancelado não pode receber pagamento." } ],
  "aviso": "Atenção: após esta operação, o saldo do banco \"Caixa\" ficou negativo (R$ -300,00)." }
```
Data futura, banco/forma desativados ou inexistentes, motivo/lista vazia ou mais de 200 ids → **400 para a requisição inteira**.
`aviso` (opcional) considera o saldo do banco depois de todo o lote.

## Consultas enriquecidas

- **`GET /contas-pagar/{id}`:** `pagamentos` agora vêm completos (formato acima, inclusive `movimentacaoId`, `estornada`, `estornoMotivo`, `estornadaEm`, `estornadaPor`),
  em ordem de criação (inclui os estornados). Eventos novos: `PAGAMENTO_REGISTRADO` e `PAGAMENTO_ESTORNADO`, com os valores no `detalhe`.
- **`GET /contas-pagar` (lista):** os filtros `pagamentoDe`/`pagamentoAte` e `bancoId` passam a valer (consideram **só pagamentos não estornados**);
  o resumo já devolve `valorPago` e `valorAberto`.
- **`GET /movimentacoes-banco` (extrato):** nas origens `PAGAMENTO_CONTA_PAGAR` e `ESTORNO_PAGAMENTO` cada linha traz `tituloId`, `tituloNumero` e `pagamentoId`
  para ligar ao título (nas demais origens esses campos são omitidos).
- **Estorno pela rota de movimentações** (`POST /movimentacoes-banco/{id}/estornar`) continua proibido para essas origens (400):
  `"Esta movimentação foi gerada por um pagamento de conta a pagar. Para estornar, utilize o estorno do pagamento no título."`

## Concorrência (estratégia)

Operações que alteram dinheiro usam **bloqueio pessimista de linha** (`SELECT ... FOR UPDATE`) na transação:
- Pagamento, estorno de pagamento, edição, alteração de vencimento e cancelamento: bloqueiam **o título** e depois **o banco**.
- Transferência: bloqueia os **dois bancos em ordem crescente de id**; ajuste e estorno de movimentação: bloqueiam o(s) banco(s) envolvido(s) e
  releem a movimentação já com o bloqueio. Edição do saldo inicial do banco também bloqueia o banco.
- A ordem (título → bancos por id) é sempre a mesma, o que evita impasse (deadlock). Dois pagamentos simultâneos no mesmo título são
  **serializados**: a segunda enxerga o saldo já abatido pela primeira e é recusada se exceder (400); nunca se paga o mesmo saldo duas vezes.
  O saldo do banco é sempre a soma das movimentações, portanto nunca diverge. Não há mudança de contrato para o front: basta tratar o 400 normalmente.

## Erros

| Situação | HTTP |
|---|---|
| Título cancelado/pago, valor acima do em aberto, data futura, desconto acima do limite, valor total ≤ 0, banco/forma desativados, motivo curto, pagamento já estornado | 400 |
| Sem permissão | 403 |
| Título, banco, forma ou pagamento inexistentes | 404 (pagamento/título na URL) ou 400 (banco/forma no corpo) |

Exemplo: `{ "timestamp": "...", "status": "400 BAD_REQUEST", "menssage": "O valor principal não pode ser superior ao valor em aberto (R$ 500,00).", "path": "..." }`.

## Teto de valores (atualização)

Nenhum valor monetário informado pode ultrapassar **R$ 9.999.999,99**. Acima disso a resposta é **400** com mensagem formal, por exemplo
`"O valor não pode ser superior a R$ 9.999.999,99."` (ou `"O valor de juros não pode ser superior a R$ 9.999.999,99."`).
Vale para: valor do título (simples, parcelado, recorrência, lote e edição, inclusive o reajuste da recorrência), valores personalizados de parcelas,
principal, juros, multa e desconto do pagamento, ajuste, transferência e saldo inicial do banco (em módulo). O front pode limitar o campo a esse teto.

## Confirmação de valor e vencimento no pagamento (título de valor variável)

`POST /contas-pagar/{id}/pagamentos` ganhou dois campos **opcionais** no corpo: `valorConta` (valor real da conta, > 0, teto R$ 9.999.999,99) e
`vencimento` (data real, `aaaa-mm-dd`).

```json
{ "valorConta": 212.35, "vencimento": "2026-12-12", "dataPagamento": "2026-12-11", "valorPrincipal": 212.35,
  "juros": 0, "multa": 0, "desconto": 0, "bancoId": 3, "formaPagamentoId": 2, "observacao": null }
```

Regras:
1. `valorConta` e `vencimento` só são aceitos quando o título está `estimado = true` **e não possui pagamento ativo**; caso contrário **400**
   ("Os dados da conta só podem ser confirmados em título de valor variável ainda não confirmado.").
2. Em título `estimado = true`, `valorConta` é **obrigatório** no pagamento individual (**400**: "Informe o valor da conta deste mês para registrar o pagamento.").
   O `vencimento`, se omitido, mantém o previsto. O vencimento real pode ser anterior ou posterior à data do pagamento; juros e multa continuam informados manualmente.
3. Tudo ocorre na **mesma transação**, sob o bloqueio do título: atualiza valor e vencimento (recalculando o valor em aberto), `estimado = false`, registra o
   evento `DADOS_CONFIRMADOS` e em seguida o pagamento. As validações do pagamento (principal ≤ valor em aberto **já atualizado**, data não futura, banco e forma
   ativos, desconto, total > 0) usam o valor **novo**. Se qualquer validação falhar, **nada é gravado** (inclusive a confirmação).
4. Título pago por inteiro (principal = `valorConta`) fica `PAGO`; com principal menor fica `PAGO_PARCIAL`.
5. **Estorno** do pagamento de um título já confirmado: o título volta a `ABERTO` com o **valor confirmado** e `estimado` continua `false` (os dados já foram confirmados).
   Como a confirmação só acontece junto com o primeiro pagamento ou pela edição antecipada, não existe título estimado com pagamento (ativo ou estornado).
6. **Permissão:** `CONTA_PAGAR_PAGAR` basta para confirmar valor e vencimento ao pagar (não exige `CONTA_PAGAR_GERENCIAR`). Decisão: quem paga a conta é quem recebe o documento real.

### Pagamento em lote
Em `POST /contas-pagar/pagamentos-lote`, títulos com `estimado = true` são **recusados por item** (`pago = false`) com a mensagem
"Título de valor variável: confirme o valor da conta no pagamento individual.", sem afetar os demais.
