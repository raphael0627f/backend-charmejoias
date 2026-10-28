# Agenda — guia de integração frontend (contrato, etapa 1A)

> **Renomeação (V55):** o antigo campo `dataConferencia` passou a se chamar **`dataPreAcerto`** (JSON e coluna `data_pre_acerto`). Regras inalteradas; só os nomes e as mensagens de erro mudaram. Backend e frontend sobem juntos no mesmo deploy.

> Status: **implementado e testado** (migration V54, backend dev na porta 8080).

Módulo novo e independente de Rota e Consignação. O usuário cria **marcadores** em dias do calendário, com nome livre. Cada marcador é de um de dois tipos:

- `TRABALHA`: dia de trabalho, com data do **pré-acerto** e data do **acerto** obrigatórias.
- `NAO_TRABALHA`: férias, feriado, carnaval, emenda etc. Sem datas de pré-acerto/acerto.

Sábado e domingo **não são gravados**: o frontend calcula e sombreia. Um marcador no dia prevalece sobre o sombreado. A agenda nasce **vazia** (sem carga de dados).

---

## 1. Modelo (tabela `agenda_marcadores`)

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | bigint | gerado pelo backend |
| `data` | date | obrigatório, **UNIQUE** (1 marcador por dia) |
| `nome` | varchar | obrigatório, texto livre (ex.: `BM / VR / PINHERAL (VM-3)`, `FERIAS`) |
| `tipo` | varchar | `TRABALHA` ou `NAO_TRABALHA` |
| `data_pre_acerto` | date | obrigatória se `TRABALHA`; nula se `NAO_TRABALHA` |
| `data_acerto` | date | obrigatória se `TRABALHA`; nula se `NAO_TRABALHA` |
| `created_at` / `updated_at` | timestamp | gerados pelo backend |
| `created_by_id` / `updated_by_id` | uuid (usuário) | gerados pelo backend (ver `auditoria-createdby-updatedby-integracao-frontend.md`) |

## 2. Formato de dados

- Datas: **`yyyy-MM-dd`** (ISO), no JSON e nos query params (`?inicio=2026-03-01&fim=2026-03-31`). Atenção: diferente de `/rotas`, que usa `dd/MM/yyyy` nos filtros.
- `createdAt` / `updatedAt`: datetime ISO, ex.: `2026-10-25T16:01:27.905`.
- `createdBy` / `updatedBy`: objeto `{ id, usuario, primeiroNome, sobrenome }` ou `null`.
- Erros: corpo `RestErrorMenssage`, campo da mensagem é **`menssage`** (com dois s, proposital).

## 3. Regras de negócio

1. **1 marcador por dia.** Segundo marcador na mesma `data` → `409`.
2. `TRABALHA` exige `dataPreAcerto` e `dataAcerto`; `NAO_TRABALHA` exige as duas **nulas** (enviar preenchidas → `400`).
3. Ordem das datas (bloqueante): `dataPreAcerto >= data` e `dataAcerto >= dataPreAcerto`, senão `400` com mensagem clara.
4. `nome` obrigatório e não vazio (o backend aplica `trim`).
5. No `PUT`, mudar `data` para um dia já ocupado por **outro** marcador → `409`.
6. Pré-acerto e acerto podem cair em outro mês ou outro ano (ex.: dezembro → fevereiro do ano seguinte). É válido.
7. Dias úteis sem marcador são permitidos.

## 4. Permissões

| Permissão | Efeito |
|---|---|
| `AGENDA_GERENCIAR` | protege `POST`, `PUT` e `DELETE` (`@PreAuthorize`) → sem ela, `403` |
| `AGENDA_VISUALIZAR` | existe **só no enum e na tela de Perfis**; o backend **não** a verifica |

- `GET /agenda/marcadores` e `GET /agenda/marcadores/{id}` exigem apenas **estar logado**, como os demais módulos. A restrição de tela (menu e URL `/agenda` exigindo `AGENDA_VISUALIZAR` ou `AGENDA_GERENCIAR`) é feita **no frontend** (`lib/auth/module-permissions.ts`).
- O perfil `ADMIN` já recebe as duas permissões pela migration. Os demais perfis passam a não ver a Agenda até receberem a permissão.
- O frontend deve incluir `AGENDA_GERENCIAR` e `AGENDA_VISUALIZAR` na lista `PERMISSOES` (`types/auth.ts`) para aparecerem na tela de Perfis. Botões de criar/editar/excluir só com `AGENDA_GERENCIAR`.

## 5. DTOs

### Request (`POST` e `PUT`)

| Campo | Tipo | Obrigatório | Observação |
|---|---|---|---|
| `id` | number | só no `PUT` | identifica o marcador a atualizar |
| `data` | string `yyyy-MM-dd` | sim | dia do marcador |
| `nome` | string | sim | texto livre |
| `tipo` | string | sim | `TRABALHA` \| `NAO_TRABALHA` |
| `dataPreAcerto` | string `yyyy-MM-dd` \| null | só `TRABALHA` | nulo em `NAO_TRABALHA` |
| `dataAcerto` | string `yyyy-MM-dd` \| null | só `TRABALHA` | nulo em `NAO_TRABALHA` |

No `PUT`, o `id` do marcador vai **no corpo** (campo `id`, obrigatório, mesmo padrão de `/rotas` e `/mostruarios`); no `POST` ele é ignorado. `createdAt`, `updatedAt`, `createdBy` e `updatedBy` são somente leitura e ignorados se enviados.

### Response (`AgendaMarcadorResponseDTO`)

| Campo | Tipo |
|---|---|
| `id` | number |
| `data` | string `yyyy-MM-dd` |
| `nome` | string |
| `tipo` | `TRABALHA` \| `NAO_TRABALHA` |
| `dataPreAcerto` | string `yyyy-MM-dd` \| null |
| `dataAcerto` | string `yyyy-MM-dd` \| null |
| `createdAt` / `updatedAt` | datetime |
| `createdBy` / `updatedBy` | objeto resumido de usuário \| null |

## 6. Endpoints

### 6.1 `GET /agenda/marcadores?inicio=&fim=`

Lista **sem paginação**, ordenada por `data` crescente. Devolve os marcadores cujo **dia, pré-acerto OU acerto** cai dentro de `[inicio, fim]` (inclusive). Assim o frontend consegue marcar, no mês exibido, os dias de conferência e de acerto vindos de marcadores de outro mês.

| Query param | Tipo | Obrigatório |
|---|---|---|
| `inicio` | `yyyy-MM-dd` | sim |
| `fim` | `yyyy-MM-dd` | sim |

Erros: `400` se faltar `inicio`/`fim`, se o formato for inválido ou se `fim < inicio`.

Exemplo: `GET /agenda/marcadores?inicio=2026-03-01&fim=2026-03-31`

```json
[
  {
    "id": 12,
    "data": "2026-02-27",
    "nome": "BM / VR / PINHERAL (VM-3)",
    "tipo": "TRABALHA",
    "dataPreAcerto": "2026-03-09",
    "dataAcerto": "2026-03-26",
    "createdAt": "2026-10-25T16:10:00.000",
    "updatedAt": "2026-10-25T16:10:00.000",
    "createdBy": { "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "usuario": "admin", "primeiroNome": "Raphael", "sobrenome": "Ferreira" },
    "updatedBy": null
  },
  {
    "id": 13,
    "data": "2026-03-03",
    "nome": "CARNAVAL",
    "tipo": "NAO_TRABALHA",
    "dataPreAcerto": null,
    "dataAcerto": null,
    "createdAt": "2026-10-25T16:11:00.000",
    "updatedAt": "2026-10-25T16:11:00.000",
    "createdBy": null,
    "updatedBy": null
  }
]
```

No exemplo, o marcador `12` tem `data` em fevereiro, mas aparece porque o pré-acerto (09/03) e o acerto (26/03) caem em março. O frontend decide, para cada dia do mês, se o marcador é "do dia", "de conferência" ou "de acerto" comparando as três datas.

### 6.2 `GET /agenda/marcadores/{id}`

`200` com um `AgendaMarcadorResponseDTO`. `404` se não existir.

### 6.3 `POST /agenda/marcadores`

Requer `AGENDA_GERENCIAR`. Retorna `201` com header `Location` e o marcador criado.

```json
{
  "data": "2026-03-12",
  "nome": "BM / VR / PINHERAL (VM-3)",
  "tipo": "TRABALHA",
  "dataPreAcerto": "2026-03-23",
  "dataAcerto": "2026-04-09"
}
```

Marcador `NAO_TRABALHA`:

```json
{ "data": "2026-04-21", "nome": "FERIADO", "tipo": "NAO_TRABALHA", "dataPreAcerto": null, "dataAcerto": null }
```

### 6.4 `PUT /agenda/marcadores`

Requer `AGENDA_GERENCIAR`. Mesmo corpo do `POST` mais o campo `id` (obrigatório); substitui todos os campos. `200` com o marcador atualizado. `404` se o `id` não existir. `409` se a nova `data` já pertence a outro marcador.

```json
{
  "id": 12,
  "data": "2026-03-12",
  "nome": "BM / VR / PINHERAL (VM-3)",
  "tipo": "TRABALHA",
  "dataPreAcerto": "2026-03-23",
  "dataAcerto": "2026-04-09"
}
```

### 6.5 `DELETE /agenda/marcadores/{id}`

Requer `AGENDA_GERENCIAR`. `204` sem corpo (exclusão definitiva). `404` se não existir.

## 7. Erros (exemplos)

Todos no formato `RestErrorMenssage`: `timestamp`, `status`, `menssage`, `path`.

**400 — datas fora de ordem**

```json
{
  "timestamp": "2026-10-25T19:20:00.000+00:00",
  "status": "BAD_REQUEST",
  "menssage": "A data do pré-acerto não pode ser anterior à data do marcador",
  "path": "uri=/agenda/marcadores"
}
```

Outras mensagens `400` previstas:

- `"A data do acerto não pode ser anterior à data do pré-acerto"`
- `"Marcador do tipo TRABALHA exige data do pré-acerto e data do acerto"`
- `"Marcador do tipo NAO_TRABALHA não pode ter data do pré-acerto nem data do acerto"`
- `"Id do marcador é obrigatório"` (no `PUT`)
- `"Data final não pode ser anterior à data inicial"` (no `GET` da lista)
- `"Período obrigatório: informe inicio e fim (yyyy-MM-dd)"` (no `GET` da lista, sem `inicio`/`fim`)

**Atenção:** campos obrigatórios ausentes no corpo (`data`, `nome` em branco, `tipo`) são barrados pelo `@Valid` e voltam `400` no formato padrão do Spring (`{ "detail": "Invalid request content.", "instance": "/agenda/marcadores", "status": 400, "title": "Bad Request" }`), **sem** o campo `menssage` — mesmo comportamento dos outros módulos. O formulário deve validar esses campos antes de enviar.

**409 — já existe marcador no dia**

```json
{
  "timestamp": "2026-10-25T19:21:00.000+00:00",
  "status": "CONFLICT",
  "menssage": "Já existe um marcador na data 2026-03-12",
  "path": "uri=/agenda/marcadores"
}
```

**403 — sem `AGENDA_GERENCIAR`** (`POST`/`PUT`/`DELETE`)

```json
{ "timestamp": "...", "status": "FORBIDDEN", "menssage": "Acesso negado", "path": "uri=/agenda/marcadores" }
```

**404 — marcador inexistente**

```json
{ "timestamp": "...", "status": "NOT_FOUND", "menssage": "Marcador de agenda não encontrado. id: 99", "path": "uri=/agenda/marcadores/99" }
```

**401** — sem token ou token expirado (igual aos demais módulos).

> O frontend deve exibir o `menssage` recebido, sem depender do texto exato.

---

## Plano de implementação

### Migration

- **`V54__agenda-marcadores.sql`** (única migration, sem carga de dados):
  - cria `agenda_marcadores` (`id` bigserial PK, `data` date NOT NULL UNIQUE, `nome`, `tipo`, `data_pre_acerto`, `data_acerto`, `created_at`, `updated_at`, `created_by`, `updated_by`);
  - `CHECK` de consistência por tipo: `TRABALHA` → pré-acerto e acerto não nulas; `NAO_TRABALHA` → ambas nulas; mais `CHECK` de tipo válido;
  - `CHECK` de ordem: `data_pre_acerto >= data` e `data_acerto >= data_pre_acerto` quando preenchidas;
  - índices em `data_pre_acerto` e `data_acerto` (`data` já é indexada pela constraint UNIQUE);
  - concede `AGENDA_GERENCIAR` e `AGENDA_VISUALIZAR` ao perfil `ADMIN` em `perfis_permissoes` (mesmo padrão da V50, `INSERT ... SELECT ... WHERE perfis.nome = 'ADMIN'`).

### Arquivos Java (pacote `com.manager.app.agenda`)

Criar:

- `AgendaMarcador.java` (entidade, com auditoria `createdBy`/`updatedBy` no padrão dos vizinhos)
- `AgendaMarcadorTipo.java` (enum `TRABALHA`, `NAO_TRABALHA`)
- `AgendaMarcadorRepository.java` (consulta por período: `data`, `dataPreAcerto` ou `dataAcerto` entre `inicio` e `fim`; `existsByData`, `existsByDataAndIdNot`)
- `AgendaMarcadorService.java`
- `AgendaMarcadorValidation.java` (1 por dia → `ObjetoExistenteException`/409; regras por tipo e ordem das datas → `ItemInvalidoException`/400)
- `AgendaMarcadorMapper.java` (MapStruct)
- `AgendaMarcadorController.java` (`/agenda/marcadores`)
- `dto/AgendaMarcadorRequestDTO.java` e `dto/AgendaMarcadorResponseDTO.java`

Alterar:

- `usuario/Permissao.java`: adicionar `AGENDA_GERENCIAR` e `AGENDA_VISUALIZAR` ao enum.

Reaproveitado sem alteração: `RestExceptionHandler` (já mapeia 404, 409, 400 e 403 para `RestErrorMenssage`). Erros de formato de data e de enum inválido seguem o tratamento padrão de `400` do Spring.

### Verificação prevista

`./mvnw -q -DskipTests compile`, subir com perfil `dev` (Flyway aplica a V54) e testar os endpoints com `admin` e com um perfil sem `AGENDA_GERENCIAR`.

### Migration V55

- **`V55__agenda-marcadores-renomeia-conferencia-para-pre-acerto.sql`**: `RENAME COLUMN data_conferencia TO data_pre_acerto` e renomeia o índice `idx_agenda_marcadores_data_conferencia` para `idx_agenda_marcadores_data_pre_acerto`. Os dados são preservados; os `CHECK`s acompanham a coluna renomeada. A V54 não foi alterada.
