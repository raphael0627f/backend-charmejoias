# Financeiro 6.0 — Fase 1: cadastros financeiros e movimentação de contas

Contrato para o Frontend. Sem prefixo `/api`. Erros no padrão do projeto (`RestErrorMenssage`, campo **`menssage`** com dois "s"),
com mensagens em tom formal. Datas em `yyyy-MM-dd`; valores monetários em número JSON com 2 casas (`1234.56`).

Migration: `V64__financeiro-cadastros-movimentacao.sql` (tabelas novas, sementes e permissões). Esta fase **não** inclui contas a pagar,
pagamentos, anexos nem recorrências (fase 2).

## Permissões (novas; concedidas somente ao perfil ADMIN — é preciso **sair e entrar de novo** para o token trazer as novas)

| Permissão | Para que serve |
|---|---|
| `CONTA_PAGAR_VISUALIZAR` | Consultar contas a pagar e os cadastros financeiros (fase 2 usa para a lista) |
| `CONTA_PAGAR_GERENCIAR` | Lançar, editar, cancelar títulos (fase 2) |
| `CONTA_PAGAR_PAGAR` | Pagar títulos (fase 2) |
| `CONTA_PAGAR_ESTORNAR` | Estornar pagamentos (fase 2) |
| `CADASTRO_FINANCEIRO_GERENCIAR` | Criar, editar, reativar/desativar e excluir bancos, categorias, formas de pagamento e centros de custo |
| `MOVIMENTACAO_VISUALIZAR` | Ver extrato e saldos |
| `MOVIMENTACAO_GERENCIAR` | Transferência, ajuste e estorno de movimentação |

**Quem consulta (GET) os cadastros:** qualquer um entre `CONTA_PAGAR_VISUALIZAR`, `CONTA_PAGAR_GERENCIAR`, `CONTA_PAGAR_PAGAR`,
`CADASTRO_FINANCEIRO_GERENCIAR`. `GET /bancos` aceita também `MOVIMENTACAO_VISUALIZAR` e `MOVIMENTACAO_GERENCIAR` (a tela de
movimentação precisa listar os bancos). Sem a permissão: **403** `"Acesso negado."`.
Escrita (POST/PUT/PATCH/DELETE) nos cadastros: `CADASTRO_FINANCEIRO_GERENCIAR`.
Extrato e saldos: `MOVIMENTACAO_VISUALIZAR` ou `MOVIMENTACAO_GERENCIAR`. Transferência, ajuste e estorno: `MOVIMENTACAO_GERENCIAR`.

Os nomes das permissões entram no enum `Permissao` (tela de perfis).

## Convenções comuns dos cadastros

- Nome **único** (sem diferenciar maiúsculas/minúsculas; espaços nas pontas são ignorados). Repetido: **409** `"Já existe ..."`.
- Rotas por cadastro: `GET /x`, `GET /x/{id}`, `POST /x` (201 + `Location`), `PUT /x/{id}`, `PATCH /x/{id}/ativar`,
  `PATCH /x/{id}/inativar`, `DELETE /x/{id}` (204). No texto exibido ao usuário, a ação é "desativar" e "reativar" (glossário); os nomes técnicos das rotas `ativar` e `inativar` permanecem.
- `GET /x` aceita `?ativo=true|false` (omitido = todos) e `?busca=` (contém, sem diferenciar maiúsculas) — exceto a árvore de categorias,
  que aceita só `ativo`.
- Todo item devolve auditoria: `createdAt`, `updatedAt`, `createdBy`, `updatedBy` (`{id, usuario, primeiroNome, sobrenome}`).
- **Excluir** só é permitido para registro que **não está em uso**; em uso: **409** com mensagem orientando a desativar.
  Para cadastros já usados, utilize **desativar** (o item some das listas de escolha do front, mas continua nos históricos).

## Bancos — `/bancos`

Tipos (`tipo`): `CONTA_CORRENTE`, `POUPANCA`, `CAIXA`, `CARTAO`, `CARTEIRA_DIGITAL`.

Request (POST/PUT):
```json
{ "nome": "Banco Inter", "tipo": "CONTA_CORRENTE", "codigoBanco": "077", "agencia": "0001", "conta": "123456-7",
  "saldoInicial": 1500.00, "dataSaldoInicial": "2026-11-01", "observacao": "Conta principal" }
```
Response (`saldoAtual` é somente leitura — soma das movimentações):
```json
{ "id": 2, "nome": "Banco Inter", "tipo": "CONTA_CORRENTE", "codigoBanco": "077", "agencia": "0001", "conta": "123456-7",
  "saldoInicial": 1500.00, "dataSaldoInicial": "2026-11-01", "observacao": "Conta principal", "ativo": true,
  "saldoAtual": 1500.00, "createdAt": "...", "updatedAt": "...", "createdBy": {...}, "updatedBy": {...} }
```
Regras:
- `nome` e `tipo` obrigatórios. `saldoInicial` opcional (zero na criação; omitido no PUT = mantém o atual); pode ser negativo. `dataSaldoInicial` opcional na criação (assume hoje);
  **não pode ser futura**.
- Ao criar com saldo inicial diferente de zero, é gerada a movimentação `SALDO_INICIAL` (ENTRADA se positivo, SAIDA se negativo).
  Saldo inicial zero não gera movimentação.
- **Editar o saldo inicial (ou sua data) só é possível enquanto não houver outras movimentações** no banco; depois, **400**:
  `"Não é possível alterar o saldo inicial, pois já existem movimentações neste banco. Utilize um Ajuste."`.
  Demais campos (nome, tipo, agência, etc.) podem ser editados sempre.
- Banco **desativado** não recebe novas movimentações (transferência, ajuste, estorno → 400).
- Excluir: só banco sem movimentações além do saldo inicial.
- A semente cria o banco **Caixa** (tipo `CAIXA`, saldo inicial zero).

## Categorias de despesa — `/categorias-despesa`

Tipos (`tipo`): `CUSTO`, `DESPESA_FIXA`, `DESPESA_VARIAVEL`, `IMPOSTO`, `PESSOAL`, `OUTROS`.

- `GET /categorias-despesa` → **árvore**: grupos (nível 1) ordenados por `ordem`/nome, cada um com `subcategorias` (nível 2).
  `?ativo=true` filtra também os subgrupos; um grupo desativado some junto com os seus subgrupos.
- `GET /categorias-despesa/plana` → **lista plana** (grupos e subgrupos juntos; cada item com `paiId` e `paiNome`; sem `subcategorias`).
  Ordenada por grupo e depois subgrupo; aceita `?ativo=` e `?busca=`. Nos grupos `paiId` e `paiNome` vêm `null`.
- `GET /categorias-despesa/{id}`.

Request (POST/PUT): `{ "nome": "Aluguel", "paiId": 2, "tipo": "DESPESA_FIXA", "ordem": 1 }` (`paiId` nulo = grupo; `ordem` opcional, padrão 0).

Response:
```json
{ "id": 8, "nome": "Aluguel", "paiId": 2, "paiNome": "Despesas operacionais", "tipo": "DESPESA_FIXA", "ordem": 1, "ativo": true,
  "createdAt": "...", "updatedAt": "...", "createdBy": {...}, "updatedBy": {...} }
```
Regras: no máximo **2 níveis** (o pai precisa ser um grupo, senão **400**); nome único **dentro do mesmo pai**; um grupo com
subgrupos não muda para subgrupo; não é possível desativar um grupo com subgrupos ativos (**409**, desative os subgrupos antes);
reativar um subgrupo exige o grupo ativo (**409**: "Reative o grupo antes de reativar o subgrupo."). Excluir: grupo sem subgrupos e sem uso.

**Sementes** (editáveis): grupos Despesas com pessoal, Despesas operacionais, Impostos e taxas, Custos de mercadoria, Marketing,
Despesas financeiras e Outras, com 42 subgrupos (lista completa no relatório da fase e na própria migration).

## Formas de pagamento — `/formas-pagamento` e Centros de custo — `/centros-custo`

Request: `{ "nome": "PIX" }`. Response: `{ "id", "nome", "ativo", "createdAt", "updatedAt", "createdBy", "updatedBy" }`.
Semente de formas: Dinheiro, PIX, Boleto, Transferência, Cartão de crédito, Cartão de débito, Débito automático, Cheque.
Centros de custo: sem semente.

## Movimentação de contas — `/movimentacoes-banco`

Origens (`origem`): `SALDO_INICIAL`, `PAGAMENTO_CONTA_PAGAR` e `ESTORNO_PAGAMENTO` (fase 2), `TRANSFERENCIA`, `AJUSTE`, `ESTORNO_MANUAL`.
Tipos (`tipo`): `ENTRADA`, `SAIDA`. O saldo do banco é a soma das movimentações (entrada soma, saída subtrai). Movimentações nunca são
alteradas nem apagadas: o **estorno** gera uma movimentação inversa.

### `GET /movimentacoes-banco` — extrato (paginado)

Parâmetros (todos opcionais): `bancoId`, `dataInicio`, `dataFim` (inclusivos), `origem`, `busca` (descrição ou nome do banco),
`page` (0), `size` (20, máximo 200). Ordenação fixa: data e id **decrescentes** (mais recente primeiro).
`saldoApos` (saldo do banco depois da linha, considerando o histórico completo e não só o período filtrado) só é devolvido
**quando `bancoId` é informado**; sem banco o campo é omitido.

Resposta: página do Spring (`content`, `totalElements`, `totalPages`, `number`, `size`...), cada item:
```json
{ "id": 15, "bancoId": 2, "bancoNome": "Banco Inter", "data": "2026-11-10", "tipo": "SAIDA", "valor": 300.00,
  "origem": "TRANSFERENCIA", "descricao": "Transferência para Caixa", "transferenciaId": 4, "estornada": false,
  "usuario": { "id": "...", "usuario": "admin", "primeiroNome": "...", "sobrenome": "..." }, "criadoEm": "2026-11-10T14:03:11",
  "saldoApos": 1200.00 }
```
Em movimentação estornada: `estornada: true`, `motivoEstorno`, `estornadaEm`, `estornadaPor`. Na movimentação inversa: `estornoDeId`
(id da original) e `origem: "ESTORNO_MANUAL"`.

### `GET /movimentacoes-banco/saldos`

```json
{ "bancos": [ { "bancoId": 1, "nome": "Caixa", "tipo": "CAIXA", "ativo": true, "saldoAtual": 0.00 } ], "total": 0.00 }
```
`total` soma o saldo de **todos** os bancos listados (ativos e desativados).

### `POST /movimentacoes-banco/transferencias` (201)

```json
{ "bancoOrigemId": 2, "bancoDestinoId": 1, "valor": 300.00, "data": "2026-11-10", "descricao": "Reforço de caixa" }
```
Bancos diferentes e ativos, valor > 0 (até 2 casas), data **não futura**. Gera a saída na origem e a entrada no destino, ligadas por
`transferenciaId`. Resposta (também em ajuste e estorno):
```json
{ "movimentacoes": [ { ...saída... }, { ...entrada... } ], "transferenciaId": 4,
  "aviso": "Atenção: após esta operação, o saldo do banco \"Banco Inter\" ficou negativo (R$ -50,00)." }
```
`aviso` só existe quando algum banco afetado fica com **saldo negativo** (a operação **é concluída**: saldo negativo é permitido).
Exiba o texto ao usuário como alerta, não como erro.

### `POST /movimentacoes-banco/ajustes` (201)

```json
{ "bancoId": 2, "tipo": "ENTRADA", "valor": 25.50, "data": "2026-11-10", "motivo": "Diferença apurada na conciliação" }
```
Banco ativo, `tipo` ENTRADA/SAIDA, valor > 0, data não futura, **motivo obrigatório com pelo menos 5 caracteres** (máx. 255).

### `POST /movimentacoes-banco/{id}/estornar` (200)

```json
{ "motivo": "Lançamento em duplicidade" }
```
Apenas movimentações de origem `TRANSFERENCIA` ou `AJUSTE` (nesta fase), motivo com pelo menos 5 caracteres, **uma única vez**
(segunda tentativa: 400 `"Esta movimentação já foi estornada."`). Gera a movimentação inversa com a data de hoje. Estornar uma
**transferência** estorna as **duas pontas** (pode-se informar o id de qualquer uma). Os bancos envolvidos precisam estar ativos.

## Erros (padrão `menssage`)

| Situação | HTTP |
|---|---|
| Campo obrigatório ausente, valor ≤ 0, data futura, motivo curto, bancos iguais, banco desativado, 3º nível de categoria, saldo inicial com movimentações | 400 |
| Sem a permissão | 403 |
| Registro inexistente | 404 |
| Nome duplicado, exclusão/desativação de item em uso | 409 |

Exemplo: `{ "timestamp": "...", "status": 400, "menssage": "Informe o motivo com pelo menos 5 caracteres.", "path": "..." }`.

## Teto de valores (atualização)

Nenhum valor monetário informado pode ultrapassar **R$ 9.999.999,99**. Acima disso a resposta é **400** com mensagem formal, por exemplo
`"O valor não pode ser superior a R$ 9.999.999,99."` (ou `"O valor de juros não pode ser superior a R$ 9.999.999,99."`).
Vale para: valor do título (simples, parcelado, recorrência, lote e edição, inclusive o reajuste da recorrência), valores personalizados de parcelas,
principal, juros, multa e desconto do pagamento, ajuste, transferência e saldo inicial do banco (em módulo). O front pode limitar o campo a esse teto.

## Categoria reservada "Devoluções a clientes"
A categoria de despesa **"Devoluções a clientes"** (grupo de nível 1, e qualquer subgrupo dela) é **reservada** ao título a pagar gerado pelo sistema na devolução do excedente do acerto.
- **Lançamento manual recusa (400, `menssage`):** `POST /contas-pagar`, `PUT /contas-pagar/{id}` (em título que não seja de devolução), lançamento em lote, recorrência (criação e edição) e duplicação (que usa o `POST`) respondem: *A categoria "Devoluções a clientes" é reservada às devoluções geradas pelo sistema a partir do excedente do acerto e não pode ser usada em lançamento manual.* O `PUT` de um título de devolução existente continua aceitando a própria categoria (ela não pode mudar).
- **Novo parâmetro opcional** `paraLancamento=true` em `GET /categorias-despesa` (árvore) e `GET /categorias-despesa/plana`: omite a categoria reservada e seus subgrupos. **Use-o nos seletores de categoria do formulário de lançamento manual** (título, lote, recorrência). Sem o parâmetro (padrão `false`) a lista é completa, para consultas, filtros e relatórios.
- **Cadastro de categorias (400, `menssage`):** o grupo reservado e seus subgrupos **não podem ser renomeados, movidos para outro grupo, desativados nem excluídos** (`PUT /categorias-despesa/{id}` com nome ou `paiId` diferentes, `PATCH .../inativar`, `DELETE`), mesmo por quem tem `CADASTRO_FINANCEIRO_GERENCIAR`. Também **não é possível criar subgrupo** dentro dele nem mover outra categoria para ele. Continuam permitidos: consultar (`GET`) e alterar apenas `tipo`/`ordem` do grupo. Mensagem: *A categoria "Devoluções a clientes" é reservada ao sistema (devoluções do excedente do acerto) e não pode ser renomeada, movida, desativada ou excluída.* Sugestão ao front: desabilitar editar nome/mover/inativar/excluir para essa categoria (identificar pelo nome "Devoluções a clientes" no nível 1 e seus filhos).
