# Financeiro 6.3 — Crédito do cliente, fase 4: devolução ao cliente e cliente como favorecido no Contas a Pagar

Complementa as fases 1 a 3. Migration **V79**: `conta_pagar` ganha `cliente_id` e `excedente_id` (anuláveis; títulos existentes seguem **avulsos**, sem vínculo automático), índice único de **1 título a pagar ativo por excedente** e a categoria de despesa **"Devoluções a clientes"** (grupo de nível 1, semeada). Erros no formato `RestErrorMenssage` (`menssage`).

## 1. Destino DEVOLUCAO do excedente
**POST `/excedentes-acerto/{id}/destinar`** `{ "destino": "DEVOLUCAO", "vencimento": "AAAA-MM-DD" }` → 200 `{ excedente, eventos, devolucao }`.
- Permissões: `CREDITO_CLIENTE_GERENCIAR` **e** `CONTA_PAGAR_GERENCIAR` (sem a segunda: 403 `Acesso negado.`). `destino: "CREDITO"` continua exigindo só a primeira.
- `vencimento` opcional (**padrão = hoje**, editável depois no título). Cria o título a pagar: descrição `DEVOLUÇÃO - CONS. Nº <consignação> - ACERTO Nº <acerto>`, favorecido = nome do cliente, `cliente` e `excedenteId` preenchidos, valor = excedente, **categoria "Devoluções a clientes" automática** (não alterável), forma prevista vazia (a **forma de pagamento é escolhida no pagamento**, como nos demais títulos). O excedente passa a `situacao: DEVOLUCAO`, `destino: DEVOLUCAO` (evento `EXCEDENTE_DESTINADO_DEVOLUCAO`).
- `GET /excedentes-acerto/{id}` ganhou o bloco **`devolucao`**: `{ contaPagarId, numero, situacao, valorPago, vencimento }` (nulo sem devolução ativa).
- Só excedente `A_DESTINAR`; 400 nos demais casos. Concorrência: destinos simultâneos do mesmo excedente — apenas um vence.

### Desfazer e cancelar
- **POST `/excedentes-acerto/{id}/desfazer-destino`** `{ "motivo": "..." }` (rota da fase 2): para devolução **cancela o título a pagar** e o excedente volta a `A_DESTINAR` (eventos `DEVOLUCAO_CANCELADA` e `DESTINO_DESFEITO`). Devolução **com pagamento ativo** (mesmo parcial): 400 `A devolução ao cliente já possui pagamento. Estorne o pagamento do título a pagar nº N antes de desfazer o destino.`
- **Cancelar o título a pagar da devolução** (`POST /contas-pagar/{id}/cancelar`, inclusive em lote) **sem pagamento** também devolve o excedente a `A_DESTINAR` (evento `DEVOLUCAO_CANCELADA`). Com pagamento ativo segue a regra existente (estornar o pagamento antes).
- Título de devolução em `PUT /contas-pagar/{id}`: **categoria, valor e cliente não podem ser alterados** (400 com mensagem formal); vencimento, descrição, centro de custo, forma prevista, documento e observação podem. O favorecido permanece o nome do cliente.

## 2. Cliente como favorecido (somente lançamento individual)
- `ContaPagarRequestDTO` ganhou **`clienteId`** (UUID, opcional) — POST `/contas-pagar` (simples ou parcelado) e PUT. Com `clienteId`, o texto `favorecido` é **substituído pelo nome do cliente no momento do lançamento (snapshot)**. Cliente inexistente ou desativado: 400.
- **Lote** (`/contas-pagar/lote`) e **recorrência** continuam só com favorecido em texto: item de lote com `clienteId` é recusado; recorrência com `clienteId` → 400; título gerado por recorrência não aceita `clienteId` no PUT.
- **PUT sem `clienteId` torna o título avulso** (cliente nulo; o favorecido passa a ser o texto enviado). O front deve reenviar `clienteId` ao editar um título vinculado.
- Títulos antigos com favorecido em texto permanecem **avulsos** (nenhum vínculo automático, mesmo que o texto seja igual ao nome de um cliente). `GET /contas-pagar/favorecidos` continua sugerindo textos.
- Título pago só permite alterar a observação (inclui não trocar o cliente).

## 3. Respostas e filtros do Contas a Pagar
- `ContaPagarResumoDTO` (lista e `parcelas`) e `ContaPagarDetalheDTO` ganharam: **`cliente`** `{ id, codigo, nome, documento }` (nulo no avulso), **`excedenteId`** e **`origem`** (`MANUAL` | `DEVOLUCAO_CLIENTE`).
- **GET `/contas-pagar`** ganhou os filtros **`clienteId`** (UUID) e **`origem`** (`MANUAL`|`DEVOLUCAO_CLIENTE`; outro valor: 400). Com `clienteId` a lista **não** assume o mês corrente por padrão (traz todos os títulos do cliente). A **`busca`** agora também encontra por **código e nome do cliente** vinculado.
- **Aba Financeiro do cliente:** listar com `GET /contas-pagar?clienteId=<id>` (requer `CONTA_PAGAR_VISUALIZAR`).
- `GET /contas-receber/clientes/{id}/situacao` ganhou o bloco **`aPagar`**: `{ quantidade, emAberto, vencido }` (títulos a pagar `ABERTO`/`PAGO_PARCIAL` do cliente como favorecido).

## 4. Aviso do financeiro do acerto (ajuste)
`aviso` de `GET /acertos-consignacao/{id}/financeiro`: com excedente persistido `A_DESTINAR` traz `O acerto possui excedente a destinar de R$ X: gere crédito ou devolução.`; com excedente já destinado, **sem aviso**; sem excedente persistido mantém apenas `... o valor titulado supera o valor do acerto em R$ X. Nenhum novo título será gerado.` (o texto antigo de indisponibilidade foi removido).

## 5. Crédito aplicado em título de pré-acerto e efeito no excedente
O excedente usa o **recebido do título** do pré-acerto, e esse valor **inclui o crédito do cliente aplicado** (o crédito conta como pago). Exemplo: crédito de R$ 630 quita o título do pré-acerto; no acerto de R$ 450, o excedente de R$ 180 nasce `A_DESTINAR`. Estornar a aplicação de crédito recalcula (ou cancela) o excedente `A_DESTINAR`; com excedente já destinado, o estorno fica bloqueado (desfaça o destino antes).
