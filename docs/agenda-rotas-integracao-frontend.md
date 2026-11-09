# Agenda: rotas, catálogo de cidades e marcadores com rota — guia de integração frontend

Contrato real (testado por curl). Complementa `docs/agenda-integracao-frontend.md` e `docs/planejador-rotas-renomeacao-integracao-frontend.md`.

- Sem prefixo `/api`. Erros de regra: `{"timestamp","status","menssage","path"}` (campo `menssage`, com dois s).
- Falha de `@Valid` em campo ausente (ex.: rota sem `nome`) volta como ProblemDetail do Spring (`{"detail":"Invalid request content.",...}`), **sem** `menssage`: validar o nome no formulário.
- Permissões: leitura de `/cidades` e `GET /agenda/rotas` exige `AGENDA_VISUALIZAR` **ou** `AGENDA_GERENCIAR` (403 sem nenhuma). Escrita em `/agenda/rotas` exige `AGENDA_GERENCIAR`. `/agenda/marcadores` segue como antes (GET só login; escrita `AGENDA_GERENCIAR`).

## 1. Catálogo de cidades (somente leitura)

`GET /cidades?busca=&uf=&tipo=&page=&size=` → página Spring (`content`, `totalElements`, `number`, `size`...). Default `size=20`, ordenado por nome (sem acento) e UF.

- `busca`: sem acento e sem diferenciar maiúscula; cada palavra precisa aparecer no nome (ou no município pai, no caso de distrito). `busca=sao paulo` acha "São Paulo" e também distritos de São Paulo.
- **Ordem com `busca`** (por relevância; o `sort` recebido é ignorado): (1) nome igual à busca, (2) nome que começa com a busca, (3) demais; em cada grupo, MUNICIPIO antes de DISTRITO, depois nome e UF. Ex.: `busca=sao paulo` traz "São Paulo - SP" primeiro. Sem `busca`, ordena por nome/UF.
- `uf`: sigla (`SP`). `tipo`: `MUNICIPIO` | `DISTRITO`.
- `GET /cidades/{id}` → 404 `"Cidade não encontrada. id: N"`.

```json
{ "id": 1892, "nome": "Campinas", "uf": "SP", "ibge": "3509502", "tipo": "MUNICIPIO", "municipioNome": null, "descricao": "Campinas - SP" }
{ "id": 111, "nome": "Água Rasa", "uf": "SP", "ibge": "355030801", "tipo": "DISTRITO", "municipioNome": "São Paulo", "descricao": "Água Rasa (distrito de São Paulo) - SP" }
```

Catálogo IBGE: 5571 municípios + 5181 distritos. O distrito "sede" (mesmo nome do município) não entra, porque já existe como MUNICIPIO.

## 2. Rotas da agenda — `/agenda/rotas`

| Método | Rota | Observação |
|---|---|---|
| GET | `/agenda/rotas?nome=&page=&size=&sort=` | paginado; `nome` = contém, sem diferenciar maiúscula; default `size=20`, `sort=nome` |
| GET | `/agenda/rotas/{id}` | 404 `"Rota da agenda não encontrada. id: N"` |
| POST | `/agenda/rotas` | 201 + `Location` |
| PUT | `/agenda/rotas` | **id no corpo** (obrigatório) |
| DELETE | `/agenda/rotas/{id}` | 204 |

Request: `{ "id": 1, "nome": "Rota Sul", "cidadeIds": [4970, 1892] }` (`id` só no PUT). A ordem de `cidadeIds` é preservada na resposta.

Response:
```json
{ "id": 1, "nome": "Rota Sul", "cidades": [ { "...CidadeResponseDTO" } ], "createdAt": "...", "updatedAt": "..." }
```

Erros:
| Caso | Status | menssage |
|---|---|---|
| nome já existe (sem diferenciar caixa) | 409 | `Já existe uma rota com o nome "X"` |
| `cidadeIds` vazio/ausente | 400 | `A rota precisa ter ao menos uma cidade` |
| cidade repetida | 400 | `Cidade repetida na lista. id: N` |
| cidade inexistente | 404 | `Cidade não encontrada. id: N` |
| excluir rota usada em marcador | 409 | `Não é possível excluir a rota: ela está em uso em N marcador(es) da agenda` |

## 3. Marcadores — `/agenda/marcadores` (mudanças)

Request:
```json
{ "id": 10, "data": "2031-03-10", "tipo": "TRABALHA", "nome": null,
  "rotaId": 1, "cidadeExtraIds": [8704],
  "dataPreAcerto": "2031-03-12", "dataAcerto": "2031-03-14" }
```
- `TRABALHA`: `rotaId` obrigatório; `cidadeExtraIds` opcional (sem repetidas, sem cidade que já está na rota); `nome` é ignorado; pré-acerto/acerto obrigatórios (regras de data de sempre).
- `NAO_TRABALHA` (folga): `nome` obrigatório; `rotaId` e `cidadeExtraIds` **proibidos**; datas de pré-acerto/acerto proibidas.
- Continua 1 marcador por dia (409).

Response (campos novos `rota` e `cidadesExtras`):
```json
{ "id": 10, "data": "2031-03-10", "nome": "Rota Sul", "tipo": "TRABALHA",
  "rota": { "id": 1, "nome": "Rota Sul", "cidades": [ { "...CidadeResponseDTO" } ] },
  "cidadesExtras": [ { "...CidadeResponseDTO" } ],
  "dataPreAcerto": "2031-03-12", "dataAcerto": "2031-03-14", "createdAt": "...", "updatedAt": "...", "createdBy": {}, "updatedBy": {} }
```
Na folga: `rota: null`, `cidadesExtras: []`. Em `TRABALHA`, `nome` é o nome atual da rota (derivado): renomear/alterar cidades da rota reflete nos marcadores. O `GET` por período já traz rota, cidades e extras (sem N+1).

Erros novos (400, exceto onde indicado):
- `Marcador do tipo TRABALHA exige uma rota (rotaId)`
- `Rota da agenda não encontrada. id: N` (404)
- `Cidade não encontrada. id: N` (404) / `Cidade repetida na lista. id: N`
- `A cidade Campinas - SP já pertence à rota; não pode ser cidade extra`
- `Nome do marcador é obrigatório para o tipo NAO_TRABALHA`
- `Marcador do tipo NAO_TRABALHA não pode ter rota` / `... não pode ter cidades extras`

## 4. Migrations (irreversíveis)

- **V56** renomeia tabelas do antigo módulo Rotas (dados preservados) e a permissão `ROTA_GERENCIAR` → `PLANEJADOR_ROTA_GERENCIAR`.
- **V57** cria e popula `cidades`.
- **V58** **apaga todos os marcadores existentes** (`DELETE FROM agenda_marcadores`), cria `agenda_rotas`, `agenda_rota_cidades`, `agenda_marcador_cidades_extras` e a coluna `rota_id`.
