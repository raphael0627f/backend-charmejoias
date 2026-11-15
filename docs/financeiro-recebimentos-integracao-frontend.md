# Financeiro 6.1 — Fase 3: recebimento, estorno, recebimento em lote e mudanças no extrato

Migration **V69** (tabela `conta_receber_recebimento` e duas colunas novas em `movimentacao_banco`; dados existentes intactos). Sem prefixo `/api`; erros com o campo **`menssage`**.
Datas `yyyy-MM-dd`; valores com 2 casas. Complementa `financeiro-contas-receber-integracao-frontend.md`. Espelha o pagamento do Contas a Pagar (`financeiro-pagamentos-integracao-frontend.md`).
**Fora desta fase:** anexos/comprovantes e situação do cliente (fase 4).

## Permissões

| Ação | Permissão |
| --- | --- |
| Receber (individual e em lote) | `CONTA_RECEBER_RECEBER` |
| Estornar recebimento | `CONTA_RECEBER_ESTORNAR` |
| Consultar (lista, detalhe, clientes) | `CONTA_RECEBER_VISUALIZAR`, `CONTA_RECEBER_GERENCIAR` ou `CONTA_RECEBER_RECEBER` |

Quem só tem `RECEBER` consulta e recebe, mas não gerencia nem estorna. `CONTA_RECEBER_ESTORNAR` isolada não dá acesso às telas (403 na consulta). `VISUALIZAR` não recebe. Sem permissão: **403** antes de qualquer validação.

## Receber — `POST /contas-receber/{id}/recebimentos` (201)
```json
{ "dataRecebimento": "2026-11-16", "valorPrincipal": 300.00, "juros": 5.00, "multa": 0, "desconto": 2.00,
  "bancoId": 3, "formaPagamentoId": 1, "observacao": "Pago no balcão" }
```
Obrigatórios: `dataRecebimento`, `valorPrincipal`, `bancoId`, `formaPagamentoId`. `juros`, `multa` e `desconto` são opcionais (≥ 0, padrão 0; até 2 casas). `observacao` até 500.
- Título `ABERTO` ou `RECEBIDO_PARCIAL`. `CANCELADO` ("O título cancelado não admite recebimento.") e `RECEBIDO` ("O título já está integralmente recebido.") são recusados (400).
- `valorPrincipal` > 0 e **não maior que o valor em aberto**.
- `desconto` não pode superar `principal + juros + multa` (400). **`valorTotal = principal + juros + multa − desconto`**, precisa ser > 0.
- **Somente o principal abate o título** (`valorRecebido`). Situação: `RECEBIDO_PARCIAL` ou, ao quitar o saldo, `RECEBIDO`.
- `dataRecebimento` não pode ser futura. Banco **ativo** e forma **ativa** (400: `... está desativado/desativada.`).
- Recebimento dividido (vários bancos/formas) = **vários recebimentos** no mesmo título.
- Na mesma transação: grava o recebimento, atualiza o título, lança no extrato do banco uma **ENTRADA** (data = `dataRecebimento`, valor = `valorTotal`, origem `RECEBIMENTO_CONTA_RECEBER`,
  descrição `Recebimento do título nº N - descrição - cliente`) e registra o evento `RECEBIMENTO_REGISTRADO`. Entrada não gera aviso de saldo.
- Resposta: `{ "recebimento": Recebimento, "titulo": <detalhe do título>, "aviso": ausente }`.

### Recebimento (no detalhe e nas respostas)
```json
{ "id": 7, "dataRecebimento": "2026-11-16", "valorPrincipal": 300.00, "juros": 5.00, "multa": 0.00, "desconto": 2.00, "valorTotal": 303.00,
  "banco": { "id": 3, "nome": "Caixa" }, "formaPagamento": { "id": 1, "nome": "PIX" }, "observacao": "...",
  "situacao": "ATIVO", "estornado": false, "estornoMotivo": null, "estornadoEm": null, "estornadoPor": null,
  "createdAt": "...", "createdBy": { }, "movimentacaoId": 55 }
```
`situacao`: `ATIVO` ou `ESTORNADO`. `GET /contas-receber/{id}` passa a trazer a lista `recebimentos` (ordem de criação, ativos e estornados), `valorRecebido` e os eventos novos.

## Estornar — `POST /contas-receber/recebimentos/{id}/estornar` — `{ "motivo": "Cheque devolvido" }` (mínimo 5)
- Gera uma **SAÍDA** de estorno no banco (origem `ESTORNO_RECEBIMENTO`, `estornoDeId` = movimentação original; a original fica `estornada`), recalcula `valorRecebido` e a situação
  (volta a `ABERTO` ou `RECEBIDO_PARCIAL`), marca o recebimento `ESTORNADO` (motivo, data, usuário) e registra `RECEBIMENTO_ESTORNADO`.
- Estornar de novo: **400** ("Este recebimento já foi estornado."). Banco desativado: permitido. Recebimento inexistente: 404.
- O estorno **pode deixar o saldo do banco negativo**: é permitido e a resposta traz `"aviso": "Atenção: após esta operação, o saldo do banco \"X\" ficou negativo (R$ -10,00)."`.
- Resposta: `{ "recebimento", "titulo", "aviso" (só quando negativo) }` (200).

## Receber em lote — `POST /contas-receber/recebimentos-lote`
`{ "ids": [..], "dataRecebimento": "...", "bancoId": 3, "formaPagamentoId": 1, "observacao": "..." }` (até 200 ids). Recebe o **saldo integral** de cada título, um recebimento por título em transação própria; os recusados não interrompem os demais.
```json
{ "recebidos": 2, "recusados": 1, "totalRecebido": 800.00,
  "resultados": [ { "id": 1, "numero": 10, "recebido": true, "recebimentoId": 7, "valorRecebido": 500.00, "valorAberto": 0.00, "situacao": "RECEBIDO", "menssage": null },
                  { "id": 2, "numero": 11, "recebido": false, "recebimentoId": null, "valorRecebido": null, "valorAberto": null, "situacao": null, "menssage": "O título cancelado não admite recebimento." } ] }
```
Validações do pedido inteiro (400): ids vazios/nulos/acima de 200, data ausente ou futura, banco/forma ausentes, inexistentes ou desativados.

## Edição e cancelamento com recebimento
- Editar título com recebimento ativo: o valor não pode ficar abaixo do já recebido e o cliente não pode ser trocado (400). `RECEBIDO`: só a observação.
- Cancelar (individual e em lote) com recebimento ativo: **400** "O título possui recebimentos ativos. Estorne os recebimentos antes de cancelar."

## Lista `GET /contas-receber`
`recebimentoDe`, `recebimentoAte` (período de recebimentos **ativos**) e `bancoId` (banco de um recebimento ativo) **passam a funcionar**; `totais.valorRecebido` e `valorAberto` refletem os recebimentos.

## Extrato `GET /movimentacoes-banco` — campos novos (os atuais não mudam)
`MovimentacaoResponseDTO` ganha:
- `tituloTipo`: `"PAGAR"` ou `"RECEBER"` (somente em linhas ligadas a um título; omitido nas demais);
- `recebimentoId`: id do recebimento (linhas `RECEBIMENTO_CONTA_RECEBER` e `ESTORNO_RECEBIMENTO`).

Em linhas do Contas a Receber, **`tituloId` e `tituloNumero` são os do título a receber** (use `tituloTipo` para montar o link: `RECEBER` → `/contas-receber/{tituloId}`, `PAGAR` → `/contas-pagar/{tituloId}`); `pagamentoId` fica ausente. Linhas do Contas a Pagar passam a trazer também `tituloTipo = "PAGAR"`, sem outra mudança.
Movimentações de origem `RECEBIMENTO_CONTA_RECEBER` e `ESTORNO_RECEBIMENTO` **não podem ser estornadas** em `/movimentacoes-banco/.../estornar` (400: use o estorno do recebimento no título).

## Concorrência
O título é bloqueado (`SELECT ... FOR UPDATE`) e depois o banco, sempre nessa ordem. Dois recebimentos simultâneos nunca ultrapassam o valor do título; um duplo estorno simultâneo estorna uma única vez (o segundo recebe 400); lote e recebimento individual concorrentes são serializados por título.

## Erros
| Situação | Status |
| --- | --- |
| Sem token | 401 |
| Sem permissão | 403 |
| Validações (valor, data futura, desconto, banco/forma desativados, título cancelado/recebido, motivo curto, já estornado) | 400 |
| Título ou recebimento não encontrado | 404 |
