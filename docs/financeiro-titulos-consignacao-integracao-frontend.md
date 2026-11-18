# Financeiro — Títulos a receber gerados a partir do pré-acerto e do acerto de consignação (Fase 1)

Migration **V72**. Rotas sem prefixo `/api`; erros em `{ "timestamp", "status", "menssage", "path" }`. Esta fase **não** inclui link de pagamento nem crédito de cliente.
Branch isolada `feat/receber-consignacao` (parte de `feat/contas-receber`): o Contas a Receber funciona sem esta funcionalidade.
Os títulos gerados seguem **todas** as regras do Contas a Receber (`docs/financeiro-contas-receber-integracao-frontend.md` e `docs/financeiro-recebimentos-integracao-frontend.md`).

## Regras de negócio

- Título ligado à **consignação** e ao **pré-acerto OU ao acerto** que o gerou. Pode ser **simples** (1 parcela) ou **parcelado** (2 a 120).
- **Um grupo de títulos ativo (não cancelado) por documento**: um para o pré-acerto e um para o acerto.
- O valor do título é o **saldo a titular, integral** (não há valor digitável). Pagamento dividido = recebimentos parciais (e bancos diferentes), que já existem.
- **Valor do documento:** pré-acerto = soma de (quantidade × preço) menos o desconto (valor absoluto); acerto = valor total dos produtos acertados menos o desconto (o mesmo `valorTotalComDesconto` do acerto).
- **Saldo a titular:**
  - pré-acerto = valor do pré-acerto − títulos ativos ligados a ele;
  - acerto = valor do acerto − **todos** os títulos ativos da consignação (inclui os do pré-acerto).
  - Título **cancelado deixa de contar** (libera o saldo). Título com recebimento ativo só cancela depois de estornar (regra atual).
  - Sem título no pré-acerto, o do acerto cobre o total.
- **Saldo ≤ 0:** não gera título. Se o título ativo estiver acima do valor do acerto, o resumo informa `excedente` e um `aviso` formal (crédito de cliente fica para fase posterior).
- O título do **acerto só pode ser gerado com o acerto CONFIRMADO**. Pré-acerto **sem consignação não gera título**. Valor do documento ≤ 0 não gera título.
- O **cliente** é o da consignação/pré-acerto e deve estar **ativo**.
- Padrões do título: vencimento hoje (editável no pedido), emissão hoje, categoria de receita fixa **Vendas > Consignação** (criada pela V72), descrição automática em maiúsculas (`CONS. Nº <id da consignação> - PRÉ-ACERTO Nº <id>` / `CONS. Nº <id da consignação> - ACERTO Nº <id>` (pré-acerto sem consignação: `PRÉ-ACERTO Nº <id>`); títulos já gerados não mudam; a descrição padrão do link de pagamento deriva da descrição do título; nas parcelas ganha o sufixo `n/N`). Centro de custo e forma de pagamento prevista são opcionais.
- **Recebimento no mesmo ato (opcional):** só para título **simples**. Tudo na mesma transação (título + recebimento); se o recebimento falhar, nada é criado. O comprovante é enviado depois pelo endpoint de anexos existente (`POST /contas-receber/recebimentos/{id}/anexos`).

### Bloqueio de edição com título ativo
- **Pré-acerto** com título ativo **próprio**: `PUT /pre-acertos-consignacao` recusa (400) qualquer alteração de itens, quantidades, preços, desconto, consignação ou cliente. A verificação compara o corpo com o que está gravado: **reenviar o corpo idêntico (sem mudança real) continua 200**, para não quebrar o salvamento da tela; qualquer diferença em preço unitário, quantidade, itens, desconto, consignação ou cliente devolve 400. Alterar **somente a observação** continua permitido. Mensagem: `O pré-acerto possui título a receber ativo e não pode ser alterado. Cancele o título para alterar.`
- Cancelar o título (todos os ativos do pré-acerto) libera a edição.
- **Acerto:** o acerto confirmado não é editável (409, regra existente). Acerto **não confirmado** com título ativo: mesma regra do pré-acerto (somente a observação pode mudar; corpo idêntico continua 200; alterar desconto, itens ou quantidades devolve 400 `O acerto possui título a receber ativo e não pode ser alterado. Cancele o título para alterar.`). O título do acerto normalmente só nasce após a confirmação; a regra cobre qualquer título ativo do acerto.
- Decisão: o título ativo do **acerto** da mesma consignação **não** bloqueia a edição do pré-acerto (fora do escopo desta fase). Em contrapartida, **não é possível gerar título do pré-acerto quando a consignação já possui título ativo do acerto** (evita excedente artificial).
- Decisão: título gerado por consignação **não permite alterar o valor nem o pagador (cliente)** em `PUT /contas-receber/{id}` (400: `O valor e o cliente de título gerado a partir de consignação não podem ser alterados. Cancele o título e gere novamente.`). Vencimento, descrição, categoria, centro, forma, documento e observação seguem editáveis; `PATCH /vencimento`, cancelamento e recebimentos funcionam normalmente.

## Permissões

| Ação | Permissão |
| --- | --- |
| `GET .../financeiro` | `CONTA_RECEBER_VISUALIZAR`, `CONTA_RECEBER_GERENCIAR` ou `CONTA_RECEBER_RECEBER` |
| `POST .../titulos` | `CONTA_RECEBER_GERENCIAR` |
| `POST .../titulos` com `recebimento` | `CONTA_RECEBER_GERENCIAR` **e** `CONTA_RECEBER_RECEBER` (sem a segunda: 403 `Acesso negado.`, antes de qualquer validação do corpo) |

Sem permissão: **403 antes de qualquer validação do corpo** (inclusive corpo inválido). Sem token: 401. As telas e as rotas atuais de pré-acerto/acerto **não mudam**: um usuário só com permissões de consignação/acerto continua usando-as normalmente (apenas não vê o resumo financeiro). O frontend deve esconder o bloco financeiro e o botão para quem não tem as permissões acima.

## Endpoints

### `GET /pre-acertos-consignacao/{id}/financeiro` e `GET /acertos-consignacao/{id}/financeiro` (200)
```json
{ "tipo": "PRE_ACERTO", "documentoId": 12, "consignacaoId": 7, "clienteCodigo": "C0123", "clienteNome": "Maria",
  "valorDocumento": 1000.00, "valorTitulado": 400.00, "valorRecebido": 100.00,
  "saldoATitular": 600.00, "excedente": 0.00, "excedenteRecebido": 0.00,
  "valorTituladoPreAcerto": null, "tituloAtivo": true, "bloqueadoParaEdicao": true,
  "podeGerar": false, "motivoBloqueio": "O pré-acerto já possui título a receber ativo.", "aviso": null,
  "titulos": [ { "id": 31, "numero": 31, "parcelaNumero": 1, "parcelaTotal": 2, "valor": 200.00,
                 "vencimento": "2026-11-16", "situacao": "RECEBIDO_PARCIAL", "valorRecebido": 100.00 } ] }
```
- `valorDocumento`: valor com desconto do documento. `valorTitulado`: soma dos títulos ativos (no acerto: de toda a consignação). `valorRecebido`: recebimentos ativos desses títulos.
- `saldoATitular` ≥ 0. `excedente` > 0 quando o titulado ativo supera o valor do documento; `excedenteRecebido` > 0 quando o recebido supera o valor do documento. `aviso` (texto formal): no acerto com excedente persistido a destinar traz `O acerto possui excedente a destinar de R$ X: gere crédito ou devolução.`; excedente já destinado, sem aviso; sem excedente persistido mantém o aviso de valor titulado acima do valor do documento.
- `valorTituladoPreAcerto`: só no acerto, a parte já titulada pelo pré-acerto da consignação.
- `tituloAtivo`: há título **próprio** ativo (do documento). `bloqueadoParaEdicao`: o documento não pode mais ser alterado (pré-acerto: `tituloAtivo`; acerto: confirmado ou `tituloAtivo`).
- `podeGerar`/`motivoBloqueio`: o frontend deve **desabilitar o botão** e exibir o motivo quando `podeGerar` for `false`.
- `titulos[]`: todos os títulos próprios do documento (inclusive cancelados, para consulta), em ordem de criação.
- 404 se o documento não existir.

### `POST /pre-acertos-consignacao/{id}/titulos` e `POST /acertos-consignacao/{id}/titulos` (201)
O frontend deve pedir **confirmação** antes de chamar.
```json
{ "dataVencimento": "2026-12-10", "quantidadeParcelas": 3, "intervalo": "MENSAL", "datas": null,
  "descricao": null, "centroCustoId": null, "formaPagamentoId": 3, "observacao": "...",
  "recebimento": { "dataRecebimento": "2026-11-16", "valorPrincipal": 500.00, "juros": 0, "multa": 0, "desconto": 0,
                   "bancoId": 1, "formaPagamentoId": 3, "observacao": "Pago em dinheiro" } }
```
- Todos os campos são opcionais: `dataEmissao` (**aditivo**; padrão hoje; não pode ser futura; permite lançar título com a data real do documento, p. ex. carga de pré-acertos antigos); `dataVencimento` (padrão = emissão; não pode ser anterior à **emissão**, ou seja, a hoje quando `dataEmissao` não é enviada); `quantidadeParcelas` (padrão 1 = simples; 2 a 120); `intervalo` (`MENSAL` padrão, `QUINZENAL`, `SEMANAL`, `PERSONALIZADO` com `datas` de exatamente `quantidadeParcelas` datas); `descricao` (até 200; padrão automático); `centroCustoId`, `formaPagamentoId` (ativos), `observacao` (até 1000).
- `recebimento` (opcional, **somente título simples**): mesmas regras do recebimento normal (`docs/financeiro-recebimentos-integracao-frontend.md`): `dataRecebimento` não futura, `valorPrincipal` até o valor do título, `juros`/`multa`/`desconto` opcionais, `bancoId` e `formaPagamentoId` obrigatórios e ativos.
- Resposta:
```json
{ "grupoParcelamentoId": null, "titulos": [ { "...resumo do título a receber, com origem..." } ],
  "recebimento": { "...recebimento (como em POST /contas-receber/{id}/recebimentos)..." },
  "resumo": { "...mesmo corpo do GET .../financeiro, já atualizado..." } }
```
`recebimento` é `null` quando não enviado.

### Contas a Receber (aditivo)
- Todo título (lista, detalhe, parcelas, criação) ganha `origem`: `null` ou `{ "tipo": "PRE_ACERTO" | "ACERTO", "documentoId": 12, "consignacaoId": 7 }`.
- `GET /contas-receber` ganha os filtros `consignacaoId` e `origem` (`PRE_ACERTO` ou `ACERTO`; outro valor: 400 `Origem inválida. Use PRE_ACERTO ou ACERTO.`).
- O histórico `TITULO_CRIADO` registra a origem (ex.: `Origem: pré-acerto nº 12 da consignação nº 7.`).

## Erros

| Situação | Status / mensagem |
| --- | --- |
| Sem token | 401 |
| Sem permissão (mesmo com corpo vazio ou inválido) | 403 |
| `recebimento` sem `CONTA_RECEBER_RECEBER` | 403 `Acesso negado.` |
| Documento inexistente | 404 |
| Pré-acerto sem consignação | 400 `O pré-acerto não possui consignação e não gera título a receber.` |
| Acerto não confirmado | 400 `O acerto precisa estar confirmado para gerar título a receber.` |
| Valor do documento ≤ 0 | 400 `O valor do pré-acerto/acerto deve ser maior que zero para gerar título a receber.` |
| Já existe grupo ativo do documento | 400 `O pré-acerto/acerto já possui título a receber ativo.` |
| Saldo ≤ 0 | 400 `O pré-acerto/acerto já está totalmente titulado.` (com excedente, o texto informa o valor excedente) |
| Título do pré-acerto com título do acerto ativo | 400 `A consignação já possui título a receber do acerto.` |
| Cliente desativado | 400 `O cliente "X" está desativado.` |
| Recebimento em título parcelado | 400 `O recebimento no mesmo ato só é permitido para título simples. A baixa das parcelas é feita na tela do título.` |
| Parcelamento/valores/datas inválidos, recebimento inválido, categoria/centro/forma desativados | 400 (mensagens do Contas a Receber e dos recebimentos) |
| Edição do pré-acerto com título ativo | 400 `O pré-acerto possui título a receber ativo e não pode ser alterado. Cancele o título para alterar.` |
| Edição do acerto não confirmado com título ativo | 400 `O acerto possui título a receber ativo e não pode ser alterado. Cancele o título para alterar.` |
| Alterar valor/cliente de título de consignação | 400 (ver acima) |

## O que o frontend deve travar
- Botão "Gerar título a receber" visível só com `CONTA_RECEBER_GERENCIAR`; habilitado somente com `podeGerar`; confirmar antes de enviar; bloquear duplo clique.
- Mostrar o bloco de recebimento no ato só com `CONTA_RECEBER_RECEBER` e só para 1 parcela.
- No pré-acerto com `bloqueadoParaEdicao`, desabilitar itens/desconto/consignação e manter só a observação editável; exibir `motivoBloqueio`.
- Exibir `aviso`/`excedente` quando houver.
- Concorrência: duas requisições simultâneas nunca geram dois grupos nem tituam acima do saldo (o servidor bloqueia a consignação e o documento); a segunda recebe 400.

## Pré-acerto SEM consignação (ajuste 6.1, migration V74)
- O pré-acerto já podia ser criado sem consignação (`POST/PUT /pre-acertos-consignacao` sem `consignacaoId`, com `clienteId` **obrigatório** e `preco` informado em cada item). Agora ele também **gera título a receber**: `GET /pre-acertos-consignacao/{id}/financeiro` e `POST /pre-acertos-consignacao/{id}/titulos` funcionam normalmente (`podeGerar = true`), com o mesmo contrato, as mesmas regras (valor > 0, um título ativo por pré-acerto, cliente ativo, edição bloqueada com título ativo) e o saldo calculado apenas sobre os títulos do próprio pré-acerto.
- Diferenças no contrato (nenhum campo novo): `consignacaoId` do resumo passa a poder ser `null`; em `/contas-receber`, `origem` = `{ "tipo": "PRE_ACERTO", "documentoId": <id>, "consignacaoId": null }`; o evento `TITULO_CRIADO` cita apenas "pré-acerto nº X" quando não há consignação. A frase de bloqueio "O pré-acerto não possui consignação..." deixou de existir; o **acerto** continua exigindo consignação (`O acerto não possui consignação e não gera título a receber.`).
- O frontend deve exibir o botão de gerar título também para pré-acertos sem consignação e tratar `consignacaoId` nulo nas telas (link para a consignação somente quando houver).
- Descrição automática sem consignação: `PRÉ-ACERTO Nº <id>` (ver acima).

## Título zerado automático no acerto (6.3, migration V75)
- **Quando:** ao confirmar o acerto (`PUT /acertos-consignacao/{id}/confirmar`), se o valor a pagar (após desconto) for **exatamente R$ 0,00** e a consignação **não tiver título ativo de pré-acerto nem do próprio acerto**, o backend registra, na mesma transação, **um título a receber de R$ 0,00 já `RECEBIDO`**, apenas para registro. Somente para o **acerto** (nunca para o pré-acerto). Com título ativo de pré-acerto **não** gera (há excedente, tratado na fase de crédito do cliente).
- **Dados do título:** `valor` 0,00, `valorRecebido` 0,00, `valorAberto` 0,00, situação `RECEBIDO`, emissão = vencimento = **data da confirmação** (hoje), competência = mês da confirmação, categoria Vendas > Consignação, descrição `CONS. Nº <consignação> - ACERTO Nº <id>`, observação `Acerto sem valor a pagar. Título registrado automaticamente, sem cobrança.`, `origem` = `{ "tipo": "ACERTO", ... }`, **sem** centro de custo, **sem** forma prevista.
- **Sem recebimento financeiro:** não há banco, forma de pagamento, movimentação bancária nem registro em `recebimentos` (a lista `recebimentos` do detalhe vem vazia). O histórico (`eventos`) traz `TITULO_CRIADO` e `RECEBIMENTO_AUTOMATICO` ("Título sem valor a pagar quitado automaticamente em dd/mm/aaaa. Não houve recebimento financeiro nem movimentação bancária.").
- **Imutável:** `POST /contas-receber/{id}/cancelar` e `PUT /contas-receber/{id}` respondem 400 `O título de acerto sem valor a pagar é um registro automático e não pode ser cancelado.` / `... não pode ser editado.`; recebimento, estorno e link de pagamento já eram recusados por o título estar `RECEBIDO` (`O título já está integralmente recebido.` / `Somente títulos em aberto ou parcialmente recebidos geram link de pagamento.`). Também fica fora do cancelamento em lote (item com `sucesso=false` e a mesma mensagem). Como não pode ser cancelado, não há regeração.
- **GET `/acertos-consignacao/{id}/financeiro`** após a confirmação: o título zerado aparece em `titulos` (valor 0, `RECEBIDO`), `tituloAtivo=true`, `bloqueadoParaEdicao=true`, `podeGerar=false` e `motivoBloqueio` = `O acerto já possui título a receber ativo.` (a regra do título próprio passou a vir **antes** da regra de valor ≤ 0). Acerto de valor 0 **com** título de pré-acerto ativo: `podeGerar=false` com `O acerto deve ter valor maior que zero para gerar título a receber.`, `excedente` > 0 e `aviso` do excedente (nenhum título é criado).
- **Idempotência:** a confirmação é irreversível e serializada (bloqueio da consignação e do acerto); reconfirmar devolve 400 `O acerto já está confirmado.`; 4 confirmações simultâneas → um 200, três 400 e um único título.
- **Listas/totais:** o título aparece em `/contas-receber` (origem ACERTO, situação `RECEBIDO`) com valor 0; os totais somam 0 e a situação do cliente continua coerente (nenhum divisor por valor).
