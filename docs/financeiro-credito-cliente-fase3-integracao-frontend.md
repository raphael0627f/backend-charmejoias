# Financeiro 6.3 — Crédito do cliente, fase 3: aplicar o crédito em títulos a receber

Complementa as fases 1 e 2. Migration **V78**: `conta_receber_recebimento` ganha a coluna `origem` (`BANCO` | `CREDITO_CLIENTE`); `banco_id` e `forma_pagamento_id` passam a aceitar nulo **somente** quando `origem = CREDITO_CLIENTE` (CHECK no banco; recebimentos existentes continuam `BANCO` com banco e forma obrigatórios). Erros no formato `RestErrorMenssage` (`menssage`).

## 1. Aplicar crédito
**POST `/contas-receber/{id}/aplicar-credito`** — corpo opcional `{ "valor": 100.00, "observacao": "..." }` → **201** `{ recebimento, titulo, aviso? }` (mesmo formato do recebimento comum).
- Permissões: **`CONTA_RECEBER_RECEBER` e `CREDITO_CLIENTE_APLICAR`** (ambas). Sem uma delas: 403 `Acesso negado.`.
- Título de **cliente cadastrado** (qualquer origem), `ABERTO` ou `RECEBIDO_PARCIAL`; título avulso, cancelado ou quitado: 400.
- `valor` opcional: padrão = **menor entre o crédito disponível do cliente e o valor em aberto do título**. Informado, não pode exceder esse menor valor (400 com a mensagem formal). Parcial permitido. Sem crédito: 400 `O cliente não possui crédito disponível.`.
- Consome os lotes por **FIFO** (id crescente). Cada consumo vira um movimento `SAIDA` no extrato (com `contaReceberId` e `recebimentoId`).
- Efeito no título: `valorRecebido` e situação seguem as regras do recebimento (`RECEBIDO_PARCIAL`/`RECEBIDO`). Evento `CREDITO_APLICADO`.
- **Links de pagamento ativos** do título viram `NAO_UTILIZAR` (evento `LINK_PAGAMENTO_NAO_UTILIZAR`) e a resposta traz `aviso`: `Os links de pagamento ativos deste título foram marcados como Não utilizar e não devem ser enviados ao cliente.` (sem relação com cancelamento de título).
- A data do recebimento é a data atual. **Não** gera movimentação bancária.

## 2. Como o recebimento de crédito aparece
`ContaReceberDetalheDTO.Recebimento` (em `recebimentos[]` do detalhe do título e em `resultado.recebimento`) ganhou **`origem`**:
- `BANCO`: como antes.
- `CREDITO_CLIENTE`: `banco: null`, `formaPagamento: { id: null, nome: "Crédito do cliente" }`, `movimentacaoId: null`, `valorTotal = valorPrincipal`. O front deve tratar `banco` nulo e exibir "Crédito do cliente" como forma, separado do caixa.

## 3. Estorno da aplicação
**POST `/contas-receber/recebimentos/{recebimentoId}/estornar`** `{ "motivo": "..." }` (rota existente). Para recebimento `CREDITO_CLIENTE` exige também `CREDITO_CLIENTE_APLICAR`. Devolve o valor aos **lotes de origem** (movimentos `ESTORNO_SAIDA`), reabre o saldo do título (evento `CREDITO_ESTORNADO`) e **não** mexe em banco. Se o título é de pré-acerto com excedente já destinado, continua bloqueado (fase 1).

## 4. Desfazer destino/lote (regra atualizada)
Lote **utilizado** (mesmo parcialmente) não pode ser desfeito, nem pelo excedente (`/excedentes-acerto/{id}/desfazer-destino`) nem pelo lançamento manual (`/clientes/{id}/credito/lotes/{loteId}/desfazer`): 400 `O crédito já foi utilizado (R$ X). Estorne a aplicação do crédito nos títulos (Contas a Receber) antes de desfazer.` Após estornar a aplicação o lote volta a não utilizado e pode ser desfeito.

## 5. Crédito nunca entra como caixa
- Nenhuma `movimentacao_banco` é criada na aplicação nem no estorno; extrato e saldo dos bancos não mudam.
- Filtros de **recebimento** da lista de títulos (`recebimentoDe`, `recebimentoAte`, `bancoId`) consideram só recebimentos `BANCO`: título quitado apenas com crédito não aparece como "recebido no período".
- `GET /contas-receber/clientes/{id}/situacao`: `recebidoNoPeriodo` e `ultimoRecebimento` consideram só `BANCO`; novo campo **`creditoDisponivel`** (saldo de crédito do cliente). `emAberto`/`vencido` refletem a aplicação (saldo do título cai).
- Atenção: `valorRecebido` do **título** (e os totais da lista de títulos) inclui o valor abatido por crédito, pois mede o quanto do título foi quitado, não o caixa. Para o caixa use movimentações do banco.
