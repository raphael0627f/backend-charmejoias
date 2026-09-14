# Grupos — separação entre Cliente e Produto

Documento para o frontend implementar a divisão de Grupos em dois tipos: `CLIENTE` e `PRODUTO`.

---

## O que mudou

Grupo agora tem um campo obrigatório **`tipo`**, que só aceita dois valores:

- `CLIENTE` — grupos usados no cadastro de **clientes**
- `PRODUTO` — grupos usados no cadastro de **produtos** e **joias** (joia é tratada como uma categoria de produto aqui, então os dois usam o mesmo tipo de grupo)

Antes, existia uma única lista de grupos usada em tudo. Agora cada tela (cliente x produto/joia) deve mostrar só os grupos do seu tipo.

---

## `GET /grupos` — novo filtro

Novo query param opcional: **`tipo`** (`CLIENTE` ou `PRODUTO`).

- Na tela de cadastro/edição de **cliente**: chamar `GET /grupos?tipo=CLIENTE` (com paginação normal, `busca` continua funcionando junto).
- Na tela de cadastro/edição de **produto** ou **joia**: chamar `GET /grupos?tipo=PRODUTO`.
- Sem o `tipo`, o endpoint continua retornando todos os grupos misturados — então **é importante sempre mandar o `tipo` certo** na tela de seleção de grupo, senão vai aparecer grupo de cliente na tela de produto e vice-versa.

Exemplo: `GET /grupos?tipo=CLIENTE&busca=revenda&page=0&size=20`

---

## `POST /grupos` e `PUT /grupos` — campo `tipo` obrigatório

O corpo da requisição de criação/edição de grupo agora precisa do campo `tipo`:

```json
{
  "nome": "Revendedoras",
  "tipo": "CLIENTE",
  "ativo": true
}
```

Se não mandar `tipo`, a API retorna **400** (validação).

**Importante**: se o grupo já estiver em uso (vinculado a algum cliente, produto ou joia), a API **não deixa trocar o `tipo`** dele — retorna **409 CONFLICT** com a mensagem `"Grupo está em uso e não pode mudar de tipo"`. Então, na tela de edição de grupo, se o usuário tentar mudar o tipo de um grupo já vinculado, mostrar esse erro do backend (ou, se preferir, desabilitar o campo `tipo` no formulário de edição quando o grupo já estiver em uso — mas isso é só uma melhoria de UX opcional, o backend já garante a regra).

---

## Vínculo de grupos em Cliente, Produto e Joia — validação de tipo

Ao criar/editar um **Cliente** (`POST`/`PUT /clientes`, campo `gruposIds`), a API só aceita ids de grupos do tipo `CLIENTE`. Se mandar um grupo do tipo `PRODUTO`, retorna **400** com a mensagem `"Um ou mais grupos informados não são do tipo CLIENTE"`.

Ao criar/editar um **Produto** ou **Joia** (campo `gruposIds` dentro das relações), a API só aceita ids de grupos do tipo `PRODUTO`. Mesma coisa ao contrário: manda grupo `CLIENTE` lá, recebe 400 com `"...não são do tipo PRODUTO"`.

**Na prática**: como a tela de seleção de grupos já vai estar usando o `GET /grupos?tipo=...` certo pra cada cadastro (ver seção acima), esse erro só deve aparecer se algo estiver errado no fluxo — mas é bom tratar esse 400 genericamente mesmo assim.

---

## Resposta — nada muda na forma, só ganha o campo `tipo`

Em `GET /clientes/{id}`, `GET /produtos/{id}` e `GET /joias/{id}`, o array `grupos` continua vindo do mesmo jeito (lista de objetos `{id, nome, ativo}`), só que agora cada grupo também traz o campo `tipo`:

```json
"grupos": [
  { "id": 1, "nome": "Semijoias", "ativo": true, "tipo": "CLIENTE" }
]
```

Não precisa fazer nada com esse campo na exibição, é só informativo.

---

## Dados existentes

Os grupos que já existiam no banco foram migrados automaticamente: quem já estava vinculado a cliente virou `CLIENTE`, quem estava vinculado a produto/joia virou `PRODUTO`. Grupo que não tinha nenhum vínculo virou `PRODUTO` por padrão — se algum desses precisar ser `CLIENTE`, é só editar manualmente pela tela (contanto que ainda não esteja em uso, por causa da regra do 409 explicada acima).
