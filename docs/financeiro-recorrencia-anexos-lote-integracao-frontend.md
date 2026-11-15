# Financeiro 6.0 — Fase 4: recorrência, anexos e lançamento em lote

Contrato para o Frontend. Sem prefixo `/api`. Erros no padrão do projeto (campo **`menssage`**), mensagens formais.
Datas `yyyy-MM-dd`; valores com 2 casas. **Migration V66.** Complementa os documentos das fases 1 a 3.

## Permissões

| Ação | Permissão |
|---|---|
| Criar recorrência, reajustar, encerrar, lançamento em lote, anexar documento/boleto | `CONTA_PAGAR_GERENCIAR` |
| Anexar comprovante em pagamento | `CONTA_PAGAR_PAGAR` |
| Consultar regra, fazer o download do anexo | `CONTA_PAGAR_VISUALIZAR`, `GERENCIAR` ou `PAGAR` |
| Remover anexo | a mesma permissão de quem anexa: `GERENCIAR` (documento/boleto) ou `PAGAR` (comprovante) |

Sem permissão: **403**.

---

## 1. Recorrência

### Criação: `POST /contas-pagar` com o bloco `recorrencia`
```json
{ "descricao": "Aluguel da loja", "favorecido": "Imobiliária Centro", "categoriaId": 4, "valor": 3500.00, "vencimento": "2026-12-31",
  "recorrencia": { "periodicidade": "MENSAL", "dataFim": null } }
```
- `periodicidade`: `SEMANAL`, `MENSAL` ou `ANUAL`. `dataFim` é opcional (não pode ser anterior ao vencimento).
- O `vencimento` informado é a **primeira ocorrência** (obrigatório).
- **Exclusivo com `parcelamento`** (400: `"O lançamento recorrente não pode ser combinado com parcelamento."`). Também não aceita `linhaDigitavel` (400).
- Gera as ocorrências até **12 meses à frente de hoje** (ou até `dataFim`, o que vier antes). Se o primeiro vencimento for passado, as ocorrências
  passadas também são geradas. Limite de 520 ocorrências por geração (400 acima disso).
- Datas: **mensal** mantém o dia original e ajusta o fim de mês sem derivar (31/01, 28/02, 31/03, 30/04…); **anual** em 29/02 cai em 28/02 nos anos
  não bissextos (e volta a 29/02 nos bissextos); **semanal** a cada 7 dias.
- Cada ocorrência é um título comum (situação, pagamento, edição, cancelamento como qualquer outro), **sem sufixo n/N** na descrição, com `recorrenciaId`;
  competência = mês do vencimento; categoria, centro, forma, favorecido, documento e observação vêm da regra.
- Resposta (201): igual à criação comum, com o campo novo `recorrenciaId`:
```json
{ "grupoParcelamentoId": null, "recorrenciaId": 5, "titulos": [ { "...resumo...": "..." } ] }
```
Evento `RECORRENCIA_CRIADA` na regra e em cada título gerado.

### Rotina diária
Uma rotina interna (todos os dias às 03:30, fuso `America/Sao_Paulo`) completa a janela de 12 meses de todas as regras **ativas**. É idempotente:
nunca duplica uma data já gerada (mesmo que o vencimento da ocorrência tenha sido alterado depois ou que a ocorrência esteja cancelada), respeita `dataFim`
e regras encerradas. Registra `ultimaGeracao`. Não há endpoint para ela. Pode ser desativada com `app.financeiro.recorrencia.enabled=false`.

### `GET /contas-pagar/recorrencias/{id}`
```json
{ "id": 5, "periodicidade": "MENSAL", "dataInicio": "2026-12-31", "dataFim": null, "ativa": true,
  "descricao": "Aluguel da loja", "favorecido": "Imobiliária Centro",
  "categoria": {"id":4,"nome":"Aluguel","grupoId":1,"grupoNome":"Despesas operacionais"}, "centroCusto": null, "formaPrevista": null,
  "valor": 3500.00, "documento": null, "observacao": null, "ultimaGeracao": "2026-11-14T03:30:00", "encerradaEm": null,
  "quantidadeGerada": 13, "proximas": [ { "...resumo do título...": "..." } ],
  "eventos": [ { "id": 1, "evento": "RECORRENCIA_CRIADA", "detalhe": "...", "usuario": {...}, "dataHora": "..." } ],
  "createdAt": "...", "createdBy": {...}, "updatedAt": "...", "updatedBy": {...} }
```
`proximas`: até 6 ocorrências não canceladas com vencimento de hoje em diante, em ordem. 404 se a regra não existir.

### `PUT /contas-pagar/recorrencias/{id}` — reajuste
```json
{ "aPartirDe": "2027-01-01", "valor": 3800.00, "descricao": null, "favorecido": null, "categoriaId": null, "centroCustoId": null,
  "formaPrevistaId": null, "documento": null, "observacao": null }
```
`aPartirDe` e `valor` são obrigatórios; os demais campos, **quando omitidos (null), mantêm o valor atual da regra** (não há como limpar um campo por aqui).
Atualiza a regra (que vale para as próximas gerações) e **somente** as ocorrências com vencimento **maior ou igual a `aPartirDe`**, situação `ABERTO`
e sem pagamento; as demais (pagas, parciais, canceladas, anteriores) ficam intactas.
Resposta: `{ "atualizadas": 8, "regra": { ...mesmo formato do GET... } }`. Regra encerrada: 400. Evento `RECORRENCIA_REAJUSTADA` na regra e em cada título atualizado.

### `POST /contas-pagar/recorrencias/{id}/encerrar`
```json
{ "cancelarFuturas": true, "motivo": "Contrato rescindido" }
```
Desativa a regra (para de gerar). Com `cancelarFuturas: true` o `motivo` é obrigatório (mínimo 5 caracteres) e são canceladas as ocorrências
**futuras** (vencimento de hoje em diante) em `ABERTO` e sem pagamento; ocorrências com pagamento nunca são tocadas.
Resposta: `{ "canceladas": 11, "regra": {...} }`. Regra já encerrada: 400. Evento `RECORRENCIA_ENCERRADA` na regra e nos títulos cancelados.

### Detalhe do título
`GET /contas-pagar/{id}` passa a trazer, quando o título nasceu de uma regra, `recorrenciaId` e o bloco:
```json
"recorrencia": { "id": 5, "periodicidade": "MENSAL", "ativa": true, "dataFim": null, "posicao": 3, "totalGerado": 13 }
```
(`posicao` = ordem do título na série; `null` quando não há recorrência).

---

## 2. Anexos

Tipos de anexo: `DOCUMENTO`, `BOLETO` (no título) e `COMPROVANTE` (no pagamento).

### Upload
- `POST /contas-pagar/{id}/anexos` — `multipart/form-data` com `arquivo` (arquivo) e `tipo` (`DOCUMENTO` ou `BOLETO`). Exige `CONTA_PAGAR_GERENCIAR`.
- `POST /contas-pagar/pagamentos/{pagamentoId}/anexos` — `multipart/form-data` com `arquivo` (o tipo é sempre `COMPROVANTE`). Exige `CONTA_PAGAR_PAGAR`.

Resposta 201:
```json
{ "id": 9, "tipo": "BOLETO", "nome": "boleto-novembro.pdf", "tipoConteudo": "application/pdf", "tamanho": 48213, "pagamentoId": null,
  "createdAt": "2026-11-14T10:20:00", "createdBy": { "id": "...", "usuario": "admin", "primeiroNome": "..." } }
```

Regras e limites:
- **5 MB** por arquivo (acima: **413**); arquivo vazio: 400.
- **PDF, JPG ou PNG**. O tipo é conferido pelo **conteúdo** do arquivo (assinatura), não só pela extensão: extensão falsa ou tipo diferente: **400**.
  O `tipoConteudo` devolvido é o detectado. O nome é higienizado (sem caminho nem caracteres especiais; máximo de 120 caracteres).
- No máximo **10 anexos por título**, somando os do título e os dos seus pagamentos: o 11º é recusado com **409**.
- Título `PAGO` aceita anexos. Título `CANCELADO` **não** aceita novos (400), mas o download continua permitido. Pagamento estornado: o comprovante existente pode ser visto, mas **não se anexa novo** (400).
- Eventos `ANEXO_ADICIONADO` e `ANEXO_REMOVIDO` no histórico do título.

### Download: `GET /contas-pagar/anexos/{id}`
Requer o token (`Authorization: Bearer`) como as demais rotas. Responde com o arquivo (bytes), `Content-Type` do arquivo,
`Content-Disposition: attachment; filename="..."; filename*=UTF-8''...` e `X-Content-Type-Options: nosniff`.
Como exige o cabeçalho de autorização, faça o download com `fetch`/axios (`responseType: blob`) e crie um link temporário (`URL.createObjectURL`); não use `<a href>` direto.
Para exibir inline (ex.: imagem), use o mesmo blob. 404 se o anexo não existir.

### Remoção: `DELETE /contas-pagar/anexos/{id}` → 204
Exige a mesma permissão de escrita do tipo (`GERENCIAR` para documento/boleto, `PAGAR` para comprovante; 403 caso contrário). Título cancelado: 400.
O arquivo é apagado e o evento `ANEXO_REMOVIDO` é registrado.

### Consultas
- `GET /contas-pagar/{id}`: novo campo `anexos` (lista, mesmo formato do upload, inclui os comprovantes dos pagamentos, identificados por `pagamentoId`).
- `GET /contas-pagar` (lista): cada resumo traz `quantidadeAnexos` (inteiro).

---

## 3. Lançamento em lote: `POST /contas-pagar/lote` (200)
```json
{ "itens": [ { "descricao": "Energia", "categoriaId": 6, "valor": 410.20, "vencimento": "2026-12-10" },
             { "descricao": "", "categoriaId": 6, "valor": 10, "vencimento": "2026-12-10" } ] }
```
Até **100 linhas** (mais: 400 para a requisição inteira; lista vazia: 400). Cada linha usa os campos do lançamento **simples**
(`descricao`, `favorecido`, `categoriaId`, `centroCustoId`, `valor`, `emissao`, `competencia`, `vencimento`, `documento`, `formaPrevistaId`, `linhaDigitavel`, `observacao`);
`parcelamento` e `recorrencia` não são aceitos no lote (a linha é recusada). Cada linha é criada em transação própria: **uma linha inválida nunca derruba as outras**.
```json
{ "criadas": 1, "recusadas": 1,
  "resultados": [ { "indice": 0, "criada": true, "id": 120, "numero": 98, "menssage": null, "campo": null },
                  { "indice": 1, "criada": false, "id": null, "numero": null, "menssage": "A descrição é obrigatória.", "campo": "descricao" } ] }
```
`indice` é a posição na lista enviada (começa em 0). `campo` identifica o campo com problema quando é possível determiná-lo (pode ser `null`).

---

## Erros

| Situação | HTTP |
|---|---|
| Validações, recorrência com parcelamento, arquivo inválido/vazio/de tipo não permitido, título cancelado em anexo, regra encerrada, motivo curto | 400 |
| Sem permissão | 403 |
| Título, regra, pagamento ou anexo inexistentes | 404 |
| 11º anexo do título | 409 |
| Arquivo acima de 5 MB | 413 |

Exemplo: `{ "timestamp": "...", "status": "409 CONFLICT", "menssage": "O título já possui o número máximo de 10 anexos.", "path": "..." }`.

## Teto de valores (atualização)

Nenhum valor monetário informado pode ultrapassar **R$ 9.999.999,99**. Acima disso a resposta é **400** com mensagem formal, por exemplo
`"O valor não pode ser superior a R$ 9.999.999,99."` (ou `"O valor de juros não pode ser superior a R$ 9.999.999,99."`).
Vale para: valor do título (simples, parcelado, recorrência, lote e edição, inclusive o reajuste da recorrência), valores personalizados de parcelas,
principal, juros, multa e desconto do pagamento, ajuste, transferência e saldo inicial do banco (em módulo). O front pode limitar o campo a esse teto.

## Resumo do título na lista: `recorrenciaId`

O resumo de cada título (lista e demais respostas que usam o resumo) agora traz `recorrenciaId` (nulo quando o título não nasceu de uma recorrência),
para o ícone de recorrente. Categoria: no lançamento simples, no lote e na recorrência a categoria pode ser **grupo ou subgrupo** (mesmo comportamento).
