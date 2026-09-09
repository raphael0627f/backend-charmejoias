# Filtros de listagem — Notas Fiscais, Consignações, Acertos e Pré-acertos

Documento para o frontend implementar os novos filtros/busca nos 5 endpoints de listagem paginada.

---

## 1. Notas Fiscais — `GET /notas-fiscais`

Novos query params (todos opcionais, além dos já existentes `page`/`size`/`sort`):

| Param | Tipo | Descrição |
|---|---|---|
| `busca` | string | Busca por **nome do cliente** (parcial, sem diferenciar maiúsc./minúsc.) OU, se o texto digitado for um número, também casa com **id** ou **número da nota** (exato) |
| `status` | enum | Um de: `RASCUNHO`, `ENVIADA`, `AUTORIZADA`, `REJEITADA`, `CANCELAMENTO_SOLICITADO`, `CANCELADA` |
| `dataInicio` | data `dd/MM/yyyy` | Início do range de criação da nota (inclusive) |
| `dataFim` | data `dd/MM/yyyy` | Fim do range de criação da nota (inclusive) |

Exemplo: `GET /notas-fiscais?busca=Raphael&status=AUTORIZADA&dataInicio=01/09/2026&dataFim=07/09/2026&page=0&size=20`

**Importante sobre `busca`**: se o usuário digitar `"123"`, o backend tenta casar exato com `id` e `numero` da nota **e** também busca parcial no nome do cliente (então também retornaria clientes cujo nome contenha "123", embora seja raro). Se digitar um texto não-numérico (`"Raphael"`), só o filtro de nome do cliente é aplicado.

---

## 2. Consignações — `GET /consignacoes`

Params já existentes: `temCliente` (boolean), `temAcerto` (boolean).

Novos:

| Param | Tipo | Descrição |
|---|---|---|
| `busca` | string | Agora também busca por **id** (exato, se numérico), **código do cliente** e **descrição** (já existia busca por nome do cliente e descrição) |
| `confirmada` | boolean | Filtra por consignação confirmada (`true`) ou pendente (`false`) — **este é o "status"** da consignação, já que ela não tem um enum de status, só esse booleano |
| `dataInicio` / `dataFim` | data `dd/MM/yyyy` | Range de criação |

Exemplo: `GET /consignacoes?busca=Maria&confirmada=false&dataInicio=01/09/2026&dataFim=07/09/2026`

---

## 3. Acerto de Consignação — `GET /acertos-consignacao`

Params já existentes: `consignacaoId` (Long), `confirmado` (boolean — **este já era o filtro de status**, não mudou).

Novos:

| Param | Tipo | Descrição |
|---|---|---|
| `busca` | string | Agora também busca por **id do próprio acerto** e **id da consignação vinculada** (exato, se numérico), além de **código/nome do cliente** da consignação (já existia busca por observações) |
| `dataInicio` / `dataFim` | data `dd/MM/yyyy` | Range de criação |

Exemplo: `GET /acertos-consignacao?busca=45&confirmado=true&dataInicio=01/09/2026&dataFim=07/09/2026`

---

## 4. Pré-acertos — `GET /pre-acertos-consignacao`

Param já existente: `consignacaoId` (Long).

Novos:

| Param | Tipo | Descrição |
|---|---|---|
| `busca` | string | Agora também busca por **id do próprio pré-acerto** e **id da consignação vinculada** (exato, se numérico, quando existir), além de **código/nome do cliente** (já existia busca por observações e nome do cliente) |
| `dataInicio` / `dataFim` | data `dd/MM/yyyy` | Range de criação |

Sem filtro de status — pré-acerto não tem esse conceito.

---

## 5. Acerto Joia — `GET /acertos-joia`

| Param | Tipo | Descrição |
|---|---|---|
| `busca` | string | Busca só por **id** (match exato, precisa ser numérico — se digitar texto não numérico, a busca não retorna nada) |
| `dataInicio` / `dataFim` | data `dd/MM/yyyy` | Range de criação |

**Atenção**: antes esse endpoint também tentava buscar por `busca` no campo de data (`createdAt`), mas isso nunca funcionou de verdade (bug de SQL — ver seção abaixo) e foi removido, já que agora existe o filtro de data dedicado (`dataInicio`/`dataFim`), que é o jeito correto de filtrar por data.

---

## Formato de data

Todos os `dataInicio`/`dataFim` usam formato brasileiro **`dd/MM/yyyy`** (ex: `06/09/2026`), não ISO. O range é **inclusivo dos dois lados** — `dataInicio=06/09/2026&dataFim=07/09/2026` traz notas/registros criados do dia 06 às 00:00 até o dia 07 às 23:59:59.

Se só mandar `dataInicio`, filtra "a partir de". Se só mandar `dataFim`, filtra "até". Pode mandar os dois juntos para um range fechado.

---

## Bug corrigido (transparente pro frontend, mas relevante)

Durante a implementação foi encontrado um bug: o Postgres não aceita `LOWER()` em cima de coluna numérica (`bigint`), e o cast que o Hibernate gerava não resolvia isso — dava erro 503 sempre que alguém buscava por id/número em texto livre com `LIKE`. Esse bug já existia mesmo antes dessas mudanças (no Acerto Joia). Foi corrigido trocando toda comparação por id/número dentro de `busca` para **igualdade exata quando o termo digitado é numérico**, em vez de `LIKE`. Na prática, para o frontend isso significa: buscar por id/número só retorna resultado se o texto digitado for **exatamente** o número (não busca parcial, tipo "12" não vai casar com id "120"). Buscar por nome/código/observação continua sendo parcial normalmente.

---

## Regra de negócio nova (não é filtro, mas relevante pro fluxo)

`PUT /consignacoes/{id}/confirmar` agora retorna **400** com a mensagem `"Consignação sem cliente não pode ser confirmada. id: {id}"` se a consignação não tiver cliente vinculado. Na tela de confirmação de consignação, o frontend deve tratar esse erro (ex: desabilitar o botão "Confirmar" quando a consignação for avulsa/sem cliente, ou mostrar essa mensagem de erro do backend).
