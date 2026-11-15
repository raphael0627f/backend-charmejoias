# Financeiro 6.1 — Fase 4: anexos e comprovantes, situação do cliente

Migration **V70** (tabela `conta_receber_anexo` e índice para a situação do cliente). Sem prefixo `/api`; erros com o campo **`menssage`**. Complementa `financeiro-contas-receber-integracao-frontend.md` e `financeiro-recebimentos-integracao-frontend.md`.
Espelha os anexos do Contas a Pagar (`financeiro-recorrencia-anexos-lote-integracao-frontend.md`).

## Permissões

| Ação | Permissão |
| --- | --- |
| Anexar documento ao título; remover documento | `CONTA_RECEBER_GERENCIAR` |
| Anexar comprovante ao recebimento; remover comprovante | `CONTA_RECEBER_RECEBER` |
| Baixar anexo; situação do cliente (individual e geral) | `CONTA_RECEBER_VISUALIZAR`, `GERENCIAR` ou `RECEBER` (não exige permissão de cliente) |

`CONTA_RECEBER_ESTORNAR` isolada não dá acesso. Sem permissão: 403 antes de qualquer validação.

## 1. Anexos

Tipos: `DOCUMENTO` (no título) e `COMPROVANTE` (no recebimento). **Não existe `BOLETO` no Contas a Receber.**

### Anexar documento — `POST /contas-receber/{id}/anexos` (201)
`multipart/form-data`: `arquivo` (arquivo) e `tipo` = `DOCUMENTO`. Qualquer outro tipo: 400 (comprovante: "O comprovante deve ser anexado diretamente ao recebimento.").

### Anexar comprovante — `POST /contas-receber/recebimentos/{recebimentoId}/anexos` (201)
`multipart/form-data`: `arquivo` (o tipo é sempre `COMPROVANTE`).

Resposta (ambos): `{ "id": 5, "tipo": "DOCUMENTO", "nome": "pedido.pdf", "tipoConteudo": "application/pdf", "tamanho": 12345, "recebimentoId": null, "createdAt": "...", "createdBy": { } }`.
`recebimentoId` só vem nos comprovantes.

### Regras e limites
- **5 MB** por arquivo (acima: **413**, mensagem "O arquivo excede o tamanho máximo permitido de 5 MB."); arquivo vazio: 400. O limite de upload do servidor (5 MB por arquivo) vale para os novos endpoints.
- Somente **PDF, JPG e PNG**, validados pela assinatura do conteúdo (extensão falsa: 400). O nome é sanitizado.
- No máximo **10 anexos por título**, somando documentos e comprovantes: o 11º é recusado com **409** ("O título já possui o número máximo de 10 anexos.").
- Título `RECEBIDO` aceita anexos. Título `CANCELADO` **não aceita novos** (400) nem remoção (400), mas o download continua permitido.
- Recebimento estornado: o comprovante já existente continua visível e baixável, mas **não aceita novo** comprovante (400).
- Eventos `ANEXO_ADICIONADO` e `ANEXO_REMOVIDO` no histórico do título.

### Download — `GET /contas-receber/anexos/{id}`
Autenticado (envie o token). Resposta binária com `Content-Type` do arquivo, `Content-Disposition: attachment; filename*=UTF-8''...`, `X-Content-Type-Options: nosniff` e `Cache-Control: private, no-store`.
No frontend: `fetch` com `Authorization`, converter em blob e usar `URL.createObjectURL` (para exibir imagem/PDF inline ou forçar download). 404 se o anexo não existir.

### Remoção — `DELETE /contas-receber/anexos/{id}` (204)
Mesma permissão de quem anexa (documento: `GERENCIAR`; comprovante: `RECEBER`); outra permissão: 403. O arquivo é apagado do disco após o commit.

### Detalhe e lista
- `GET /contas-receber/{id}`: o campo `anexos` passa a trazer a lista (formato do upload; documentos e comprovantes, estes com `recebimentoId`).
- `GET /contas-receber` e as parcelas: `quantidadeAnexos` com o total real.

### Armazenamento
Os arquivos ficam em `app.storage.financeiro-anexos-dir` na subpasta **`receber/{idDoTitulo}/{uuid}.{ext}`**, separada das pastas dos títulos a pagar (`{idDoTitulo}/...`), sem risco de colisão. O banco guarda só os metadados.

## 2. Situação do cliente

Definições (data de referência = hoje; considera só títulos `ABERTO` e `RECEBIDO_PARCIAL`, nunca os cancelados; "valor em aberto" = valor − valor recebido):
- **emAberto**: todos os abertos (inclui os vencidos). **vencido**: vencimento anterior a hoje.
- **vencemEm7Dias** / **vencemEm30Dias**: vencimento de hoje até hoje+7 / hoje+30 (inclusive; a faixa de 30 dias inclui a de 7).
- **diasMaiorAtraso**: dias entre o vencimento mais antigo vencido e hoje (0 se não há vencido).

### `GET /contas-receber/clientes/{clienteId}/situacao?recebimentoDe=&recebimentoAte=`
`recebimentoDe`/`recebimentoAte` (padrão: últimos 12 meses até hoje) delimitam `recebidoNoPeriodo`. Cliente inexistente: 404; cliente desativado também é consultável.
```json
{ "cliente": { "id": "uuid", "nome": "Maria", "documento": "12345678900", "cidade": "Campinas" },
  "emAberto": { "valor": 1500.00, "quantidade": 4 },
  "vencido": { "valor": 400.00, "quantidade": 1 },
  "vencemEm7Dias": { "valor": 300.00, "quantidade": 1 },
  "vencemEm30Dias": { "valor": 800.00, "quantidade": 2 },
  "diasMaiorAtraso": 12,
  "tituloMaisAntigoVencido": { "id": 10, "numero": 7, "vencimento": "2026-11-04", "valorAberto": 400.00 },
  "recebidoNoPeriodo": { "de": "2025-11-16", "ate": "2026-11-16", "valor": 2300.00, "quantidade": 5 },
  "ultimoRecebimento": { "data": "2026-11-10", "valor": 500.00 },
  "titulos": { "total": 9, "abertos": 4, "recebidos": 4, "cancelados": 1 } }
```
`tituloMaisAntigoVencido` e `ultimoRecebimento` vêm `null` quando não existem. `recebidoNoPeriodo` soma o **valor total** dos recebimentos **ativos** (não estornados) no período. `titulos.abertos` conta `ABERTO` e `RECEBIDO_PARCIAL`; `recebidos` conta `RECEBIDO`.
Cliente sem títulos: todos os valores `0`, quantidades `0`, `diasMaiorAtraso` 0 e os campos opcionais `null`.

**Lista de títulos do cliente:** use `GET /contas-receber?clienteId={id}` (combina com todos os demais filtros, paginação e totais).

### `GET /contas-receber/clientes/situacao` — visão geral (clientes com saldo em aberto)
Parâmetros: `busca` (nome ou CPF/documento, contém, sem diferenciar caixa), `somenteVencidos` (boolean; só clientes com valor vencido), `ordenar` (`emAberto` [padrão], `vencido`, `diasMaiorAtraso`, `nome`), `direcao` (`asc`|`desc`; padrão `desc`, e `asc` para `nome`), `page` (0), `size` (20, máximo 200).
```json
{ "content": [ { "cliente": { "id": "uuid", "nome": "Maria", "documento": "...", "cidade": "Campinas" },
                 "emAberto": 1500.00, "vencido": 400.00, "quantidadeTitulosAbertos": 4, "diasMaiorAtraso": 12,
                 "ultimoRecebimento": { "data": "2026-11-10", "valor": 500.00 } } ],
  "page": 0, "size": 20, "totalElements": 37, "totalPages": 2,
  "totais": { "totalEmAberto": 91000.00, "totalVencido": 22000.00, "clientesComAtraso": 9, "quantidadeClientes": 37 } }
```
Os `totais` valem para todo o filtro (não só para a página). Só aparecem clientes com valor em aberto > 0.

## Erros
| Situação | Status |
| --- | --- |
| Sem token | 401 |
| Sem permissão | 403 |
| Arquivo vazio, de tipo/extensão não permitido, tipo de anexo inválido, título cancelado, recebimento estornado, parâmetros inválidos | 400 |
| Título, recebimento, anexo ou cliente inexistente | 404 |
| 11º anexo do título | 409 |
| Arquivo acima de 5 MB | 413 |
