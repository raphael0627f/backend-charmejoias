# Etiqueta da montagem de mostruário — guia de integração frontend

Gera em PDF a etiqueta (283x425, **uma etiqueta por PDF**) de uma montagem de mostruário.

## Endpoint

`GET /montagens/{id}/etiqueta`

- **Permissão:** `MONTAGEM_GERENCIAR` (sem ela, `403`).
- **Sem corpo e sem query params.**
- **Resposta `200`:** o PDF em bytes.
  - `Content-Type: application/pdf`
  - `Content-Disposition: inline; filename=etiqueta-montagem-{id}.pdf`

Como o endpoint exige o token JWT, não dá para abrir a URL direto com `window.open`/`<a href>`. Baixe com `fetch` + `Authorization: Bearer <token>`, converta a resposta em `blob` e abra com `URL.createObjectURL(blob)` (ex.: em nova aba para imprimir).

## Regras

| Situação | Resposta |
|---|---|
| Status `ABERTA`, `EM_MONTAGEM` ou `CONCLUIDA`, com ao menos uma consignação vinculada | `200` + PDF |
| Montagem sem nenhuma consignação vinculada | `400` |
| Montagem `CANCELADA` | `400` |
| Montagem inexistente | `404` |
| Usuário sem `MONTAGEM_GERENCIAR` | `403` |
| Sem token / token inválido | `401` |

Os erros vêm em JSON no formato padrão (`RestErrorMenssage`, campo **`menssage`** com dois s). Atenção: quando o erro acontece o corpo é JSON, não PDF — confira o `status`/`Content-Type` antes de criar o blob.

```json
{ "timestamp": "2026-10-28T00:16:16.684Z", "status": "400 BAD_REQUEST", "menssage": "Para imprimir a etiqueta é necessário ao menos uma consignação vinculada", "path": "uri=/montagens/16/etiqueta" }
```

```json
{ "timestamp": "...", "status": "400 BAD_REQUEST", "menssage": "Montagem 25 está CANCELADA e não pode ter a etiqueta impressa", "path": "uri=/montagens/25/etiqueta" }
```

```json
{ "timestamp": "...", "status": "404 NOT_FOUND", "menssage": "Montagem não encontrada. id: 999999", "path": "uri=/montagens/999999/etiqueta" }
```

```json
{ "timestamp": "...", "status": "403 FORBIDDEN", "menssage": "Acesso negado", "path": "uri=/montagens/17/etiqueta" }
```

## Conteúdo da etiqueta (resumo)

Datas em `dd/MM/yyyy`. Cliente = cliente da montagem.

- **Código / referência:** `codigo` e `referencia` do cliente.
- **Mostruário:** descrição do mostruário da montagem.
- **Data da visita:** `dataEntrega` da montagem. **Data da troca:** `dataAcertoConsignacao` da **primeira** consignação vinculada (vazia se nula).
- **Nome:** `nome - celular / telefone` (partes vazias são omitidas).
- **Endereços:** todos, um por linha, principal primeiro: `descricao: logradouro - numero - bairro - municipio - complemento - pontoReferencia (latitude, longitude)` (partes vazias omitidas).
- **Nome / Bairro / Cidade:** nome completo, bairro e município do endereço principal (ou o primeiro, se nenhum for principal).
- **Sobrenome:** 5+ palavras → todas menos as 2 primeiras; 2 a 4 palavras → todas menos a primeira; 1 palavra → ela mesma.

## Limitações conhecidas do layout

- O quadro de **Endereços** tem altura fixa: cliente com muitos endereços (ou endereços longos) tem as últimas linhas cortadas na etiqueta.
- Os dois quadros sem rótulo ("Observação" e o abaixo do PIX) são campos do próprio modelo `.jasper` sem expressão e imprimem o texto `null`.
