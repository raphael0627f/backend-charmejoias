# Receptor de pagamentos (Cloudflare Worker + D1) — Charme Joias

Recebe o webhook da InfinitePay a qualquer hora e guarda numa fila; o ERP (quando ligado) **busca** a fila por requisição de saída, confirma cada pagamento no `payment_check` e baixa o título. O receptor **só recebe e guarda**: não decide, não chama a InfinitePay e não tem a chave dela. O servidor local nunca é exposto.

- Worker: `charme-pagamentos` · D1: `charme-pagamentos-db` · domínio: `pagamentos.charmejoias.com.br` (Custom Domain; `workers.dev` desligado).
- O `webhook_url` é gravado **por link** na InfinitePay e **não pode ser editado**: nunca usar `workers.dev` em link real.

## Rotas
| Rota | Quem | Descrição |
|------|------|-----------|
| `POST /w/{token}` | InfinitePay | Guarda o evento (JSON até 16 KB; idempotente por `transaction_nsu` + evento). Token errado: 404 sem corpo. Corpo estranho/inválido é guardado do mesmo modo (o ERP decide; nada é descartado). |
| `GET /fila?limite=N` | ERP (`Authorization: Bearer <SEGREDO_ERP>`) | Itens sem ack, mais antigos primeiro (padrão 50, máximo 200). |
| `POST /fila/ack` | ERP (Bearer) | `{"ids":[...]}` (1 a 200). Idempotente. |
| `GET /saude` | ERP (Bearer) | `{ok, pendentes, maisAntigoEm, total, confirmados, maisAntigoRetidoEm, retencaoDias, versao}` (v1.2.0: contagem dos registros retidos; os confirmados são apagados após 90 dias). |

Qualquer outra rota/método: 404. Sem CORS.

## Segredos (Cloudflare *secrets*; nunca no repositório)
- `TOKEN_ENTRADA`: token que vai **na URL** do webhook (`/w/<token>`). Aceita vários separados por vírgula (rotação: o novo entra primeiro; o antigo continua válido para links já gerados).
- `SEGREDO_ERP`: segredo do ERP para `/fila`, `/fila/ack` e `/saude`. **Diferente** do token de entrada.
- Ambos: 32 a 200 caracteres, letras, números, `-` e `_` (o ERP valida o mesmo formato). Gere, por exemplo, com `node -e "console.log(require('crypto').randomBytes(36).toString('base64url'))"`.

## Retenção
Não há cron. O ERP, durante a busca (no máximo 1 vez a cada 24 h), chama `POST /fila/limpar` (Bearer `SEGREDO_ERP`, corpo `{"dias":90}`), que apaga **somente** itens **confirmados (ack) há mais de 90 dias** (`dias` mínimo 90). Itens **sem ack nunca são apagados** (o ERP alerta o administrador quando a fila fica parada).

## Teste local (sem Cloudflare, sem InfinitePay)
```
npm install
# crie .dev.vars (ignorado pelo git) com TOKEN_ENTRADA=<t1>,<t2> e SEGREDO_ERP=<s>
npm run db:local
npm run dev            # outro terminal (wrangler dev --local)
npm test               # teste de contrato (33 verificações)
```

## Implantação (feita por quem tem o token `CLOUDFLARE_API_TOKEN` no ambiente; o valor nunca é impresso nem gravado)
1. `npx wrangler d1 create charme-pagamentos-db` → copiar o `database_id` para `wrangler.toml` (não é segredo).
2. `npx wrangler d1 execute charme-pagamentos-db --remote --file=schema.sql`.
3. `npx wrangler secret put TOKEN_ENTRADA` e `npx wrangler secret put SEGREDO_ERP` (valor digitado/colado no prompt; não vai para arquivo).
4. `npx wrangler deploy` → cria o Worker, o Custom Domain `pagamentos.charmejoias.com.br` (registro DNS e certificado) (sem cron).
5. Conferir: `GET https://pagamentos.charmejoias.com.br/saude` sem Bearer = 401; com Bearer = `{"ok":true,...}`; `POST /w/<token-errado>` = 404.
6. No ERP (Sistema > InfinitePay): URL `https://pagamentos.charmejoias.com.br`, token de entrada e segredo, depois "Testar receptor".

## Rollback
`npx wrangler delete` remove o Worker e o Custom Domain; o D1 pode ser mantido (fila preservada). Os links já gerados com este webhook deixam de ser notificados até o Worker voltar (a baixa manual continua existindo).
