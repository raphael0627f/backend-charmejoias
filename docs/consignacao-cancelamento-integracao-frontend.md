# Cancelamento de consignação — contrato para o frontend

Migration: `V61__consignacao-cancelamento.sql` (aditiva: `cancelada` BOOLEAN NOT NULL DEFAULT FALSE, `motivo_cancelamento`,
`cancelada_em`, `cancelada_por_id`). Consignações existentes ficam com `cancelada = false`.

## Endpoint

`POST /consignacoes/{id}/cancelar` — permissão `CONSIGNACAO_GERENCIAR` (mesmo padrão de `POST /pedidos/{id}/cancelar`).

Request:
```json
{ "motivo": "Cliente desistiu da entrega" }
```
Resposta 200: `ConsignacaoResponseDTO` (com os campos novos abaixo).

## Campos novos (`ConsignacaoResponseDTO` e `ConsignacaoResumoDTO`)

| Campo | Tipo | Observação |
|---|---|---|
| `cancelada` | boolean | `false` por padrão |
| `motivoCancelamento` | string \| null | motivo informado (já com trim) |
| `canceladaEm` | datetime \| null | ISO-8601 |
| `canceladaPor` | `UsuarioResumoDTO` \| null | `{ id, usuario, primeiroNome, sobrenome }` (mesmo formato de `createdBy`) |

## Listagem

`GET /consignacoes` **sem filtro devolve todas, inclusive as canceladas**. Filtro opcional `?cancelada=true|false`.
Não existe parâmetro `incluirCanceladas`.

- Telas de acerto, pré-acerto, montagem e planejador que escolhem consignação devem enviar `cancelada=false`.
- O seletor "Adicionar produtos de outro documento" (consignação, pré-acerto, nota fiscal) usa a listagem **sem** o filtro e
  `GET /consignacoes/{id}`, que devolve a cancelada normalmente (somente leitura; a origem nunca é alterada).

## Regras e erros

Mensagem em `menssage` (padrão `docs/padrao-mensagens.md`).

| Situação | Status | `menssage` |
|---|---|---|
| Motivo ausente/vazio (corpo) | 400 | `O campo Motivo é obrigatório.` (com `errors[]`) |
| Motivo com menos de 5 caracteres | 400 | `O motivo do cancelamento deve ter ao menos 5 caracteres.` |
| Já cancelada | 400 | `A consignação já está cancelada.` |
| Consignação confirmada | 409 | `Não é possível cancelar uma consignação confirmada.` |
| Possui pré-acerto ou acerto | 409 | `Não é possível cancelar a consignação, pois ela possui pré-acerto ou acerto.` |
| Vinculada a montagem ABERTA/EM_MONTAGEM | 409 | `Não é possível cancelar a consignação, pois ela está vinculada a uma montagem em andamento. Desvincule-a da montagem antes de cancelar.` |
| Não encontrada | 404 | `Consignação não encontrada.` |

Cancelamento é **definitivo** (não há reativação). Só consignação não confirmada pode ser cancelada.
Vinculada a montagem concluída ou cancelada pode ser cancelada.

## Consignação cancelada é somente leitura

| Operação | Resultado |
|---|---|
| `PUT /consignacoes` (editar) | 409 `Não é possível editar uma consignação cancelada.` |
| `PUT /consignacoes/{id}/confirmar` | 400 `Não é possível confirmar uma consignação cancelada.` |
| Adicionar produtos (conferência da montagem) | 409 `Não é possível editar uma consignação cancelada.` |
| Criar/editar pré-acerto, acerto de consignação, acerto de joia, vincular à montagem, planejador de rotas | 400 `Não é possível utilizar uma consignação cancelada.` |
| Vínculo automático à montagem (mesmo cliente e data) | ignora canceladas |
| `GET /consignacoes/{id}`, `GET /consignacoes/{id}/relatorio` | continuam funcionando (leitura/impressão; o PDF não muda) |

Não há exclusão de consignação na API. Não existe cálculo de disponibilidade/estoque "em consignação" no backend
(a quantidade é somada pela própria consulta de consignações; use `cancelada=false`).
