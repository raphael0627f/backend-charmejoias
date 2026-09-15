# Acerto de Consignação — referência opcional ao Pré-acerto

## O que mudou

O `AcertoConsignacao` agora pode, opcionalmente, referenciar o `PreAcertoConsignacao` que o originou. Não é obrigatório — se o usuário não escolher um pré-acerto, o campo simplesmente fica `null`, sem nenhuma restrição.

## Campo novo: `preAcertoId`

### `POST /acertos-consignacao` e `PUT /acertos-consignacao`

Corpo aceita um novo campo opcional:

```json
{
  "id": null,
  "consignacaoId": 10,
  "preAcertoId": 5,
  "desconto": 0,
  "observacoes": "...",
  "produtos": [...]
}
```

- `preAcertoId`: **opcional**. Pode ser omitido ou enviado como `null` — nesse caso o Acerto é criado/atualizado sem vínculo com nenhum Pré-acerto.
- Se `preAcertoId` for informado mas não existir um Pré-acerto com esse id, a API retorna **404** (mesmo formato de erro já usado em `ObjetoNaoEncontradoException`):
  ```json
  { "status": "404 NOT_FOUND", "menssage": "Pré-acerto de Consignação não encontrado. id: 5" }
  ```
- Não há nenhuma validação cruzada entre o `preAcertoId` e o `consignacaoId` informados (ex: não é obrigatório que o pré-acerto pertença à mesma consignação) — é só uma referência informativa.

### Respostas (`GET /acertos-consignacao`, `GET /acertos-consignacao/{id}`, `POST`, `PUT`)

Todas as respostas de Acerto de Consignação (listagem resumida e detalhe) agora incluem `preAcertoId`:

```json
{
  "id": 12,
  "consignacaoId": 10,
  "preAcertoId": 5,
  "cliente": { "codigo": "10815RF", "nome": "..." },
  "desconto": 0,
  "observacoes": "...",
  "confirmado": false,
  "quantidadeProdutosAcertados": 20,
  "quantidadeProdutosDevolvidos": 3,
  "valorTotalProdutosAcertados": 1200.00,
  "valorTotalComDesconto": 1200.00,
  "quantidadePorCategoria": [...],
  "createdAt": "...",
  "updatedAt": "..."
}
```

`preAcertoId` vem como `null` quando o Acerto não tem nenhum Pré-acerto vinculado.

## O que o frontend precisa fazer

1. Na tela de criação/edição de Acerto de Consignação, adicionar um campo (select/autocomplete) opcional para escolher um Pré-acerto — pode ficar vazio.
2. Enviar `preAcertoId` no corpo do `POST`/`PUT` só quando o usuário selecionar algum; caso contrário, omitir o campo ou enviar `null`.
3. Exibir o `preAcertoId` (ou os dados do pré-acerto, se a tela buscar por id) na listagem/detalhe do Acerto, quando não for `null`.
4. Nenhuma mudança é necessária nos fluxos que não usam esse campo — ele é 100% opcional e não quebra nada existente.
