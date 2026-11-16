# Financeiro 6.1 — Fase 2: núcleo do título a receber (`/contas-receber`)

Migration **V68** (tabelas `conta_receber` e `conta_receber_evento`). Rotas sem prefixo `/api`; erros em `{ "timestamp", "status", "menssage", "path" }`.
O módulo espelha o Contas a Pagar (`docs/financeiro-contas-pagar-integracao-frontend.md`): nomes de campos, filtros, totais e comportamento são os mesmos, com as diferenças descritas aqui.
**Fora desta fase:** recebimento, estorno, recebimento em lote, anexos e situação do cliente (fases 3 e 4). Não há recorrência no Contas a Receber nesta versão.

## Permissões

| Permissão | Efeito |
| --- | --- |
| `CONTA_RECEBER_VISUALIZAR` | Leitura (lista, detalhe, busca de clientes) |
| `CONTA_RECEBER_GERENCIAR` | Criar, editar, alterar vencimento, cancelar (individual e em lote). Também lê |
| `CONTA_RECEBER_RECEBER` | Recebimentos (fase 3). Também lê |
| `CONTA_RECEBER_ESTORNAR` | Estorno de recebimento (fase 3). **Isolada não concede acesso às telas** |

Leitura aceita qualquer uma das três primeiras. Sem permissão: **403 antes de qualquer validação do corpo**. Cadastros usados (categorias de receita, formas, centros, bancos) seguem as regras da fase 1.

## Títulos

### `POST /contas-receber` (201) — simples ou parcelado
```json
{ "descricao": "Venda de joias - pedido 10", "clienteId": "6f1c...-uuid", "categoriaId": 2, "centroCustoId": null,
  "valor": 1500.00, "emissao": "2026-11-16", "competencia": "2026-12", "vencimento": "2026-12-10",
  "documento": "NF 123", "formaPrevistaId": 3, "observacao": "...",
  "parcelamento": { "quantidade": 3, "primeiroVencimento": "2026-12-10", "intervalo": "MENSAL", "datas": null, "valores": null } }
```
- Obrigatórios: `descricao` (até 200), `categoriaId`, `valor` e **um pagador** (`clienteId` **ou** `pagadorNome`, veja a seção "Pagador avulso"); `vencimento` é obrigatório no título simples. `documento` até 60 e `observacao` até 1000 caracteres.
- Quando informado, `clienteId` deve existir e estar **ativo** (400: "O cliente informado não foi encontrado." / "O cliente X está desativado.").
- `categoriaId`: categoria de receita ativa (grupo **ou** subgrupo). `centroCustoId` e `formaPrevistaId` (opcionais): ativos.
- `valor` > 0, até 2 casas e **teto R$ 9.999.999,99**.
- `emissao` padrão: hoje. **Decisão: o vencimento não pode ser anterior à emissão** (400: "O vencimento não pode ser anterior à data de emissão."; vale para cada parcela). Não há restrição para emissão futura.
- `competencia` (`aaaa-mm`) padrão: mês do vencimento (no parcelamento, mês de cada parcela).
- **Parcelamento** (mesmas regras do Contas a Pagar): `quantidade` de 2 a 120; `intervalo` `MENSAL`, `QUINZENAL`, `SEMANAL` (a partir de `primeiroVencimento`, ou de `vencimento` se omitido) ou `PERSONALIZADO` (`datas` com exatamente `quantidade` datas). Valores iguais com **centavo na última parcela**; `valores` opcional (um por parcela, cada um > 0, soma igual ao `valor`, senão 400). Parcela menor que R$ 0,01 é recusada. Mensal parte sempre da data original (31/01 → 28/02 → 31/03). A descrição ganha o sufixo `n/N`.
- Resposta: `{ "grupoParcelamentoId": null|"uuid", "titulos": [ Resumo ] }`.

### Resumo do título (lista, criação, parcelas)
```json
{ "id": 1, "numero": 1, "descricao": "...", "cliente": { "id": "uuid", "nome": "Maria", "documento": "12345678900" },
  "categoria": { "id": 2, "nome": "Vendas de joias", "grupoId": 1, "grupoNome": "Vendas" }, "centroCusto": null, "formaPrevista": { "id": 3, "nome": "Boleto" },
  "valor": 500.00, "valorRecebido": 0.00, "valorAberto": 500.00, "emissao": "2026-11-16", "competencia": "2026-12", "vencimento": "2026-12-10",
  "documento": "NF 123", "situacao": "ABERTO", "situacaoExibicao": "ABERTO", "grupoParcelamentoId": "uuid", "parcelaNumero": 1, "parcelaTotal": 3,
  "motivoCancelamento": null, "dataCancelamento": null, "quantidadeAnexos": 0 }
```
`situacao` gravada: `ABERTO`, `RECEBIDO_PARCIAL`, `RECEBIDO`, `CANCELADO`. `situacaoExibicao` acrescenta `VENCIDO` (aberto ou parcial com vencimento anterior a hoje; **calculado, nunca gravado**).
`cliente.documento` é o CPF do cadastro (ou o documento, se não houver CPF). `quantidadeAnexos` é 0 até a fase 4.

### `GET /contas-receber` — lista paginada
Parâmetros (todos opcionais): `vencimentoDe`, `vencimentoAte`, `emissaoDe`, `emissaoAte`, `recebimentoDe`, `recebimentoAte`, `situacao` (repetível; aceita `VENCIDO`),
`busca` (descrição, nome do cliente, documento do título, CPF/documento do cliente e número, com `#` opcional), `clienteId`, `categoriaId` (grupo traz os subgrupos), `centroCustoId`,
`formaPrevistaId`, `bancoId`, `valorMin`, `valorMax`, `ordenar` (`vencimento` [padrão], `valor`, `numero`, `cliente`, `situacao`), `direcao` (`asc`|`desc`), `page` (0), `size` (20, máximo 200).
- O filtro `situacao=ABERTO` corresponde à situação gravada (inclui os atrasados, como no Contas a Pagar); `situacao=VENCIDO` retorna só os atrasados. Combine os dois para ver todos os em aberto.
- Sem filtro de data, de busca nem de situação, o backend aplica o **mês atual** no vencimento; o frontend normalmente envia o período.
- `recebimentoDe`, `recebimentoAte` e `bancoId` dependem dos recebimentos (fase 3): **nesta fase retornam lista vazia** quando informados.
- Resposta: `{ "content": [Resumo], "page", "size", "totalElements", "totalPages", "periodoVencimento": {"de","ate"}, "totais": { "quantidade", "valorTotal", "valorRecebido", "valorAberto", "valorVencido" } }`.
  Os totais valem para todo o filtro; `valorTotal` e `valorAberto` desconsideram os cancelados; `valorVencido` = parte do aberto com vencimento anterior a hoje.

### `GET /contas-receber/{id}` — detalhe
Resumo completo e, além dele: `observacao`, `cancelamento` (`motivo`, `em`, `por`) e, também no nível raiz como na lista, `motivoCancelamento`, `dataCancelamento` e `canceladoPor` (nulos se o título não foi cancelado), `recebimentos` (**lista vazia até a fase 3**), `anexos` (**lista vazia até a fase 4**),
`parcelas` (resumos do grupo de parcelamento, vazio se não parcelado), `eventos` (`id`, `evento`, `detalhe`, `usuario`, `dataHora`), `createdAt`, `updatedAt`, `createdBy`, `updatedBy`.
404 se o título não existir.

### `PUT /contas-receber/{id}` — editar (corpo igual ao de criação; `parcelamento` é ignorado)
- Título `ABERTO` sem recebimento: edição livre (inclusive troca de cliente).
- Com recebimento (`valorRecebido` > 0, fase 3): o valor não pode ficar abaixo do recebido (400) e **o cliente não pode ser trocado** (400).
- `RECEBIDO`: só a `observacao` pode mudar (qualquer outro campo diferente → 400).
- `CANCELADO`: não pode ser editado (400).
- Campos opcionais omitidos no corpo (`centroCustoId`, `formaPrevistaId`, `documento`, `observacao`) são **limpos**: envie sempre o formulário completo.
- Os campos omitidos `emissao`, `competencia` e `vencimento` mantêm o valor atual. Evento `TITULO_EDITADO` com as alterações.

### `PATCH /contas-receber/{id}/vencimento` — alterar vencimento
`{ "dataVencimento": "2026-12-20", "motivo": "Prorrogação acordada com o cliente" }` (`novoVencimento` também é aceito). Motivo com **mínimo de 5 caracteres**; nova data diferente da atual e não anterior à emissão; recusado em título `RECEBIDO` ou `CANCELADO`. Evento `VENCIMENTO_ALTERADO`.

### `POST /contas-receber/{id}/cancelar` — `{ "motivo": "Lançamento em duplicidade" }` (mínimo 5)
Recusado em título já cancelado e em título com recebimento ativo (400: estorne antes; fase 3). Fica `CANCELADO` com motivo, data e usuário. Evento `TITULO_CANCELADO`.

### `POST /contas-receber/cancelar-lote` — `{ "ids": [..], "motivo": ".." }` (até 200)
Cada título em transação própria: `{ "cancelados": 2, "recusados": 1, "resultados": [ { "id", "numero", "cancelado": true|false, "menssage": null|"..." } ] }`. Lista vazia, ids nulos, mais de 200 ou motivo curto → 400 para o pedido inteiro.

### `GET /contas-receber/clientes?busca=maria` — seletor de clientes
Até **20** clientes **ativos**, por nome ou CPF/documento (contém, sem diferenciar maiúsculas; o documento é devolvido e guardado **sem máscara**, mas a busca aceita a entrada mascarada, p. ex. `111.444.777-35` ou `12.345.678/0001-95`, e encontra o mesmo cliente que `11144477735`; vale também para o filtro `busca` da lista `/contas-receber` e da visão geral `/contas-receber/clientes/situacao`): `[ { "id": "uuid", "nome": "Maria", "documento": "12345678900", "cidade": "Campinas" } ]`
(`cidade` = município do endereço principal). Acessível com `CONTA_RECEBER_VISUALIZAR`, `GERENCIAR` ou `RECEBER`, **sem exigir permissão de cliente**. Sem `busca`, devolve os primeiros 20 por nome.

### Duplicar
Feito no frontend a partir do detalhe (sem endpoint): abre o formulário de novo título com os dados copiados.

## Histórico (eventos)
(nomes diferentes dos do Contas a Pagar, que usa `CRIACAO`, `EDICAO` e `CANCELAMENTO`) `TITULO_CRIADO`, `TITULO_EDITADO`, `VENCIMENTO_ALTERADO`, `TITULO_CANCELADO` (fase 3 acrescenta os de recebimento). Cada título parcelado registra o seu próprio histórico.

## Categorias de receita em uso
`DELETE /categorias-receita/{id}` passa a devolver **409** quando há título a receber na categoria
("Não é possível excluir a categoria "X", pois ela está em uso por títulos a receber. Desative-a."). Desativar categoria em uso é permitido (títulos existentes permanecem).

## Concorrência
Edição, alteração de vencimento e cancelamento bloqueiam a linha do título (`SELECT ... FOR UPDATE`): duas alterações simultâneas no mesmo título são serializadas.

## Erros

| Situação | Status |
| --- | --- |
| Sem token | 401 |
| Sem permissão (mesmo com corpo vazio ou inválido) | 403 |
| Campo obrigatório ausente, cliente inexistente/desativado, categoria/centro/forma desativados ou inexistentes, valor ≤ 0 ou acima do teto, vencimento anterior à emissão, parcelamento inválido, motivo curto, regra de edição/cancelamento violada | 400 |
| Título não encontrado | 404 |

## Visibilidade do saldo dos bancos

O saldo dos bancos é uma informação restrita. A regra é aplicada no servidor; a tela apenas acompanha.

- **Quem vê o saldo:** quem possui `MOVIMENTACAO_VISUALIZAR`, `MOVIMENTACAO_GERENCIAR` ou `CADASTRO_FINANCEIRO_GERENCIAR` (e o administrador). As permissões `CONTA_PAGAR_*` mantêm o comportamento anterior (continuam vendo), pois o Contas a Pagar não foi alterado.
- **Quem não vê:** perfis que possuem apenas permissões `CONTA_RECEBER_*`.
- **Efeito em `GET /bancos` e `GET /bancos/{id}`:** para quem não vê, os campos `saldoInicial` e `saldoAtual` vêm `null`. Os demais campos (`id`, `nome`, `tipo`, `ativo`, etc.) continuam normais, de modo que o banco segue utilizável em filtros e no diálogo de recebimento.
- **Aviso de saldo negativo** (estorno de recebimento, campo `aviso`): para quem não vê o saldo, o texto não traz o valor. Exemplo: `Atenção: após esta operação, o saldo do banco "Caixa" ficou negativo.` Para quem vê, o texto continua com o valor entre parênteses.
- `GET /movimentacoes-banco` (extrato, com `saldoApos`) e `GET /movimentacoes-banco/saldos` já exigiam permissão de movimentação e continuam respondendo 403 para perfis só de Contas a Receber.
- O front deve tratar `saldoAtual`/`saldoInicial` nulos (ocultar a coluna/valor) e não depender de `aviso` conter o valor.

## Pagador avulso (migration V71)

O título pode ser lançado para quem **não é cliente cadastrado** (aluguel, devolução de fornecedor, reembolso, venda avulsa, rendimentos). O cliente deixou de ser obrigatório: informa-se **ou** o cliente **ou** o pagador avulso.

### Requisição (`POST /contas-receber` e `PUT /contas-receber/{id}`)
Novos campos opcionais no corpo: `pagadorNome` e `pagadorDocumento`.
```json
{ "descricao": "Aluguel da vitrine", "clienteId": null, "pagadorNome": "Loja Vizinha Ltda", "pagadorDocumento": "12.345.678/0001-90",
  "categoriaId": 2, "valor": 800.00, "vencimento": "2026-12-10" }
```
| Regra | Resultado |
| --- | --- |
| Nenhum dos dois (`clienteId` e `pagadorNome`) | 400 "Informe o cliente ou o pagador avulso." |
| Ambos | 400 "Informe apenas o cliente ou o pagador avulso." |
| `pagadorNome` | `trim`, convertido para **MAIÚSCULAS**, de 2 a 150 caracteres (400 "O nome do pagador avulso deve ter entre 2 e 150 caracteres.") |
| `pagadorDocumento` (opcional) | Aceita com ou sem máscara; gravado **só com dígitos**; 11 (CPF) ou 14 (CNPJ) dígitos, senão 400 "O documento do pagador avulso deve ter 11 dígitos (CPF) ou 14 dígitos (CNPJ)." Os dígitos verificadores **não** são conferidos. Sem cliente e sem `pagadorNome`, informar só `pagadorDocumento` é 400 ("Informe o cliente ou o pagador avulso."); com `clienteId`, qualquer `pagadorNome` ou `pagadorDocumento` é 400 ("Informe apenas o cliente ou o pagador avulso.") |
| `clienteId` informado | Regras anteriores (cliente existente e ativo) |

No parcelamento todas as parcelas herdam o pagador (ou o cliente). Para "duplicar", reenvie os mesmos campos.

### Edição
- Trocar de cliente para avulso, de avulso para cliente ou alterar o pagador avulso só é permitido **sem valor recebido** (mesma regra da troca de cliente). Com recebimento ativo: 400 "O pagador não pode ser alterado em título com recebimento. Estorne os recebimentos antes." (a mensagem anterior sobre cliente foi substituída por essa).
- Título `RECEBIDO` altera só a observação; `CANCELADO` não altera nada (regras já existentes).
- Ao trocar, o histórico registra `pagador: "ANTES" → "DEPOIS"` (nome de exibição).

### Respostas (lista, resumo, parcelas, detalhe, criação)
Campos novos em todo título; os atuais foram mantidos:
```json
{ "cliente": null, "pagadorNome": "LOJA VIZINHA LTDA", "pagadorDocumento": "12345678000190",
  "pagadorExibicao": "LOJA VIZINHA LTDA", "tipoPagador": "AVULSO" }
```
- `cliente` agora pode ser **`null`** (título avulso); quando há cliente continua `{ id, nome, documento }`, e `pagadorNome`/`pagadorDocumento` vêm `null`.
- `pagadorExibicao`: nome do cliente ou o `pagadorNome`; `tipoPagador`: `CLIENTE` ou `AVULSO`. A tela não precisa decidir.
- `pagadorDocumento` vem sem máscara.

### Lista (`GET /contas-receber`)
- `busca` também procura em `pagadorNome` e `pagadorDocumento` (com ou sem máscara).
- Novo filtro `tipoPagador=CLIENTE|AVULSO` (outro valor: 400 "Tipo de pagador inválido. Use CLIENTE ou AVULSO."). `clienteId` continua e **não** traz avulsos.
- `ordenar=cliente` ordena pelo nome de exibição (cliente ou avulso), sem diferenciar maiúsculas; avulsos entram na ordenação.
- Totais consideram os avulsos (o filtro é o mesmo da lista).

### Recebimento, extrato e histórico
A descrição da entrada no extrato passa a usar o nome de exibição: `Recebimento do título nº N - descrição - NOME DO CLIENTE OU DO PAGADOR AVULSO`. Títulos e movimentações antigos não mudam. O histórico de criação diz "Título criado para o cliente X..." ou "Título criado para o pagador avulso X...".

### O que não muda
- `GET /contas-receber/clientes` (seletor) e a **situação do cliente** (`/contas-receber/clientes/{id}/situacao` e `/contas-receber/clientes/situacao`): continuam **só para clientes cadastrados**; títulos avulsos não entram nesses cálculos nem nos totais.
- Cancelamento, lote, anexos, comprovantes, estorno: sem mudança de regra, funcionam igualmente para título avulso.
