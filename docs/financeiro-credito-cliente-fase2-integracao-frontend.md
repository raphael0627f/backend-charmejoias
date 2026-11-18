# Financeiro 6.3 — Crédito do cliente, fase 2: destino CRÉDITO, lotes, extrato e lançamento manual

Complementa `financeiro-credito-cliente-integracao-frontend.md` (fase 1). Migration **V77** (tabelas novas `credito_cliente_lote` e `credito_cliente_movimento`; nada existente foi alterado). Erros no formato `RestErrorMenssage` (`menssage`). Crédito **sem validade**. Nesta fase o crédito ainda **não pode ser aplicado** em títulos (fase 3) e o destino **devolução** ainda não existe (fase 4: `destino: "DEVOLUCAO"` responde 400).

Permissões (fase 1, relogar): consulta = qualquer entre `CREDITO_CLIENTE_VISUALIZAR/GERENCIAR/APLICAR`; escrita desta fase = `CREDITO_CLIENTE_GERENCIAR`. Sem permissão: 403 `Acesso negado.`.

## 1. Destinar o excedente do acerto como crédito
- **POST `/excedentes-acerto/{id}/destinar`** `{ "destino": "CREDITO" }` → 200 com o mesmo corpo de `GET /excedentes-acerto/{id}` (`{ excedente, eventos }`).
  Só excedente `A_DESTINAR`. Cria um lote ATIVO no valor do excedente e o movimento `ENTRADA`; o excedente passa a `situacao: CREDITO`, `destino: CREDITO`. Evento `EXCEDENTE_DESTINADO_CREDITO`.
  Erros 400: destino ausente/inválido; `DEVOLUCAO` (indisponível); excedente que não está a destinar. 404: excedente inexistente.
- **POST `/excedentes-acerto/{id}/desfazer-destino`** `{ "motivo": "..." }` (5 a 500 caracteres) → 200 `{ excedente, eventos }`.
  Só se o lote não foi utilizado: o lote vira DESFEITO, entra `ESTORNO_ENTRADA` no extrato e o excedente volta a `A_DESTINAR` para nova escolha. Evento `DESTINO_DESFEITO`. Crédito já aplicado (mesmo parcialmente): 400 orientando a estornar a aplicação antes (fase 3).
- Enquanto destinado, estornar recebimento ou cancelar título do pré-acerto continua **bloqueado** (fase 1).
- Concorrência: destinar/desfazer simultâneos do mesmo excedente — apenas um vence, os demais recebem 400.

## 2. Saldo, lotes e extrato do cliente (aba Financeiro do cadastro do cliente)
- **GET `/clientes/{clienteId}/credito`** → `{ saldo, lotes: [Lote] }` (lotes do mais recente ao mais antigo, inclusive DESFEITOS).
  `Lote`: `{ id, origemTipo (EXCEDENTE_ACERTO|LANCAMENTO_MANUAL), origemId (id do excedente; nulo no manual), valorOriginal, valorUtilizado, disponivel, situacao (ATIVO|DESFEITO), motivo, criadoEm, criadoPor }`.
  `saldo` = soma do disponível dos lotes ATIVOS.
- **GET `/clientes/{clienteId}/credito/extrato?de=AAAA-MM-DD&ate=AAAA-MM-DD&page=0&size=20`** → `{ saldoAtual, content: [Movimento], page, size, totalElements, totalPages }`, do mais recente ao mais antigo.
  `Movimento`: `{ id, tipo (ENTRADA|SAIDA|ESTORNO_SAIDA|ESTORNO_ENTRADA), valor, loteId, contaReceberId, recebimentoId, motivo, usuario, dataHora, saldoApos }`. O extrato é append-only; correções entram como estorno. `saldoApos` é o saldo do cliente após o movimento (independe do filtro de datas). Cliente inexistente: 404.

### Origem do lote (campos adicionais)
`Lote` e `Movimento` trazem também **`acertoId`** e **`consignacaoId`** (acerto e consignação de origem do excedente; nulos no lançamento manual) para exibir "Excedente do acerto Nº <acertoId> (consignação Nº <consignacaoId>)" em vez do id do excedente (`origemId`). A descrição (`motivo`) das novas entradas segue esse mesmo texto: `Excedente do acerto Nº X (consignação Nº Y) destinado como crédito.` Movimentos antigos mantêm a descrição gravada (`nº` minúsculo, sem consignação); use os campos novos. **Período invertido** no extrato (`ate` anterior a `de`) responde 400: `A data inicial do período não pode ser posterior à data final.`

## 3. Lançamento manual (ajuste/benefício, sem movimentação bancária)
- **POST `/clientes/{clienteId}/credito/lancamentos`** `{ "valor": 75.50, "motivo": "..." }` → 201 `Lote`. `valor` > 0; `motivo` obrigatório (5 a 500 caracteres). Não gera movimentação de banco.
- **POST `/clientes/{clienteId}/credito/lotes/{loteId}/desfazer`** `{ "motivo": "..." }` → 200 `Lote` (DESFEITO). Somente lote **manual** não utilizado; lote de excedente de acerto deve ser desfeito pelo excedente (400 com orientação); lote de outro cliente: 404; já desfeito: 400.
