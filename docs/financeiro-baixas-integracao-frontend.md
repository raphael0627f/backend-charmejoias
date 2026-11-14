# Financeiro 6.0 — Fase 3: baixa, estorno e movimentação gerada pela baixa

Contrato para o Frontend. Sem prefixo `/api`. Erros no padrão do projeto (campo **`menssage`**), mensagens formais.
Datas `yyyy-MM-dd`; valores monetários com 2 casas. **Não há migration nova**: as tabelas `conta_pagar_baixa` (V65) e `movimentacao_banco` (V64) já existiam.
Complementa `financeiro-contas-pagar-integracao-frontend.md` e `financeiro-cadastros-integracao-frontend.md`.

## Permissões

| Ação | Permissão |
|---|---|
| Baixar (individual e em lote) | `CONTA_PAGAR_BAIXAR` |
| Estornar baixa | `CONTA_PAGAR_ESTORNAR` |
| Consultar (lista, detalhe, extrato) | as de sempre (`CONTA_PAGAR_VISUALIZAR/GERENCIAR/BAIXAR`; extrato: `MOVIMENTACAO_*`) |

As duas permissões são independentes (quem baixa não estorna, e vice-versa). Sem permissão: **403** `"Acesso negado."`.

## Regras da baixa

- Título `ABERTO` ou `PAGO_PARCIAL`. `CANCELADO` e `PAGO` são recusados (400, mensagem formal).
- `valorPrincipal` > 0 e **não maior que o valor em aberto** do título. `juros`, `multa` e `desconto` são opcionais (≥ 0; padrão 0).
- `desconto` não pode superar `principal + juros + multa`.
- **`valorTotal = principal + juros + multa − desconto`** e precisa ser > 0. É o valor que sai do banco.
- **Somente o principal abate o título** (`valorPago` do título soma o principal; juros, multa e desconto não alteram o valor do título).
  O título passa a `PAGO_PARCIAL` ou, quando o principal quita o saldo, `PAGO`.
- `dataPagamento` **não pode ser futura**. Banco **ativo** e forma de pagamento **ativa** são obrigatórios.
- Pagamento dividido (várias formas/bancos) = **várias baixas** no mesmo título.
- Na mesma transação: grava a baixa, atualiza o título, cria a **movimentação de SAÍDA** no banco (data = `dataPagamento`, valor = `valorTotal`,
  origem `BAIXA_CONTA_PAGAR`, descrição com número e descrição do título) e registra o evento `BAIXA` no histórico.
- **Saldo negativo é permitido:** se o banco ficar negativo, a resposta traz o campo `aviso` (texto formal) e a baixa **é concluída**.

## `POST /contas-pagar/{id}/baixas` (201)

```json
{ "dataPagamento": "2026-11-14", "valorPrincipal": 1000.00, "juros": 12.50, "multa": 20.00, "desconto": 5.00,
  "bancoId": 1, "formaPagamentoId": 2, "observacao": "Pago no balcão" }
```
Obrigatórios: `dataPagamento`, `valorPrincipal`, `bancoId`, `formaPagamentoId`. `observacao` até 500 caracteres.

Resposta:
```json
{ "baixa": { "id": 7, "dataPagamento": "2026-11-14", "valorPrincipal": 1000.00, "juros": 12.50, "multa": 20.00, "desconto": 5.00,
             "valorTotal": 1027.50, "banco": {"id":1,"nome":"Caixa"}, "formaPagamento": {"id":2,"nome":"PIX"}, "observacao": "Pago no balcão",
             "estornada": false, "estornoMotivo": null, "estornadaEm": null, "estornadaPor": null,
             "createdAt": "...", "createdBy": {...}, "movimentacaoId": 31 },
  "titulo": { ...detalhe completo do título, atualizado (situacao, valorPago, valorAberto, baixas, eventos)... },
  "aviso": "Atenção: após esta operação, o saldo do banco \"Caixa\" ficou negativo (R$ -27,50)." }
```
`aviso` só existe quando o saldo do banco fica negativo (exiba como alerta, não como erro).

## `POST /contas-pagar/baixas/{baixaId}/estornar` (200)

`{ "motivo": "Pagamento lançado em duplicidade" }` — motivo com pelo menos 5 caracteres.

- Só baixa **não estornada** (400 se já estornada).
- Marca a baixa como estornada (motivo, data/hora e usuário), **reduz o `valorPago`** do título pelo principal da baixa, recalcula a situação
  (`ABERTO` ou `PAGO_PARCIAL`), cria a movimentação **inversa (ENTRADA, origem `ESTORNO_BAIXA`)** e marca a movimentação original como estornada.
  Registra o evento `ESTORNO_BAIXA`.
- **Decisão:** o estorno é **permitido mesmo com o banco inativo** (corrige o histórico). A baixa nunca é apagada.
- Resposta: `{ "baixa": {...estornada...}, "titulo": {...detalhe atualizado...} }`.

## `POST /contas-pagar/baixas-lote` (200)

```json
{ "ids": [12, 13, 14], "dataPagamento": "2026-11-14", "bancoId": 1, "formaPagamentoId": 2, "observacao": "Pagamento do dia" }
```
Paga o **saldo integral em aberto** de cada título (sem juros, multa ou desconto), cada título em transação própria (até 200 ids, repetidos são ignorados).
Um título recusado **nunca derruba o lote**:
```json
{ "baixados": 2, "recusados": 1, "totalPago": 2300.00,
  "resultados": [
    { "id": 12, "numero": 7, "baixado": true, "baixaId": 20, "valorBaixado": 1500.00, "valorAberto": 0.00, "situacao": "PAGO", "menssage": null },
    { "id": 14, "numero": 9, "baixado": false, "baixaId": null, "valorBaixado": null, "valorAberto": null, "situacao": null,
      "menssage": "O título cancelado não pode receber baixa." } ],
  "aviso": "Atenção: após esta operação, o saldo do banco \"Caixa\" ficou negativo (R$ -300,00)." }
```
Data futura, banco/forma inativos ou inexistentes, motivo/lista vazia ou mais de 200 ids → **400 para a requisição inteira**.
`aviso` (opcional) considera o saldo do banco depois de todo o lote.

## Consultas enriquecidas

- **`GET /contas-pagar/{id}`:** `baixas` agora vêm completas (formato acima, inclusive `movimentacaoId`, `estornada`, `estornoMotivo`, `estornadaEm`, `estornadaPor`),
  em ordem de criação (inclui as estornadas). Eventos novos: `BAIXA` e `ESTORNO_BAIXA`, com os valores no `detalhe`.
- **`GET /contas-pagar` (lista):** os filtros `pagamentoDe`/`pagamentoAte` e `bancoId` passam a valer (consideram **só baixas não estornadas**);
  o resumo já devolve `valorPago` e `valorAberto`.
- **`GET /movimentacoes-banco` (extrato):** nas origens `BAIXA_CONTA_PAGAR` e `ESTORNO_BAIXA` cada linha traz `tituloId`, `tituloNumero` e `baixaId`
  para ligar ao título (nas demais origens esses campos são omitidos).
- **Estorno pela rota de movimentações** (`POST /movimentacoes-banco/{id}/estornar`) continua proibido para essas origens (400):
  `"Esta movimentação foi gerada por uma baixa de conta a pagar. Para estornar, utilize o estorno da baixa no título."`

## Concorrência (estratégia)

Operações que alteram dinheiro usam **bloqueio pessimista de linha** (`SELECT ... FOR UPDATE`) na transação:
- Baixa, estorno de baixa, edição, alteração de vencimento e cancelamento: bloqueiam **o título** e depois **o banco**.
- Transferência: bloqueia os **dois bancos em ordem crescente de id**; ajuste e estorno de movimentação: bloqueiam o(s) banco(s) envolvido(s) e
  releem a movimentação já com o bloqueio. Edição do saldo inicial do banco também bloqueia o banco.
- A ordem (título → bancos por id) é sempre a mesma, o que evita impasse (deadlock). Duas baixas simultâneas no mesmo título são
  **serializadas**: a segunda enxerga o saldo já abatido pela primeira e é recusada se exceder (400); nunca se paga o mesmo saldo duas vezes.
  O saldo do banco é sempre a soma das movimentações, portanto nunca diverge. Não há mudança de contrato para o front: basta tratar o 400 normalmente.

## Erros

| Situação | HTTP |
|---|---|
| Título cancelado/pago, valor acima do em aberto, data futura, desconto acima do limite, valor total ≤ 0, banco/forma inativos, motivo curto, baixa já estornada | 400 |
| Sem permissão | 403 |
| Título, banco, forma ou baixa inexistentes | 404 (baixa/título na URL) ou 400 (banco/forma no corpo) |

Exemplo: `{ "timestamp": "...", "status": "400 BAD_REQUEST", "menssage": "O valor principal não pode ser superior ao valor em aberto (R$ 500,00).", "path": "..." }`.
