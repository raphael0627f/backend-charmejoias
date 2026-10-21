# Montagem de Mostruário — guia de integração frontend

Módulo novo: uma **montagem** reúne um cliente, as consignações e os pedidos dele que entram no mostruário, as instruções para quem monta e a data de entrega, com andamento por status. Não altera o cadastro `Mostruario` existente, nem pedidos/consignações (só os referencia).

Todas as rotas exigem autenticação. As que escrevem (POST/PUT/PATCH/DELETE) exigem a permissão **`MONTAGEM_GERENCIAR`** (já concedida ao perfil ADMIN pela migration; os demais perfis precisam receber a permissão). Sem ela: `403`.

---

## 1. Status e regras

| Status | Significado | O que pode |
|---|---|---|
| `ABERTA` | cadastrada, ninguém começou | editar tudo; trocar cliente (só se não houver vínculos); vincular/desvincular; iniciar; cancelar; excluir (só sem vínculos) |
| `EM_MONTAGEM` | alguém clicou em "Iniciar montagem" | editar instruções, data de entrega e mostruário; vincular/desvincular; concluir; cancelar. **Cliente travado** |
| `CONCLUIDA` | finalizada | somente leitura |
| `CANCELADA` | cancelada com motivo | somente leitura |

Transições:

```
ABERTA ──iniciar──▶ EM_MONTAGEM ──concluir──▶ CONCLUIDA
  │                      │
  └────── cancelar ──────┴──▶ CANCELADA
```

- **Concluir** exige estar `EM_MONTAGEM` e ter **ao menos 1 consignação** vinculada (pedidos são opcionais).
- Ao **concluir**, os pedidos da montagem com status `SEPARADO` passam para `ENVIADO` (na mesma transação; `CANCELADO` e `ENVIADO` não mudam). A resposta já traz os pedidos com o novo status.
- **Pedido sem cliente (avulso, como a consignação):** `clienteId` é opcional em `POST/PUT /pedidos`; a `dataEntrega` só é obrigatória quando há cliente. `GET /pedidos?temCliente=false` lista só os avulsos. Pedido sem cliente/data **pode ser vinculado** a uma montagem; enquanto estiver vinculado, `PUT /pedidos` rejeita (400) informar cliente ou data que ele ainda não tem. Ao **concluir** a montagem, os pedidos não cancelados sem cliente e/ou sem data recebem o cliente e a data de entrega da montagem. Pedido sem cliente ou data **não pode ficar Enviado** (400 na troca de status). `GET /pedidos/{id}` traz `montagemId` (montagem a que está vinculado).
- Pedido **cancelado** continua vinculado à montagem (histórico), mas não conta: `quantidadePedidos` da listagem não o inclui, a conferência o ignora e ele não é alterado ao concluir.
- **Cancelar** exige `motivo` com no mínimo 5 caracteres e status `ABERTA` ou `EM_MONTAGEM`.
- **Excluir** só vale para `ABERTA` e sem pedidos/consignações vinculados; nos demais casos, usar cancelar.
- `iniciadaEm`/`iniciadaPor` são gravados ao iniciar; `concluidaEm` ao concluir.
- Qualquer escrita em montagem `CONCLUIDA`/`CANCELADA` retorna `400`.

---

## 2. Objeto retornado (`MontagemResponseDTO`)

```json
{
  "id": 1,
  "clienteId": "uuid",
  "cliente": { "codigo": "123", "nome": "Fulana de Tal" },
  "mostruario": { "id": 1, "descricao": "Mostruário Prata 001", "createdAt": "...", "updatedAt": "..." },
  "instrucoes": [
    { "id": 1, "texto": "Separar peças douradas em caixa à parte", "concluida": true, "concluidaEm": "2026-10-20T10:00:00", "concluidaPor": { "...": "UsuarioResumoDTO" } },
    { "id": 2, "texto": "Conferir a etiqueta de cada produto", "concluida": false, "concluidaEm": null, "concluidaPor": null }
  ],
  "dataEntrega": "2026-11-05",
  "status": "EM_MONTAGEM",
  "motivoCancelamento": null,
  "iniciadaEm": "2026-10-20T09:15:00",
  "iniciadaPor": { "...": "UsuarioResumoDTO" },
  "concluidaEm": null,
  "consignacoes": [
    { "id": 8711, "descricao": null, "tipo": "PRODUTO", "dataEntrega": null, "confirmada": false, "quantidadeTotal": 12, "valorTotal": 840.00 }
  ],
  "pedidos": [
    { "id": 52, "status": "PENDENTE", "categoria": "...", "tipo": "PRODUTO", "dataPedido": "2026-10-18", "dataEntrega": "2026-11-01", "observacao": null }
  ],
  "createdAt": "...", "updatedAt": "...", "createdBy": { }, "updatedBy": { }
}
```

`mostruario` e `iniciadaPor` podem vir `null`. `consignacoes` e `pedidos` são resumos; para o detalhe usar `GET /consignacoes/{id}` e `GET /pedidos/{id}`.

---

## 3. Endpoints

### Listagem e detalhe
- `GET /montagens?busca=&modoBusca=&status=&clienteId=&dataEntregaDe=&dataEntregaAte=&page=&size=&sort=`
  - `busca`: nome do cliente (like, case-insensitive; `modoBusca` como nas outras listagens).
  - `status`: um dos valores acima. `clienteId`: UUID.
  - `dataEntregaDe` / `dataEntregaAte`: formato **`yyyy-MM-dd`** (ISO), inclusivos.
  - Ordenação padrão: `id`.
- `GET /montagens/{id}` — `404` se não existir.

### Cadastro
- `POST /montagens` — `201` + `Location`.
  ```json
  { "clienteId": "uuid", "mostruarioId": 1, "instrucoes": [ { "texto": "Separar peças douradas" } ], "dataEntrega": "2026-11-05" }
  ```
  - `clienteId` (cliente ativo) e `dataEntrega` obrigatórios; `mostruarioId` e `instrucoes` opcionais. Nasce `ABERTA`. `instrucoes` é a lista completa de itens (`texto`, e `id` ao editar uma existente): na edição, os itens com `id` são mantidos (preservando se já foram feitos), os sem `id` são criados e os que não vierem são removidos.
  - **Vínculo automático:** ao criar, o backend já vincula (sem perguntar) as consignações **não confirmadas** e os pedidos **não cancelados** do mesmo cliente com a mesma `dataEntrega`, exceto os que já estão em outra montagem. A resposta já traz as listas preenchidas.
- `PUT /montagens` — mesmo corpo + `"id"`. Troca de `clienteId` só em `ABERTA` e sem vínculos (senão `400`).
- `DELETE /montagens/{id}` — `204`. `400` se não estiver `ABERTA`; `409` se tiver vínculos.

### Instruções (checklist)
- `PATCH /montagens/{id}/instrucoes/{instrucaoId}` — `{ "concluida": true }` marca/desmarca a instrução como feita (grava `concluidaEm`/`concluidaPor`). Retorna a montagem. Só com a montagem `ABERTA` ou `EM_MONTAGEM`. Não depende de salvar o `PUT /montagens`.
- A montagem **só pode ser concluída com todas as instruções marcadas como feitas**; `GET /montagens/{id}/conferencia` lista as pendentes em `instrucoesPendentes` e conta no `ok`.

### Andamento
- `PATCH /montagens/{id}/iniciar` — sem corpo. `ABERTA → EM_MONTAGEM`.
- `PATCH /montagens/{id}/concluir` — sem corpo. `EM_MONTAGEM → CONCLUIDA` (exige ≥ 1 consignação).
- `PATCH /montagens/{id}/cancelar` — `{ "motivo": "mínimo 5 caracteres" }`.

Todos retornam a montagem atualizada (`200`).

### Vincular / desvincular (itens já existentes)
- `POST /montagens/{id}/consignacoes/{consignacaoId}` · `DELETE /montagens/{id}/consignacoes/{consignacaoId}`
- `POST /montagens/{id}/pedidos/{pedidoId}` · `DELETE /montagens/{id}/pedidos/{pedidoId}`

Retornam a montagem atualizada. Regras do vínculo:
- montagem `ABERTA` ou `EM_MONTAGEM`;
- o **cliente do pedido/consignação precisa ser o da montagem** (consignação sem cliente é recusada) → `400`;
- pedido `CANCELADO` não pode ser vinculado → `400`;
- um pedido/consignação só pode estar em **uma** montagem: se já estiver em outra, `409` com o id dela; se já estiver nesta, `400`;
- desvincular **não altera nem exclui** o pedido/consignação; `404` se o item não estava vinculado.

Dica de tela: ao escolher o item a vincular, listar `GET /consignacoes?clienteId={cliente}` e `GET /pedidos?clienteNome=...` filtrando pelo cliente da montagem.

### Criar já vinculado
- `POST /montagens/{id}/pedidos` — corpo **igual ao de `POST /pedidos`**; retorna `201` com o `PedidoResponseDTO`.
- `POST /montagens/{id}/consignacoes` — corpo **igual ao de `POST /consignacoes`**; retorna `201` com o `ConsignacaoResponseDTO`.

Particularidades:
- o `clienteId` do corpo é **ignorado e substituído** pelo cliente da montagem (pode omitir);
- o mostruário **não** é herdado da montagem: vale o `mostruarioId` do corpo (opcional no pedido; na consignação é obrigatório, sem ele `400 "Mostruário é obrigatório"`);
- o `id` do corpo é ignorado (sempre cria);
- as validações de itens, preços etc. são as mesmas de `/pedidos` e `/consignacoes`; erros de campo voltam como `400` com as mensagens separadas por `;`;
- a criação e o vínculo são atômicos: se qualquer parte falhar, nada é salvo;
- exige montagem `ABERTA` ou `EM_MONTAGEM`.

---

## 4. Erros

| Código | Quando |
|---|---|
| `400` | validação, transição de status inválida, montagem somente leitura, cliente diferente, pedido cancelado, concluir sem consignação |
| `403` | usuário sem `MONTAGEM_GERENCIAR` |
| `404` | montagem / pedido / consignação / cliente / mostruário inexistente, ou item não vinculado ao desvincular |
| `409` | excluir montagem com vínculos; item já vinculado a outra montagem |

Corpo de erro no padrão dos demais módulos (`timestamp`, `status`, `message`, `path`).

---

## 5. Sugestão de fluxo de tela

1. **Nova montagem**: cliente, mostruário (opcional), data de entrega, instruções → `POST /montagens`.
2. **Detalhe**: mostrar status, abas Consignações e Pedidos, com botões "Vincular existente" e "Criar novo" (este abre o formulário atual de pedido/consignação com o cliente já fixo).
3. **Iniciar montagem** quando começar a separar; **Concluir** quando terminar (habilitar só com ≥ 1 consignação); **Cancelar** pede o motivo.
4. Em `CONCLUIDA`/`CANCELADA`, esconder todas as ações de escrita.
