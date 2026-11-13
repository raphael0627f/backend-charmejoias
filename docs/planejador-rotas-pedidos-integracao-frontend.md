# Planejador de rotas — número da montagem e pedidos a deixar (contrato para o frontend)

Migration: `V62__planejador-rota-pedidos-deixar.sql` (aditiva: tabela nova `planejador_rota_pedidos_deixar`, única por
`(planejador_rota_cliente_id, pedido_id)`). Rotas antigas continuam funcionando (`pedidosDeixar: []`).

## 1. `montagemId` nas consignações

`montagemId` (Long, `null` se não tiver) em cada consignação de `consignacoesDeixar` e `consignacoesTrazer` da rota
(`GET/POST/PUT /planejador-rotas`), e também em:

- `GET /consignacoes` (`ConsignacaoResumoDTO`) — para os seletores de consignação do planejador;
- `GET /consignacoes/{id}` (`ConsignacaoResponseDTO`). Nas respostas de `POST/PUT /consignacoes`, confirmar e cancelar o campo vem `null`.

Regra: a consignação pode estar ligada a mais de uma montagem (tabela `montagens_mostruario_consignacoes`); vale a
**mais recente (maior id) que não esteja CANCELADA**. Só em montagens canceladas = `null`. Busca em lote (uma consulta
para a página/rota inteira, sem N+1).

## 2. Pedidos a deixar

### Request (item do cliente em `POST/PUT /planejador-rotas`)

Campo opcional `pedidosDeixar` (mesmo formato de `consignacoesDeixar`):

```json
{
  "clienteId": "2b8fb937-8dbe-48ee-add6-5269b08c7b61",
  "enderecoId": 5961,
  "consignacoesDeixar": [],
  "pedidosDeixar": [ { "pedidoId": 124 }, { "pedidoId": 125, "confirmada": false } ]
}
```

- `pedidoId` obrigatório; `confirmada` opcional (omitido = `false`; só faz sentido preencher na edição pós-visita).
- Consignações a deixar e pedidos a deixar podem coexistir no mesmo cliente (o backend não proíbe).
- Na edição (`PUT`) envie o `id` do item do cliente, como já é feito hoje; sem o `id` o item é recriado e o mesmo pedido
  gera `409 Já existe um registro com os dados informados.`

### Response (item do cliente)

```json
"pedidosDeixar": [
  { "id": 124, "status": "SEPARADO", "itensCount": 1, "quantidadeTotal": 1, "valorTotal": 7.00,
    "observacao": null, "dataEntrega": "2039-05-05", "confirmada": false }
]
```

`status` é o **atual** do pedido: se ele virar ENVIADO depois, a rota gravada continua abrindo normalmente com o status novo.

### Validações (ao salvar) — `menssage`

| Situação | Status | Mensagem |
|---|---|---|
| Pedido inexistente | 404 | `Pedido não encontrado.` |
| Pedido de outro cliente (ou sem cliente) | 400 | `O pedido Nº X não pertence ao cliente selecionado.` |
| Pedido que está entrando e não está SEPARADO | 400 | `O pedido Nº X não está com o status Separado e não pode ser deixado na rota.` |
| Pedido repetido no mesmo cliente | 400 | `O pedido Nº X foi informado mais de uma vez para o mesmo cliente.` |
| `pedidoId` ausente | 400 | `O campo Pedido é obrigatório.` (com `errors[]`) |

Só o pedido que **entra agora** precisa estar SEPARADO: um pedido já gravado naquele cliente da rota segue válido mesmo que
tenha virado ENVIADO, para a edição pós-visita (confirmação) não falhar. Um pedido pode constar em mais de uma rota (igual às
consignações: não há bloqueio entre rotas).

### Lista de pedidos que podem ser deixados

`GET /pedidos` **não** tem filtro por `clienteId` (só `clienteCodigo`/`clienteNome`), então use:

`GET /planejador-rotas/clientes/{clienteId}/pedidos-para-deixar` — permissão `PLANEJADOR_ROTA_GERENCIAR`;
lista simples (sem paginação) só dos pedidos **SEPARADO** do cliente, ordenados por id, no mesmo resumo acima
(sem `confirmada`). Cliente inexistente → 404 `Cliente não encontrado.`

## 3. XLSX (`GET /planejador-rotas/{id}/xlsx`)

Colunas existentes inalteradas (incluindo `Referência` e o prefixo `DESCRIÇÃO:` nos endereços). Antes as consignações só
apareciam como contagem; agora há duas colunas novas **no final** (índices 17 e 18), um documento por linha:

- `Deixar (documentos)`: `Consignação Nº 8756 (Montagem Nº 830)` e `Pedido Nº 124` (sem o parêntese se não há montagem);
- `Trazer (documentos)`: `Consignação Nº 8800 (Montagem Nº 12)`.

`IDA`/`DEIXOU` passam a contar também os pedidos a deixar (previsto e confirmados).
