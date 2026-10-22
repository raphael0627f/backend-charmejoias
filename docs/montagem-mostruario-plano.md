# Plano: Montagem de Mostruário (novo módulo `montagem`)

## Contexto
Hoje `Mostruario` é só um cadastro (id + descrição) referenciado por `Consignacao` e `Pedido`. Falta o conceito de **montar** um mostruário para um cliente: quem é o cliente, quais consignações e pedidos dele entram, instruções para quem monta e a data de entrega, com andamento. Decisões já tomadas com o usuário:
- **Nova entidade** `MontagemMostruario`, separada do `Mostruario`, que não muda.
- Consignações e pedidos podem ser **vinculados (já existentes)** ou **criados já vinculados** pela montagem, reaproveitando `PedidoService.create` e `ConsignacaoService.create`.
- Status: `ABERTA → EM_MONTAGEM → CONCLUIDA` ou `CANCELADA` (com motivo).
- Concluir exige ao menos 1 consignação OU 1 pedido de O.S. (só O.S. dispensa a consignação; outro tipo de pedido exige consignação).
- Sem relatório/PDF por enquanto. Sem testes unitários. Frontend por conta do usuário. Fases com OK antes de cada uma.

## Modelo de dados — migration `V51__create-montagens-mostruario.sql`
(última existente: V50; nunca editar migrations já aplicadas)
- `montagens_mostruario`: `id BIGSERIAL`, `cliente_id UUID NOT NULL` (FK clientes), `mostruario_id BIGINT NULL` (FK mostruarios; opcional, usado como padrão ao criar pedido/consignação pela montagem), `instrucoes TEXT`, `data_entrega DATE NOT NULL`, `status VARCHAR(20) NOT NULL` (CHECK dos 4 valores), `motivo_cancelamento TEXT`, `iniciada_em TIMESTAMP`, `iniciada_por_id` (FK usuarios), `concluida_em TIMESTAMP`, auditoria (`created_at/updated_at/created_by_id/updated_by_id`, como `mostruarios`). Índices em `cliente_id`, `status`, `data_entrega`.
- `montagens_mostruario_consignacoes (montagem_id, consignacao_id)` e `montagens_mostruario_pedidos (montagem_id, pedido_id)`: PK composta e **UNIQUE em `consignacao_id` / `pedido_id`** (um item só pode estar em uma montagem). Hipótese a confirmar: se o usuário quiser o mesmo pedido em mais de uma montagem, é só remover o UNIQUE.
- Permissão: `MONTAGEM_GERENCIAR` em `usuario/Permissao.java` + insert para o perfil ADMIN, mesmo padrão de `V50__permissao-backup-admin.sql` (no mesmo V51 ou num V52 separado).

## Fase 1 — CRUD e status
Pacote `com.manager.app.montagem` (seguir o padrão de `mostruario/`: Entity, Repository, Service, Validation, Mapper, Controller, `dto/`).
- `MontagemMostruario` (entity com `@EntityListeners(AuditingEntityListener.class)`), `StatusMontagem` (enum), `MontagemMostruarioRepository` (JpaSpecificationExecutor).
- Endpoints (`/montagens`, `@PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")` nas escritas):
  - `GET /montagens?busca=&status=&clienteId=&dataEntregaDe=&dataEntregaAte=&page=` (busca por nome do cliente; padrão `comBusca` de `MostruarioService`/`SpecificationUtils.likeIgnoreCase`)
  - `GET /montagens/{id}` (traz cliente, mostruário, instruções, datas, status, listas de consignações e pedidos resumidos)
  - `POST /montagens`, `PUT /montagens` (`clienteId`, `mostruarioId?`, `instrucoes`, `dataEntrega`)
  - `DELETE /montagens/{id}` (só `ABERTA` e sem vínculos; senão usar cancelar)
  - `PATCH /montagens/{id}/iniciar` (`ABERTA → EM_MONTAGEM`, grava `iniciadaEm`/`iniciadaPor`)
  - `PATCH /montagens/{id}/concluir` (`EM_MONTAGEM → CONCLUIDA`, exige ≥1 consignação; grava `concluidaEm`)
  - `PATCH /montagens/{id}/cancelar` body `{motivo}` obrigatório (`ABERTA` ou `EM_MONTAGEM`; padrão de `PedidoService.cancelar(id, motivo)`)
- Regras (em `MontagemValidation`, exceções existentes `ItemInvalidoException`/`ObjetoNaoEncontradoException`):
  - `ABERTA`: tudo editável. Trocar o cliente só se não houver vínculos.
  - `EM_MONTAGEM`: cliente travado; instruções, data de entrega e vínculos continuam editáveis.
  - `CONCLUIDA` / `CANCELADA`: somente leitura (qualquer escrita ou vínculo retorna 400/409).
  - Transições inválidas retornam erro claro (ex.: concluir sem estar em montagem).

## Fase 2 — Vínculos
- `POST /montagens/{id}/consignacoes/{consignacaoId}` e `DELETE ...` ; `POST /montagens/{id}/pedidos/{pedidoId}` e `DELETE ...`.
- Validações: montagem em `ABERTA`/`EM_MONTAGEM`; item existe; **cliente do item == cliente da montagem**; pedido não pode estar `CANCELADO`; item ainda não vinculado a outra montagem (mensagem diz qual).
- Ao desvincular, o pedido/consignação **não é alterado nem excluído**, só sai da lista.
- Impedir vínculo órfão: `ConsignacaoService` e `PedidoService` devem bloquear exclusão enquanto estiverem numa montagem ativa (`ObjetoEmUsoException`, como em `MostruarioService.delete`); pedido cancelado continua vinculado, mas aparece como cancelado.

## Fase 3 — Criar já vinculado
- `POST /montagens/{id}/pedidos` body = `PedidoRequestDTO` atual; `POST /montagens/{id}/consignacoes` body = `ConsignacaoRequestDTO` atual.
- No service da montagem: validar status, **forçar `clienteId` = cliente da montagem** (ignora/rejeita o enviado), usar `mostruarioId` da montagem como padrão se o body não trouxer, delegar para `PedidoService.create` / `ConsignacaoService.create` (reaproveita toda a validação de itens, preços e estoque) e então inserir o vínculo, tudo na mesma transação. Nenhuma regra de pedido/consignação é duplicada.
- Resposta: o pedido/consignação criado (mesmo DTO dos endpoints atuais) com `201`.

## Fase 4 — Documento para o frontend
- Criar `docs/montagem-mostruario-integracao-frontend.md` no padrão dos demais (contrato dos endpoints, DTOs, erros 400/404/409, regras de status e travas por status).

## Arquivos
Novos: `montagem/{MontagemMostruario, StatusMontagem, MontagemMostruarioRepository, MontagemMostruarioService, MontagemValidation, MontagemMostruarioMapper, MontagemMostruarioController, dto/*}`, `db/migration/V51__...sql`. Alterados: `usuario/Permissao.java` (+`MONTAGEM_GERENCIAR`), `consignacao/ConsignacaoService` e `pedido/PedidoService` (bloqueio de exclusão com vínculo ativo, Fase 2). Reaproveitar: `SpecificationUtils.likeIgnoreCase`, `ModoBusca`, `PedidoService.create/cancelar`, `ConsignacaoService.create`, `ObjetoEmUsoException`, `ItemInvalidoException`.

## Verificação
- `./mvnw -q -DskipTests compile` e subir a aplicação (Flyway aplica V51).
- Fluxo manual pela API: criar montagem → vincular consignação do mesmo cliente (ok) e de outro cliente (400) → criar pedido pela montagem (cliente forçado) → iniciar → tentar trocar cliente (rejeitado) → concluir sem consignação (rejeitado) e com consignação (ok) → cancelar outra com e sem motivo → tentar vincular em montagem concluída (rejeitado) → conferir permissão com usuário sem `MONTAGEM_GERENCIAR` (403).
