# Módulo SPC — guia de integração para o frontend

Documento para o frontend implementar as telas de **consulta de crédito**, **inclusão** e **exclusão** de devedor no SPC Brasil. Todos os endpoints já estão implementados e testados contra o ambiente real de homologação do SPC.

---

## Visão geral

O módulo tem três frentes, todas vinculadas a um **Cliente já cadastrado** no sistema (não existe consulta/inclusão/exclusão avulsa, sem cliente):

1. **Consulta** — verifica se o cliente tem restrição de crédito (protesto, pendência SPC, ações judiciais, etc) antes de conceder consignação/crediário.
2. **Inclusão** — registra o cliente como devedor no SPC.
3. **Exclusão** — remove o registro de devedor do SPC (ex: depois que ele pagou).

Não tem nada de autenticação especial pro frontend fazer aqui — os endpoints são chamados normalmente como qualquer outro endpoint do sistema. Quem se autentica com o SPC é o backend.

Todos os endpoints ficam sob o prefixo **`/spc`**.

---

## Tipos/enums usados nos requests

### `tipoPessoa` — Física ou Jurídica

Só dois valores, sempre em maiúsculo:

- `"F"` — Pessoa Física
- `"J"` — Pessoa Jurídica

**Importante**: isso **não vem de nenhum campo do Cliente** — o Cliente não tem "tipo de pessoa" cadastrado. Quem escolhe é o usuário, na hora de consultar/incluir/excluir (ex: um seletor F/J na tela). O backend usa o CPF do Cliente se for `F`, e o `documento` (campo onde entra CNPJ) se for `J`.

### `codigoTipoDevedor` — Tipo de devedor (usado em inclusão/exclusão)

5 valores fixos:

| Valor | Significado |
|---|---|
| `C` | Comprador |
| `F` | Fiador |
| `A` | Avalista |
| `E` | Endossante |
| `T` | Contribuinte |

Não tem endpoint pra listar isso (são só 5 valores fixos, dá pra hardcodar no frontend um select com essas opções).

### `tipoOperacao` — usado só no filtro do histórico

- `"INCLUSAO"`
- `"EXCLUSAO"`

---

## Tratamento de erros (padrão em todos os endpoints do módulo)

Erros vêm no formato padrão do sistema:

```json
{
  "timestamp": "2026-09-12T10:00:00.000Z",
  "status": "400 BAD_REQUEST",
  "menssage": "mensagem explicando o erro",
  "path": "uri=/spc/consultas"
}
```

Status possíveis:
- **400** — dado inválido no request (ex: insumo opcional que não pertence ao produto escolhido, produto que não aceita o `tipoPessoa` informado).
- **404** — algo referenciado não existe (cliente, produto do catálogo, natureza de inclusão, motivo de exclusão, consulta/registro por id).
- **502** — erro vindo do próprio SPC (ex: autenticação, CPF inválido, contrato já existe, etc). A mensagem já vem com o texto que o SPC devolveu, então dá pra mostrar direto pro usuário.

---

## 1. Consulta de crédito

### 1.1 — Descobrir os produtos disponíveis

`GET /spc/produtos?tipoPessoa=F` (ou `J`)

Retorna os produtos de consulta contratados que aceitam aquele tipo de pessoa (alguns produtos servem pros dois tipos):

```json
[
  { "codigo": 325, "nome": "SPC MAXI" },
  { "codigo": 323, "nome": "SPC MIX MAIS" },
  { "codigo": 11, "nome": "CONFIRME PF" }
]
```

Use isso pra montar o seletor de produto na tela de consulta.

### 1.2 — Descobrir os insumos opcionais daquele produto

`GET /spc/produtos/{codigo}/insumos`

Cada produto tem um conjunto de "insumos" que já vêm de graça na resposta (não aparecem aqui, são automáticos) e outros que só aparecem **se forem explicitamente pedidos** — geralmente coisas como score de crédito e alerta de óbito. Exemplo pro produto 325:

```json
[
  { "codigo": 18, "nome": "acao" },
  { "codigo": 77, "nome": "spc-score-3-meses" },
  { "codigo": 78, "nome": "spc-score-12-meses" },
  { "codigo": 3082, "nome": "spc-obito" }
]
```

Use isso pra montar checkboxes opcionais na tela (ex: "incluir score de crédito nessa consulta?"). O usuário pode não marcar nenhum — nesse caso só vem o que já é automático do produto escolhido.

### 1.3 — Fazer a consulta

`POST /spc/consultas`

**Request:**
```json
{
  "clienteId": "b982711a-15f3-4400-be7f-136721e096bf",
  "tipoPessoa": "F",
  "codigoProduto": 325,
  "insumosOpcionais": [77]
}
```
- `insumosOpcionais` é opcional — pode mandar `[]` ou omitir se não quiser pedir nenhum.

**Response** (formato completo — ver seção 1.5 pra entender a estrutura de `insumos`):
```json
{
  "id": "ad0467c8-0697-4685-bb5a-b10bac14a9e3",
  "protocoloNumero": 15260866066,
  "protocoloDigito": 8,
  "restricao": true,
  "consumidor": {
    "nome": "FULANO DE TAL",
    "idade": 45,
    "sexo": "MASCULINO",
    "cpf.numero": "12327840104",
    "endereco.logradouro": "AVENIDA PAULISTA",
    "endereco.numero": "1000",
    "endereco.bairro": "BELA VISTA",
    "endereco.cidade.nome": "SAO PAULO",
    "endereco.cidade.estado.siglaUf": "SP"
  },
  "avisos": [],
  "insumos": [
    {
      "nome": "protesto",
      "resumo": {
        "quantidadeTotal": 6,
        "valorTotal": 11674.74,
        "valorUltimaOcorrencia": null,
        "dataPrimeiraOcorrencia": "2020-08-20T00:00:00-03:00",
        "dataUltimaOcorrencia": null,
        "mesanoPrimeiraOcorrencia": null,
        "mesanoUltimaOcorrencia": null
      },
      "detalhes": [
        {
          "dataProtesto": "2022-11-10T00:00:00-03:00",
          "valor": 1500.00,
          "cartorio.nome": "UN",
          "cartorio.cidade.nome": "LEME",
          "cartorio.cidade.estado.siglaUf": "SP"
        }
      ]
    },
    {
      "nome": "spc-score-3-meses",
      "resumo": null,
      "detalhes": [
        { "classe": "D", "score": 429, "probabilidade": 9.83, "tipoClienteScore": "RESTRITO" }
      ]
    }
  ],
  "createdAt": "2026-09-12T18:43:17.700498"
}
```

**Campo `restricao`** (booleano, no topo): é o próprio SPC quem calcula isso — `true` significa que o consumidor tem alguma restrição. É o jeito mais rápido de decidir se mostra um alerta grande na tela ("cliente com restrição") sem precisar vasculhar a lista de `insumos`.

### 1.4 — Consultar uma consulta já feita

`GET /spc/consultas/{id}` — devolve **exatamente o mesmo formato** do `POST` acima (o backend reprocessa o XML salvo, então é sempre consistente com o que veio na hora da consulta original).

### 1.5 — Entendendo a estrutura genérica de `insumos`

O SPC tem mais de 60 tipos de informação possíveis numa resposta (protesto, ações judiciais, score, alerta de óbito, sócios, telefones, etc). Em vez de modelar cada um com campos fixos, o backend devolve uma **lista genérica**, onde cada item é:

```ts
{
  nome: string;              // nome do insumo, ex: "protesto", "spc", "spc-score-3-meses"
  resumo: {                  // pode ser null se o insumo não tiver resumo agregado
    quantidadeTotal: number | null;
    valorTotal: number | null;
    valorUltimaOcorrencia: number | null;
    dataPrimeiraOcorrencia: string | null;  // ISO 8601
    dataUltimaOcorrencia: string | null;
    mesanoPrimeiraOcorrencia: string | null;
    mesanoUltimaOcorrencia: string | null;
  } | null;
  detalhes: Array<Record<string, any>>;  // uma ocorrência por item da lista, campos variam por tipo de insumo
}
```

**Isso significa que o frontend não deve esperar uma lista fixa de insumos** — o que aparece depende do produto escolhido e dos insumos opcionais pedidos. A abordagem recomendada pra tela:

1. Ter uma lista de insumos "conhecidos" com um label bonito e talvez um ícone (tabela abaixo), pra exibir de forma amigável.
2. Pra qualquer insumo que não esteja nessa lista conhecida, ter um fallback genérico: mostrar o `nome` como título e cada chave de `detalhes[i]` como um par rótulo:valor cru (ou simplesmente direcionar pro relatório em PDF, que já imprime tudo formatado — ver seção 1.7).

Insumos mais comuns que provavelmente vão aparecer, dependendo do produto:

| `nome` | O que é |
|---|---|
| `spc` | Pendência no cadastro do SPC (o "nome" do módulo) |
| `protesto` | Título protestado em cartório |
| `acao` | Ação judicial (cobrança, execução) |
| `informacao-poder-judiciario` | Processo judicial |
| `pendencia-financeira` | Pendência financeira genérica |
| `alerta-documento` | Alerta de documento roubado/fraudado |
| `spc-obito` | Alerta de óbito (prevenção de fraude) |
| `participacao-falencia` | Participação em falência/recuperação judicial |
| `spc-score-3-meses` / `spc-score-12-meses` | Score de crédito (só vem se pedido como insumo opcional) |
| `consulta-realizada` / `ultimas-consultas` | Quem mais consultou esse CPF/CNPJ recentemente |

Cada um desses tem campos de `detalhes` diferentes (ex: `protesto` tem `dataProtesto`, `valor`, `cartorio.nome`; `spc-score-3-meses` tem `classe`, `score`, `probabilidade`). Se precisar do formato exato de algum insumo específico que ainda não apareceu num teste, me avisa que eu confirmo contra o ambiente real.

**Campo `consumidor`**: mesma lógica genérica — é um mapa achatado com os dados da pessoa física ou jurídica que o SPC devolveu (nome, cpf/cnpj, endereço, telefone, etc, com chaves tipo `endereco.cidade.nome` pra campos aninhados).

**Campo `avisos`**: lista de strings com avisos de confiabilidade (ex: "alguma base de dados estava fora do ar na hora da consulta"). Pode vir vazia.

### 1.6 — Histórico de consultas (lista enxuta)

`GET /spc/consultas?clienteId={id}&page=0&size=20`

Formato **enxuto**, só pra listagem/tabela — não traz `insumos` nem `consumidor` (pra isso, usar o `GET /spc/consultas/{id}` de cada linha):

```json
{
  "content": [
    {
      "id": "ad0467c8-0697-4685-bb5a-b10bac14a9e3",
      "clienteId": "b982711a-15f3-4400-be7f-136721e096bf",
      "clienteNome": "CLIENTE TESTE",
      "codigoProduto": 325,
      "nomeProduto": "SPC MAXI",
      "protocoloNumero": 15260866066,
      "possuiRestricao": true,
      "createdAt": "2026-09-12T18:43:17.700498"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```
`clienteId` é opcional — sem ele, lista todas as consultas de todos os clientes (paginado).

### 1.7 — Relatório em PDF

`GET /spc/consultas/{id}/relatorio`

Devolve o PDF com **todos** os dados da consulta (protocolo, cliente, avisos, e cada insumo com resumo + detalhes completos — inclusive os que não têm um formato "bonito" mapeado no frontend). O header já vem como `Content-Disposition: inline`, então **pra mostrar embutido na tela** (em vez de abrir em nova guia ou forçar download), é só apontar a URL num `<iframe>` ou `<embed>`:

```html
<iframe src="/spc/consultas/ad0467c8-.../relatorio" width="100%" height="800"></iframe>
```

---

## 2. Inclusão de devedor

### 2.1 — Buscar naturezas de inclusão (pra popular o select)

`GET /spc/naturezas-inclusao` → lista completa (92 itens):
```json
[
  { "id": 52, "nome": "COMERCIO VAREJISTA" },
  { "id": 101, "nome": "CREDIÁRIO" }
]
```

### 2.2 — Incluir

`POST /spc/inclusoes`

**Request:**
```json
{
  "clienteId": "b982711a-15f3-4400-be7f-136721e096bf",
  "tipoPessoa": "F",
  "numeroContrato": "1234",
  "dataCompra": "2026-08-01",
  "dataVencimento": "2026-09-01",
  "codigoTipoDevedor": "C",
  "valorDebito": 150.00,
  "naturezaInclusaoId": 101
}
```
Todos os campos são obrigatórios.

**Response:**
```json
{
  "id": "f1a2b3c4-...",
  "clienteId": "b982711a-15f3-4400-be7f-136721e096bf",
  "clienteNome": "CLIENTE TESTE",
  "tipoOperacao": "INCLUSAO",
  "tipoPessoa": "F",
  "numeroContrato": "1234",
  "dataCompra": "2026-08-01",
  "dataVencimento": "2026-09-01",
  "codigoTipoDevedor": "C",
  "valorDebito": 150.00,
  "naturezaInclusaoId": 101,
  "naturezaInclusaoNome": "CREDIÁRIO",
  "motivoExclusaoId": null,
  "motivoExclusaoDescricao": null,
  "mensagemRetorno": "Dados inseridos com sucesso!",
  "createdAt": "2026-09-12T10:00:00"
}
```
`mensagemRetorno` é o texto literal que o SPC devolveu — dá pra mostrar como confirmação pro usuário.

---

## 3. Exclusão de devedor

### 3.1 — Buscar motivos de exclusão (pra popular o select)

`GET /spc/motivos-exclusao` → lista completa (25 itens):
```json
[
  { "id": 1, "descricao": "Pagamento da divida" },
  { "id": 4, "descricao": "Ordem Judicial" }
]
```

### 3.2 — Excluir

`POST /spc/exclusoes`

**Request:**
```json
{
  "clienteId": "b982711a-15f3-4400-be7f-136721e096bf",
  "tipoPessoa": "F",
  "numeroContrato": "1234",
  "dataVencimento": "2026-09-01",
  "codigoTipoDevedor": "C",
  "motivoExclusaoId": 1
}
```

**Response**: mesmo formato do `SpcRegistroResponseDTO` da inclusão, mas com `tipoOperacao: "EXCLUSAO"`, `naturezaInclusaoId`/`naturezaInclusaoNome` nulos, e `motivoExclusaoId`/`motivoExclusaoDescricao` preenchidos. `mensagemRetorno` vem `"Registro removido com sucesso!"`.

**Importante**: a exclusão **não exige** que o cliente tenha sido incluído por esse sistema antes — pode ser usada pra excluir um registro feito manualmente fora do app também. Não tem validação de "só pode excluir o que foi incluído aqui".

---

## 4. Histórico de inclusões/exclusões

`GET /spc/registros?clienteId={id}&tipoOperacao=INCLUSAO&page=0&size=20`

Ambos os filtros (`clienteId`, `tipoOperacao`) são opcionais e combináveis. Retorna uma página de `SpcRegistroResponseDTO` (mesmo formato do response de incluir/excluir), ordenado por data de criação.

---

## Fora do escopo (não implementado)

- **Cheque** (CCF, cheque sem fundo, contra-ordem) — a loja não trabalha com cheque, então esses insumos nunca aparecem, mesmo que existam no catálogo do SPC.
- Nenhuma decisão automática de negócio é tomada a partir da consulta (ex: bloquear consignação automaticamente se `restricao: true`) — quem decide é a tela, com base no que a API devolve.
- SPC Busca (localização de veículo/pessoa) — produto diferente, não contratado.
