# Financeiro — Link de pagamento InfinitePay (Fase 3A, modalidade manual)

Migration **V73**. Rotas sem prefixo `/api`; erros em `{ "timestamp", "status", "menssage", "path" }`.
Branch `feat/infinitepay-link` (empilhada sobre `feat/receber-consignacao`).

**Escopo da Fase 3A (manual):** o sistema gera o link de pagamento na InfinitePay para **um título a receber** (parcela), o usuário o envia ao cliente (copiar, WhatsApp, e-mail) e a **baixa continua manual** pelo recebimento existente. **Não há** webhook, recebimento automático, confirmação de pagamento nem redirecionamento (Fase 3B). A InfinitePay **não permite cancelar** um link já criado: o sistema só registra o estado do link do seu lado.

## Conceitos
- Um título pode ter **vários links** (cada geração cria um registro próprio, com `orderNsu` único `CR-<número do título>-<código curto>`).
- `status` do link: `ATIVO`, `PAGO_MANUAL`, `ENCERRADO_TITULO_RECEBIDO`, `TITULO_CANCELADO`, `NAO_UTILIZAR`.
  - Rótulo para a tela do `NAO_UTILIZAR`: **"Não utilizar"** (substitui o antigo `OBSOLETO`; mapa antigo → novo: `OBSOLETO` → `NAO_UTILIZAR`). Texto de apoio: `Outro link deste título foi pago. Este link não deve mais ser utilizado, mas ainda pode ser pago na InfinitePay.`
- `expirado` (booleano) é **calculado** pela `validade` (data inclusiva, fuso de São Paulo); não é gravado. Um link `ATIVO` e `expirado` deve ser exibido como "Expirado".
- Valor mínimo do link: **R$ 1,00**; máximo: o **saldo em aberto** do título.

## Permissões
| Ação | Permissão |
| --- | --- |
| `GET /infinitepay/configuracao`, `PUT /infinitepay/configuracao` | **`INFINITEPAY_GERENCIAR`** (nova; concedida só ao perfil ADMIN na V73, mesmo padrão da configuração de e-mail) |
| `GET /infinitepay/padroes` | `INFINITEPAY_GERENCIAR`, `CONTA_RECEBER_VISUALIZAR`, `CONTA_RECEBER_GERENCIAR` ou `CONTA_RECEBER_RECEBER` |
| `GET /contas-receber/{id}/links-pagamento` | `CONTA_RECEBER_VISUALIZAR`, `CONTA_RECEBER_GERENCIAR` ou `CONTA_RECEBER_RECEBER` |
| `POST /contas-receber/{id}/links-pagamento` | `CONTA_RECEBER_GERENCIAR` |
| `POST /links-pagamento/{id}/marcar-pago`, `POST /links-pagamento/{id}/enviar-email` | `CONTA_RECEBER_GERENCIAR` |
| `linkId` no recebimento manual | `CONTA_RECEBER_RECEBER` (a do recebimento) |

Sem permissão: 403 **antes** de qualquer validação do corpo. Sem token: 401. A nova permissão deve aparecer na tela de perfis (`INFINITEPAY_GERENCIAR`).

## Configuração — `GET` / `PUT /infinitepay/configuracao`
A **chave da API nunca é devolvida** (nem registrada em log). O usuário informa a InfiniteTag e a chave pela tela; ninguém além dele as conhece.
```json
GET 200
{ "handle": "minha-tag", "chaveConfigurada": true, "chaveFinal": "****abcd",
  "validadeDias": 7, "pagamentoUnico": true, "taxaParcelamentoAssumida": 0, "pixHabilitado": true,
  "descontoPix": 0, "descontoCartao": 0, "exigeEnderecoEntrega": false,
  "textoWhatsapp": "Olá, {cliente}! ...{link}", "textoEmail": "...",
  "envioEmailHabilitado": false, "atualizadoEm": "2026-11-16T10:00:00", "atualizadoPor": { "id": "...", "usuario": "...", "primeiroNome": "...", "sobrenome": "..." } }
```
- `PUT` (mesmos campos, todos opcionais exceto o que o usuário quiser alterar; **`chave` vazia ou ausente mantém a anterior**; `removerChave: true` apaga a chave):
```json
{ "handle": "minha-tag", "chave": "inchk_...", "removerChave": false, "validadeDias": 7, "pagamentoUnico": true, "taxaParcelamentoAssumida": 0,
  "pixHabilitado": true, "descontoPix": 0, "descontoCartao": 0, "exigeEnderecoEntrega": false, "textoWhatsapp": "...", "textoEmail": "..." }
```
- `handle`: InfiniteTag **sem** `$` (o `$` inicial e espaços são removidos); até 60 caracteres, letras, números, `.`, `_`, `-`.
- `chaveFinal` mostra apenas os 4 últimos caracteres mascarados (nulo sem chave).
- `validadeDias` 0 a 365 (0 = padrão "sem validade"); `taxaParcelamentoAssumida` 0 a 12; descontos: 0 ou maiores que 0 e menores que 100.
- `textoWhatsapp` / `textoEmail` (até 2000): marcadores `{cliente}`, `{valor}`, `{descricao}`, `{link}`, `{validade}`. Vazios = textos formais padrão (terminam com `Validade do link: {validade}.`).
  - `{validade}` devolve **somente a data** (`DD/MM/AAAA`) ou, quando o link não tem validade, o texto `sem data de expiração`; **não** inclui a preposição. Um texto personalizado deve escrever a palavra ao redor (ex.: `Validade: {validade}` ou `Válido até {validade}`; neste último, sem validade ficaria `Válido até sem data de expiração`, por isso prefira `Validade: {validade}`).
- `envioEmailHabilitado` é **somente leitura** (vem da propriedade `app.infinitepay.envio-email-enabled`, hoje `false`).
- Erros: 400 (validações), 403, 409 `Não foi possível gravar a chave com segurança.` (sem segredo de criptografia no servidor).

## Padrões para a tela de geração — `GET /infinitepay/padroes` (200)
Somente leitura, para quem gera links (sem exigir `INFINITEPAY_GERENCIAR`). **Não** devolve InfiniteTag, chave nem textos.
```json
{ "configurada": true, "validadeDias": 7, "pagamentoUnico": true, "taxaParcelamentoAssumida": 0, "pixHabilitado": true,
  "descontoPix": 0, "descontoCartao": 0, "exigeEnderecoEntrega": false, "envioEmailHabilitado": false }
```
- `configurada` = InfiniteTag **e** chave presentes. Com `false`, a tela deve desabilitar o botão de gerar link e exibir: `Configure a InfinitePay em Sistema antes de gerar links.`

## Gerar link — `POST /contas-receber/{id}/links-pagamento`
```json
{ "descricao": "Consignação Nº 12 — acerto", "valor": 250.00, "validade": "2026-11-23", "pagamentoUnico": true,
  "taxaParcelamentoAssumida": 0, "pixHabilitado": true, "descontoPix": 5, "descontoCartao": null, "exigeEnderecoEntrega": false,
  "cliente": { "nome": "Maria", "email": "maria@exemplo.com", "telefone": "(24) 99999-8888" } }
```
- **Todos os campos são opcionais.** Ausentes usam o padrão da configuração (`pagamentoUnico`, `taxaParcelamentoAssumida`, `pixHabilitado`, `descontoPix`, `descontoCartao`, `exigeEnderecoEntrega`), `valor` = saldo em aberto, `descricao` = descrição do título.
- **`validade` ausente ou nula = link sem validade.** A tela deve preencher a data a partir de `validadeDias` (hoje + N dias) e permitir limpar. Regra: data **futura** (a partir de amanhã) e no máximo **1 ano**. O link vale até o fim do dia informado.
- `valor`: de 1,00 até o saldo em aberto do título (2 casas). `descricao`: até 200 caracteres.
- `taxaParcelamentoAssumida` 0 a 12 (0 = o cliente paga a taxa em todas as parcelas; N = a empresa absorve até N parcelas). A InfinitePay limita as parcelas do cartão para que **cada parcela seja ≥ R$ 1,00** (até 12x); o sistema só exige valor ≥ R$ 1,00.
- `descontoPix` / `descontoCartao`: ausentes, 0 (sem desconto) ou maiores que 0 e menores que 100 (percentual). `descontoPix` > 0 com `pixHabilitado: false` é recusado.
- `cliente` (opcional): se ausente, usa nome, e-mail e celular/telefone do cadastro do cliente do título (título avulso: nome do pagador). Só é **enviado o que for válido**: e-mail básico; telefone normalizado para `+55DDDNÚMERO` (10 ou 11 dígitos; celular preferido); dado inválido é **omitido** (não bloqueia). O registro guarda o que de fato foi enviado.
- Só gera para título **ABERTO** ou **RECEBIDO_PARCIAL**. Título cancelado ou recebido: 400.
- **Proteção contra duplo clique:** se existir um link `ATIVO` do mesmo título, criado há menos de **2 minutos**, com **os mesmos dados**, o sistema **não cria outro**: devolve o existente com **200** e `reaproveitado: true` (a criação nova devolve **201** e `reaproveitado: false`). Dados diferentes geram novo registro e **os anteriores permanecem ATIVOS** (a InfinitePay não permite cancelá-los).
- Resposta (201/200) — mesmo corpo do item da lista:
```json
{ "id": 5, "contaReceberId": 31, "tituloNumero": 31, "orderNsu": "CR-31-7f3a9c1e", "url": "https://checkout.infinitepay.io/...",
  "valor": 250.00, "descricao": "Consignação Nº 12 — acerto", "validade": "2026-11-23", "expirado": false,
  "pagamentoUnico": true, "taxaParcelamentoAssumida": 0, "pixHabilitado": true, "descontoPix": 5, "descontoCartao": 0, "exigeEnderecoEntrega": false,
  "cliente": { "nome": "Maria", "email": "maria@exemplo.com", "telefone": "+5524999998888" },
  "status": "ATIVO", "recebimentoId": null, "criadoEm": "2026-11-16T10:00:00", "criadoPor": { "id": "...", "usuario": "...", "primeiroNome": "...", "sobrenome": "..." },
  "reaproveitado": false,
  "textoWhatsapp": "mensagem pronta com o link", "textoEmail": "mensagem pronta com o link" }
```
- `textoWhatsapp` / `textoEmail` já vêm com os marcadores substituídos (editáveis na tela antes do envio). O WhatsApp é aberto pelo frontend (`https://wa.me/55DDDNÚMERO?text=...`); o sistema não envia WhatsApp.
- Uma geração registra no histórico do título o evento `LINK_PAGAMENTO_GERADO` (texto formal, sem a URL completa).

## Listar — `GET /contas-receber/{id}/links-pagamento` (200)
Lista de links do título, do mais recente ao mais antigo, no formato acima (`reaproveitado` sempre `false`). Título inexistente: 404.

## Marcar como pago manualmente — `POST /links-pagamento/{id}/marcar-pago`
```json
{ "recebimentoId": 40, "observacao": "Pago pelo link; conferido no extrato." }
```
- Corpo opcional. `recebimentoId` (opcional): o recebimento **ativo e do mesmo título** que corresponde ao pagamento. `observacao` até 500.
- Só link `ATIVO`. O link vira `PAGO_MANUAL`; os **demais links ATIVOS do mesmo título viram `NAO_UTILIZAR`**. Evento `LINK_PAGAMENTO_MARCADO_PAGO`. Marcar **não** registra recebimento algum: a baixa é feita pela tela de recebimento.
- Resposta 200: o link atualizado. Erros: 404; 400 `Somente links ativos podem ser marcados como pagos.`; 400 recebimento de outro título ou estornado.

## Recebimento manual com link — campo `linkId`
`POST /contas-receber/{id}/recebimentos` (e o recebimento no mesmo ato **não** usa) aceita o campo **opcional** `linkId` no corpo atual:
```json
{ "dataRecebimento": "...", "valorPrincipal": 250.00, "bancoId": 1, "formaPagamentoId": 3, "linkId": 5 }
```
- Valida que o link é **do título** e está **ATIVO** (senão 400 `O link de pagamento informado não pertence a este título.` ou `... não está ativo.`). O banco e a forma continuam escolhidos pelo usuário.
- Na mesma transação: o link vira `PAGO_MANUAL` com `recebimentoId` preenchido, e os demais ATIVOS do título viram `NAO_UTILIZAR`. Se a baixa falhar, nada muda. Sem `linkId` o recebimento segue como hoje.
- A tela deve oferecer o campo opcional "Pago pelo link" listando os links ATIVOS do título.

## Enviar por e-mail — `POST /links-pagamento/{id}/enviar-email`
```json
{ "para": ["maria@exemplo.com"], "assunto": "Link para pagamento", "mensagem": "texto editado" }
```
- **Desabilitado por enquanto:** enquanto `app.infinitepay.envio-email-enabled=false` (padrão), responde **409** `O envio por e-mail está desabilitado no momento.` O frontend deve manter o botão **desabilitado** com dica formal (usar `envioEmailHabilitado` da configuração).
- Quando habilitado: usa a configuração de e-mail do sistema; `para` (1 a 5 endereços válidos), `assunto` (até 200, sem quebra de linha), `mensagem` (até 2000; ausente = `textoEmail`). Evento `LINK_PAGAMENTO_ENVIADO`. Erros: 400 (endereços), 409 (link não ativo), 503 (e-mail não configurado), 502 (falha de envio).

## Ciclo de vida dos links
| Fato no título | Efeito nos links `ATIVO` |
| --- | --- |
| Título **quitado** (`RECEBIDO`) por qualquer recebimento (individual, no ato ou em lote) | viram `ENCERRADO_TITULO_RECEBIDO` |
| Título **cancelado** (individual ou em lote) | viram `TITULO_CANCELADO`. A resposta do cancelamento individual traz `aviso`: `Os links de pagamento já enviados não podem ser cancelados na InfinitePay e ainda podem ser pagos.` (exibir ao usuário; vale só quando havia link ativo) |
| Link marcado como pago (manual ou por `linkId`) | os outros ATIVOS do título viram `NAO_UTILIZAR` |
| **Estorno** do recebimento (título reabre) | links `ENCERRADO_TITULO_RECEBIDO` **não** voltam a `ATIVO` sozinhos; o usuário gera um novo link |
- Links nos estados finais não mudam mais. `expirado` é calculado, nunca gravado.
- **Cancelamento em lote** (`POST /contas-receber/cancelar-lote`): a resposta ganha o campo aditivo `aviso` (texto único, **somente** quando ao menos um título cancelado tinha link `ATIVO`; sem links, o campo não vem). Exemplo: `Os links de pagamento já enviados de 2 títulos cancelados não podem ser cancelados na InfinitePay e ainda podem ser pagos.` (no singular: `de 1 título cancelado não podem`). Exibir ao usuário depois do resumo do lote.
- `aviso` é um campo novo e **aditivo** (somente quando há texto) em `ContaReceberDetalheDTO`, usado só no `POST /contas-receber/{id}/cancelar`.

## Erros da geração
| Situação | Status / mensagem |
| --- | --- |
| Sem token / sem permissão (mesmo com corpo inválido) | 401 / 403 |
| Título inexistente | 404 |
| InfinitePay não configurada (sem InfiniteTag ou sem chave) | 409 `Configure a InfinitePay em Sistema antes de gerar links.` |
| Título cancelado ou recebido | 400 `Somente títulos em aberto ou parcialmente recebidos geram link de pagamento.` |
| Valor < 1,00 ou > saldo em aberto | 400 (mensagem informa o limite) |
| Validade passada, hoje ou além de 1 ano | 400 |
| Taxa fora de 0 a 12; desconto fora de (0, 100); desconto PIX com PIX desabilitado | 400 |
| Descrição acima de 200 | 400 |
| InfinitePay recusou (400/422) | 502 `A InfinitePay recusou os dados do link. Revise o valor, a validade e as opções.` |
| Chave/InfiniteTag inválida (401/403) | 502 `A InfinitePay não aceitou as credenciais configuradas. Verifique a InfiniteTag e a chave em Sistema.` |
| Indisponibilidade, 5xx ou tempo esgotado | 502 `A InfinitePay está indisponível no momento. Tente novamente em instantes.` |
| Resposta sem URL do link | 502 `A InfinitePay não devolveu o link de pagamento.` |
Nenhuma mensagem expõe a chave nem o corpo bruto da resposta da InfinitePay (o sistema guarda uma cópia truncada da resposta apenas para diagnóstico).

## O que o frontend deve travar
- Tela Sistema > InfinitePay visível só com `INFINITEPAY_GERENCIAR`; o campo da chave nunca é preenchido com valor (usar `chaveConfigurada` / `chaveFinal`).
- Botão "Gerar link de pagamento" só com `CONTA_RECEBER_GERENCIAR` e título ABERTO/RECEBIDO_PARCIAL; bloquear duplo clique.
- Mostrar `reaproveitado: true` como "link já gerado há instantes".
- Botão de e-mail desabilitado enquanto `envioEmailHabilitado` for `false`.
- No cancelamento, exibir o `aviso` quando vier.
