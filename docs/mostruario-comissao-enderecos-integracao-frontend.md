# Mostruário, Comissão e Endereços múltiplos — guia de integração frontend

Este documento cobre três mudanças de backend que o frontend precisa acompanhar:

1. **Mostruário** — CRUD novo.
2. **Comissão** — CRUD novo.
3. **Consignação** — passou a exigir um Mostruário.
4. **Cliente** — endereço único virou **lista de endereços**, com um marcado como principal. **Isso é uma breaking change** no contrato de `GET/POST/PUT /clientes`.

---

## 1. Mostruário

CRUD simples, sem campo `ativo` — a exclusão é definitiva (`DELETE` de verdade, não desativação).

### Campos

| Campo | Tipo | Observação |
|---|---|---|
| `id` | number | gerado pelo backend, autoincremento |
| `descricao` | string | obrigatório |
| `createdAt` / `updatedAt` | datetime | gerados pelo backend, não enviar no request |

### Endpoints

- `GET /mostruarios?busca=&page=&size=&sort=` — lista paginada. `busca` filtra por `descricao` (like, case-insensitive).
- `GET /mostruarios/{id}`
- `POST /mostruarios` — body: `{ "descricao": "string" }`
- `PUT /mostruarios` — body: `{ "id": number, "descricao": "string" }`
- `DELETE /mostruarios/{id}` — `204` se conseguir excluir. **`409` se o mostruário estiver em uso por alguma Consignação** (não pode ser excluído nesse caso).

### Exemplo de resposta

```json
{
  "id": 1,
  "descricao": "Mostruário Prata 001",
  "createdAt": "2026-09-13T00:28:21.888",
  "updatedAt": "2026-09-13T00:28:21.888"
}
```

---

## 2. Comissão

CRUD simples, também sem `ativo` e sem `busca` na listagem (não tem um campo de texto natural pra filtrar).

### Campos

| Campo | Tipo | Observação |
|---|---|---|
| `id` | number | gerado pelo backend |
| `percentual` | number (decimal) | obrigatório, entre 0 e 100 |
| `valorMinimo` | number (decimal) | obrigatório, ≥ 0 — início do range de valor (ex: `500.00`) |
| `valorMaximo` | number (decimal) | obrigatório — fim do range de valor (ex: `1000.00`). **Precisa ser maior que `valorMinimo`**, senão o backend responde `400` com a mensagem `"Valor máximo do range deve ser maior que o valor mínimo"` |
| `createdAt` / `updatedAt` | datetime | gerados pelo backend |

### Endpoints

- `GET /comissoes?page=&size=&sort=`
- `GET /comissoes/{id}`
- `POST /comissoes` — body: `{ "percentual": number, "valorMinimo": number, "valorMaximo": number }`
- `PUT /comissoes` — body: idem + `"id"`
- `DELETE /comissoes/{id}` — `204`. Sem nenhuma trava de uso hoje (não está vinculada a mais nada no sistema ainda).

### Exemplo de resposta

```json
{
  "id": 1,
  "percentual": 5.50,
  "valorMinimo": 500.00,
  "valorMaximo": 1000.00,
  "createdAt": "2026-09-13T00:28:15.575",
  "updatedAt": "2026-09-13T00:28:15.575"
}
```

---

## 3. Consignação agora exige um Mostruário

**Breaking change**: `POST /consignacoes` e `PUT /consignacoes` agora exigem o campo `mostruarioId`.

### `ConsignacaoRequestDTO` — campo novo

```json
{
  "clienteId": "uuid-ou-null",
  "mostruarioId": 1,
  "descricao": "string",
  "tipo": "PRODUTO",
  "produtos": [ /* ... como já era ... */ ],
  "dataEntrega": "2026-09-20",
  "dataPreAcertoConsignacao": null,
  "dataAcertoConsignacao": null
}
```

- `mostruarioId` é **obrigatório** — se vier nulo/ausente, `400` (`"Mostruário é obrigatório"`).
- Se o id não existir no cadastro de mostruários, `404` (`"Mostruário não encontrado. id: X"`).
- Um mesmo Mostruário pode estar em várias Consignações (não é exclusivo).

### `ConsignacaoResponseDTO` e `ConsignacaoResumoDTO` — campo novo

Tanto o `GET /consignacoes/{id}` (completo) quanto o `GET /consignacoes` (listagem enxuta) agora trazem o objeto `mostruario` inteiro, logo depois de `cliente`:

```json
{
  "id": 8711,
  "descricao": null,
  "tipo": "PRODUTO",
  "cliente": null,
  "mostruario": {
    "id": 1,
    "descricao": "Mostruário Prata 001",
    "createdAt": "2026-09-13T00:28:21.888",
    "updatedAt": "2026-09-13T00:28:21.888"
  },
  "produtos": [ "..." ],
  "dataEntrega": null,
  "confirmada": false,
  "createdAt": "2026-09-13T00:31:26.493",
  "updatedAt": "2026-09-13T00:31:26.493"
}
```

**Formulário de Consignação precisa ganhar um select de Mostruário** (`GET /mostruarios?size=999` ou paginado, como já é feito hoje pra outros selects de catálogo) — sem isso, toda criação/edição de Consignação passa a falhar com `400`.

**Consignações antigas** (criadas antes dessa mudança) ficam com `mostruario: null` até serem editadas — o campo no banco é opcional só pra não quebrar o histórico, mas o formulário deve tratar como obrigatório dali pra frente.

---

## 4. Cliente: múltiplos endereços com um principal

**Breaking change** no shape de `Cliente`. Antes:

```json
{ "...": "...", "endereco": { "id": 3455, "logradouro": "...", "...": "..." } }
```

Agora:

```json
{
  "...": "...",
  "enderecos": [
    {
      "id": 3455,
      "logradouro": "RUA JOAS ANDREOLI",
      "numero": "598",
      "complemento": null,
      "bairro": "VILA NAIR",
      "municipio": "CAMPOS DO JORDÃO",
      "uf": "SP",
      "cep": "12440000",
      "ibge": "3509700",
      "principal": true
    },
    {
      "id": 7189,
      "logradouro": "RUA NOVA",
      "numero": "10",
      "bairro": "CENTRO",
      "municipio": "SAO PAULO",
      "uf": "SP",
      "cep": "01000000",
      "principal": false
    }
  ]
}
```

Isso vale tanto pra `ClienteResponseDTO` (o que vem em `GET /clientes` e `GET /clientes/{id}`) quanto pra `ClienteRequestDTO` (o que se manda em `POST`/`PUT /clientes`).

### Campo novo: `principal`

Cada endereço tem um booleano `principal`. Regras aplicadas no backend (`400` se violar):

- O cliente precisa ter **ao menos um** endereço na lista (`"Cliente precisa ter ao menos um endereço"`).
- **Exatamente um** dos endereços da lista precisa ter `principal: true` — nem zero, nem dois ou mais (`"Cliente precisa ter exatamente um endereço marcado como principal"`).

O endereço principal é o que o backend usa em tudo que hoje já usava "o endereço do cliente": emissão de NFe, escopo de CFOP, consulta SPC, relatório de Consignação. **O frontend não precisa fazer nada especial pra isso** além de garantir que sempre haja um marcado como principal — o backend resolve sozinho qual usar internamente.

### Como funciona o `POST`/`PUT` da lista

O comportamento é o mesmo já usado hoje pra variações de produto (sincronização por id):

- Endereço da lista **com `id`** que já existe no cliente → é **atualizado** com os dados enviados.
- Endereço da lista **sem `id`** (ou com `id` que não existe no cliente) → é **criado** como novo.
- Endereço que o cliente já tinha e **não veio** na lista enviada → é **excluído**.

Ou seja: pra editar um endereço existente, sempre mandar o `id` dele de volta. Pra adicionar um novo, mandar sem `id` (ou `id: null`). Pra remover, simplesmente não incluir mais na lista do `PUT`.

### Preenchimento automático do IBGE

Continua igual a antes (por CEP, via BrasilAPI) — só que agora roda pra **cada** endereço da lista que não tiver `ibge` preenchido, não só pra um.

### Migração dos dados existentes

Todo cliente que já tinha um endereço cadastrado teve esse endereço migrado automaticamente para `principal: true` — nenhum cliente ficou sem endereço ou com o principal errado depois da migração. Empresa **não foi afetada** — continua com endereço único, sem lista.

### Atenção — validações de campo por item da lista

O backend valida no nível da lista (pelo menos um item, exatamente um principal), mas **não valida campo a campo dentro de cada endereço** (ex: não bloqueia logradouro/bairro/CEP vazios em cada item) — isso já era assim antes da mudança (era assim mesmo quando existia um único `endereco`) e continua sendo responsabilidade do formulário do frontend validar antes de enviar.
