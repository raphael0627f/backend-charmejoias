# Ajustes 6.6 — contrato do backend para o Frontend

Branch `feat/ajustes-6-6` (backend). Erros seguem `RestErrorMenssage` (`{"menssage": "..."}`, dois "s"); sem prefixo `/api`. Todos os campos novos são **aditivos**: nada do que existe foi removido ou renomeado. Valores monetários em reais (`BigDecimal`, 2 casas) e datas ISO.
Legenda: ✅ pronto no backend · 🔜 contrato fechado, implementação em andamento (o Frontend pode construir contra este documento; qualquer mudança será avisada).

---

## 0. Regra geral — "valor realmente pago" (T1)

**Definição (calculada no servidor, o front só exibe):**
- `totalPago` = soma de `valor_total` (principal + juros + multa − desconto) dos recebimentos **não estornados** do título. Recebimento por **crédito do cliente** (`origem = CREDITO_CLIENTE`) **conta**.
- `valorCreditoAplicado` = parte de `totalPago` que veio de crédito do cliente (sem movimentação bancária). `creditoAplicado` (boolean) = `valorCreditoAplicado > 0` → o front mostra o aviso formal **"Inclui crédito do cliente aplicado: R$ X"**.
- **Taxa da InfinitePay** (`valorTaxaCliente`) **fica FORA** do `totalPago` e segue como campo à parte (informação, não é caixa).
- **Em aberto e Vencido continuam por principal** (`valor − valorRecebido`). `valorRecebido` (antigo) **continua sendo o principal abatido** — não mudou de significado.
- Título cancelado: nunca tem recebimento ativo (só se cancela sem recebimento); `totalPago = 0`. Estornos: recebimento estornado não entra em nenhuma soma. Recebimento parcial: soma normal.

**O que o front deve usar:** para qualquer texto "Recebido/Pago pelo cliente" usar `totalPago`/`valorPago`; usar `valorRecebido` só onde a conta é de saldo (ex.: "Em aberto = valor − valorRecebido").

### Levantamento (lugar · antes · depois)
| # | Lugar (backend) | Antes | Depois |
|---|---|---|---|
| 1 | Lista de Contas a Receber — totais (`GET /contas-receber` → `totais`) | `valorRecebido` = Σ principal | **+ `valorPago`** (Σ total real) e `valorCreditoAplicado`; `valorRecebido` mantido. Cards: "Recebido" passa a usar `valorPago`; "Em aberto"/"Vencido" inalterados |
| 2 | Lista de Contas a Receber — cada linha (`ContaReceberResumoDTO`) | `valorRecebido` (principal) | **+ `valorPago`**, `valorCreditoAplicado` |
| 3 | Detalhe do título (`GET /contas-receber/{id}`) | só `valorRecebido` + tabela de recebimentos | **+ `totalPago`, `totalPrincipal`, `totalJuros`, `totalMulta`, `totalDesconto`, `totalTaxaCliente`, `valorCreditoAplicado`, `creditoAplicado`** |
| 4 | Resumo financeiro do pré-acerto/acerto (`GET /pre-acertos-consignacao/{id}/financeiro`, `/acertos-consignacao/{id}/financeiro`) | `valorRecebido` (principal; base do excedente) e por título | **+ `valorPago`, `valorCreditoAplicado`** no `Resumo` e **+ `valorPago`** por título. `valorRecebido`/`excedenteRecebido` seguem por principal (são regra de excedente, não exibição de pago) |
| 5 | Situação dos clientes (`/contas-receber/clientes/...situacao`) | `recebidoNoPeriodo` já é o **valor real** (`valor_total`, não estornado) e **exclui crédito** (é caixa do período); em aberto/vencido por principal | **Sem mudança de cálculo** (já era o total real). + `creditoAplicado` no período (informativo, fora do valor). Em aberto/vencido inalterados |
| 6 | Aba Financeiro do cliente (nova) | não existia por consignação | `valorPago` = total real + indicador de crédito (ver seção 3) |
| 7 | Recebimento em lote (`ResultadoLote.totalRecebido`) | soma do **valor recebido de cada item** | inalterado (é o resultado da operação, já em total real do recebimento) |
| 8 | Caixa / movimentações bancárias | `valor_total` do recebimento | inalterado (já era o total real; taxa do cliente nunca entra) |
| 9 | Relatórios, exportações e dashboard | — | **Não existe relatório/exportação/dashboard de Contas a Receber no backend** (busca no código); nada a alterar |

---

## 1. Título (detalhe) — totais e origem na consignação 🔜
`GET /contas-receber/{id}` (`ContaReceberDetalheDTO`) ganha, no nível raiz:
```
totalPago, totalPrincipal, totalJuros, totalMulta, totalDesconto, totalTaxaCliente   // BigDecimal, recebimentos não estornados
valorCreditoAplicado                                                                  // BigDecimal (parte do totalPago vinda de crédito)
creditoAplicado                                                                       // boolean
consignacao: { tipoDocumento: "PRE_ACERTO"|"ACERTO", documentoId, consignacaoId,
               valorVendido, valorComissao, valorAPagar,                              // do documento de origem
               valorTitulo, parcelaNumero, parcelaTotal } | null                      // null = título sem origem em consignação
```
- `valorVendido` = valor total dos produtos do documento; `valorComissao` = desconto/comissão do documento; `valorAPagar` = `valorVendido − valorComissao` (valor do documento). `valorTitulo` = valor deste título (parcela) — em título parcelado o front mostra o valor do documento e o da parcela.
- Acerto com valor a pagar zero: `valorAPagar = 0.00` (mostrar "R$ 0,00" com texto formal).
- **Diálogo "Gerar link"** usa `consignacao` do detalhe do título (o front já tem o detalhe); **não** há endpoint novo para o link.
- Crédito do cliente/excedente e taxa da InfinitePay **não** entram nesse bloco.

Tabela de recebimentos (`recebimentos[]`) inalterada (já traz `valorTotal`, `juros`, `multa`, `desconto`, `valorTaxaCliente`, `automatico`, `origem`).

## 2. Lista de Contas a Receber 🔜
- `totais` (`GET /contas-receber`): `{ quantidade, valorTotal, valorRecebido, valorAberto, valorVencido, valorPago, valorCreditoAplicado }`. **Card "Recebido" → `valorPago`** (tooltip formal: "Valor efetivamente recebido, incluindo juros e multa e descontando descontos. Inclui crédito do cliente aplicado, quando houver."). Mostrar o aviso de crédito se `valorCreditoAplicado > 0`.
- Cada linha: `+ valorPago`, `+ valorCreditoAplicado`.

## 3. Pré-acerto e acerto — resumo financeiro (T2) 🔜
Contrato atual **mantido** (`titulos[]` = títulos do próprio documento; `linkAutomaticoAtivo`, `valorTaxaCliente`, `pagoPeloLink`, `existeLinkAutomaticoAtivo`, `valorTaxaClienteTotal`, totais do documento). Novos campos no `Resumo`:
```
valorPago, valorCreditoAplicado, creditoAplicado          // total real dos títulos do documento (regra geral)
valorVendido, valorComissao, valorAPagar                  // do documento (para os diálogos Gerar título/Gerar link)
titulosConsignacao: [ TituloConsignacao ]                 // TODOS os títulos da consignação (pré-acerto + acerto, inclusive cancelados)
totalConsignacao: { quantidade, quantidadeCancelados, valorTitulado, valorRecebido, valorPago, valorCreditoAplicado, valorTaxaCliente, emAberto }
```
`TituloConsignacao` = os campos de `Titulo` atuais + `valorPago`, `valorCreditoAplicado` + 
`{ doDocumento: boolean, origem: { tipo: "PRE_ACERTO"|"ACERTO"|"OUTRO", documentoId, numero }, cancelado: boolean, descricao, numero }`.
- Ordenação sugerida no front: vencimento, depois número. Selo "Cancelado" quando `cancelado` (ocultável).
- **Ações (Receber, Cancelar, link, WhatsApp) só nos títulos com `doDocumento = true`**; os demais mostram "Abrir título" (`/contas-receber/{id}`).
- `totalConsignacao` é exibido **à parte**; os totais do documento seguem como hoje. **Regras de bloqueio/edição do documento NÃO mudam** por causa de títulos de outros documentos.
- `valorVendido/valorComissao/valorAPagar` valem para o **documento aberto** (pré-acerto ou acerto): acerto = produtos acertados; pré-acerto = soma dos itens e desconto. Pré-acerto/acerto sem consignação: mesmos campos (do próprio documento); `titulosConsignacao` traz só os do documento.

## 4. Aba Financeiro do cliente (T3) 🔜
Permissão: a mesma de `GET /contas-receber/clientes/{id}/situacao` (`CONTA_RECEBER_VISUALIZAR`/`GERENCIAR`/`RECEBER`).

**Regra do documento por consignação (confirmada no código):** a consignação tem **no máximo um pré-acerto** (índice único) e **no máximo um acerto**. Se existe **acerto**, vale **só o acerto** (ele já contém todos os produtos acertados da consignação); senão, o **pré-acerto**. Nunca se soma pré-acerto + acerto (não há contagem em dobro). Consignação sem acerto nem pré-acerto não tem valores e só aparece se tiver títulos.

### 4.1 `GET /contas-receber/clientes/{clienteId}/consignacoes?filtro=TODAS|EM_ABERTO|VENCIDAS|QUITADAS&page=0&size=20`
`size` padrão 20, máximo 100 (acima → 100); ordem fixa: **mais recente primeiro** (data da consignação, depois id). Resposta:
```
{ content: [Consignacao], page, size, totalElements, totalPages, filtro,
  resumo: { quantidade, valorVendido, valorComissao, valorAPagar, valorPago, valorCreditoAplicado, emAberto },   // do conjunto filtrado
  semConsignacao: { quantidadeTitulos, valorPago, valorCreditoAplicado, emAberto, vencimentoMaisAntigo, selo } | null }   // só na primeira página (page=0)
Consignacao = { consignacaoId, numero (= id), data, documentoTipo: "ACERTO"|"PRE_ACERTO"|null, documentoId,
                valorVendido, valorComissao, valorAPagar,
                valorPago, valorCreditoAplicado, creditoAplicado, valorTaxaCliente,        // valorPago = TOTAL REAL; taxa à parte
                emAberto, vencimentoMaisAntigo (vencido mais antigo, ou null),
                selo, quantidadeTitulos, quantidadeCancelados }
```
**Selo** (`selo`), nesta ordem de precedência: `CANCELADA` (consignação cancelada ou todos os títulos cancelados) → `SEM_VALOR_A_PAGAR` (documento com `valorAPagar = 0`) → `SEM_TITULO` (nenhum título; documento com valor a pagar) → `QUITADA` (em aberto = 0) → `PARCIAL_VENCIDA` / `VENCIDA` (há saldo vencido; "parcial" quando já houve pagamento) → `PARCIAL` / `EM_ABERTO`. Rótulos sugeridos: Cancelada, Sem valor a pagar, Sem título, Quitada, Parcial vencida, Vencida (com `vencimentoMaisAntigo`), Parcial, Em aberto.
**Filtros:** `EM_ABERTO` = selos com saldo (EM_ABERTO, PARCIAL, VENCIDA, PARCIAL_VENCIDA); `VENCIDAS` = VENCIDA, PARCIAL_VENCIDA; `QUITADAS` = QUITADA; `TODAS` = todos. Títulos cancelados ficam **fora das somas** (`valorPago`, `emAberto`); `quantidadeCancelados` informa quantos.

### 4.2 Títulos de uma consignação (botão "Ver títulos")
`GET /contas-receber/clientes/{clienteId}/consignacoes/{consignacaoId}/titulos?page=0&size=20` (máx. 100; ordem: vencimento, número). 
`GET /contas-receber/clientes/{clienteId}/sem-consignacao/titulos?page=0&size=20` — linha "Sem consignação".
Resposta paginada de `TituloLinha = { id, numero, descricao, parcelaNumero, parcelaTotal, vencimento, valor, valorPago, valorCreditoAplicado, emAberto, situacao, cancelado, origem: { tipo, documentoId, numero } }`. O botão de cada linha abre `/contas-receber/{id}`.

### 4.3 Contas a pagar do cliente — `GET /contas-pagar/clientes/{clienteId}?page=0&size=20`
Permissão: a mesma de `GET /contas-pagar`. Lista **completa** (todos os títulos a pagar do cliente como favorecido), **mais recente primeiro** (emissão desc, id desc), `size` máx. 100. Resposta:
```
{ content: [ { id, numero, descricao, emissao, vencimento, valor, valorPago, emAberto, situacao, cancelado } ],
  page, size, totalElements, totalPages,
  totais: { quantidade, valor, valorPago, emAberto } }      // títulos cancelados fora das somas
```
O botão de cada linha abre `/contas-pagar/{id}`. Elimina o "Ver todos os títulos a pagar".

---

## 5. InfinitePay (T4)

### 5.1 Padronização de erros 400 (F3, F4) ✅/🔜
- `GET /infinitepay/revisoes?sort=...` com campo inválido → **400** `{"menssage": "Campo de ordenação inválido."}` (sem stack trace). Campos aceitos: `id`, `criadoEm`, `status`, `motivoCodigo`, `transactionNsu`, `orderNsu`, `tentativas`, `resolvidoEm`. O mesmo tratamento vale para **todas** as listas paginadas do sistema (ordenação por campo inexistente → 400 formal).
- `de > ate` → 400 `"A data inicial não pode ser posterior à data final."`; `motivo` inválido → 400 `"Motivo de revisão inválido."`; `status`/`clienteId` inválidos continuam 400.

### 5.2 Reprocessar (F1 — MÉDIO) 🔜
`POST /infinitepay/revisoes/{id}/reprocessar` agora **exige confirmação no corpo**: `{ "confirmar": true }` (sem isso → 400 `"Confirme o reprocessamento para continuar."`). Comportamento:
- Se a InfinitePay **confirmar** o pagamento → baixa (como hoje) e o item sai da revisão.
- Se a InfinitePay **não confirmar** ou a consulta falhar → o item **permanece em `REVISAO`** (nunca vai sozinho para "Sem ação necessária"/IGNORADO); a resposta é **200** com o detalhe e o campo novo **`aviso`** (string formal): *"A InfinitePay não confirmou o pagamento neste momento. O item permanece em revisão."* (ou, se a consulta falhou: *"Não foi possível consultar a InfinitePay agora. O item permanece em revisão; tente novamente mais tarde."*). `aviso` é `null` nos demais casos. **Sem mensagem técnica** ("payment_check").
- Só a ação explícita **Ignorar** (com motivo) move para ignorado.
- (O ciclo automático de busca, para um aviso `paid` que a InfinitePay não confirma, continua marcando "Sem ação necessária": protege contra aviso falso; reprocessar é a ação manual segura.)

### 5.3 Permissões (F5) 🔜
`INFINITEPAY_REVISAO_RESOLVER` **implica poder ver**: os GET `/infinitepay/revisoes`, `/infinitepay/revisoes/{id}`, `/infinitepay/fila/estado` e `/infinitepay/alertas` aceitam `INFINITEPAY_REVISAO_VISUALIZAR` **ou** `INFINITEPAY_REVISAO_RESOLVER` (alertas: também `INFINITEPAY_GERENCIAR`). Para o menu/tela, o front pode considerar `VISUALIZAR || RESOLVER`.

### 5.4 Textos (F5) ✅
Mensagens com o nome do produto usam a grafia **"InfinitePay"** (ex.: "Aviso da InfinitePay").

### 5.5 Configuração (F6, F7) 🔜
- `PUT /infinitepay/automatico`: `intervaloFilaMinutos` **fracionário** (ex.: `1.5`) → **400** `"O intervalo de verificação deve ser um número inteiro de minutos, entre 1 e 60."`.
- Com a **baixa automática ligada**, remover o **banco padrão** (`bancoPadraoId = 0`/nulo) → **400** `"Não é possível remover o banco padrão com a baixa automática ligada. Desligue a baixa automática antes."` (como já ocorre com as formas PIX/cartão).

### 5.6 Alerta `BAIXA_DESLIGADA` (item 12) 🔜
`GET /infinitepay/alertas` ganha o alerta **`BAIXA_DESLIGADA`** (substitui `AGUARDANDO_COM_INTERRUPTOR_DESLIGADO`): gravidade `ALERTA`, `rota = "/sistema/infinitepay"`, `quantidade` = links automáticos ativos + registros retidos na Cloudflare. Texto: *"A baixa automática está desligada. Os pagamentos recebidos pelos links automáticos permanecem aguardando e serão baixados somente após a ativação."* Condição: baixa **desligada** **e** integração configurada **e** (há links automáticos ativos **ou** registros retidos na Cloudflare > 0). O alerta só é devolvido a quem tem `INFINITEPAY_GERENCIAR`.

### 5.7 Pago a menor com desconto configurado (item 13) 🔜
O desconto **existe** no link (`descontoPix`/`descontoCartao`, em %). Regra: se o valor pago for **exatamente** o valor do link com o desconto do meio de pagamento (PIX ou cartão), a baixa é feita **direto**, sem revisão: principal = valor do título/link e **desconto** = diferença (o caixa recebe o valor realmente pago). Qualquer outro valor menor continua em revisão (`VALOR_MENOR`). Sem mudança de contrato; a observação da baixa informa o desconto aplicado.

---

## 6. Estado da fila com contagem na Cloudflare (T5) 🔜
`GET /infinitepay/fila/estado` mantém os campos atuais e ganha o objeto **`receptor`**:
```
receptor: { disponivel: boolean, totalRetidos, pendentes, confirmadosRetidos, maisAntigoEm, retencaoDias: 90 }   // números/datas nulos quando disponivel = false
```
Receptor indisponível ou não configurado → `disponivel = false` e demais campos `null` (**nunca erro**). Texto sugerido: "Registros retidos na Cloudflare: N (excluídos automaticamente após 90 dias)", mais a data do mais antigo. "Aguardando processamento" continua sendo só as pendentes do ERP. O Worker v1.2.0 (deploy feito pelo Maestro) fornece as contagens; até lá `receptor.disponivel` pode vir `false` com o Worker atual (campos novos ausentes → tratados como nulos).

## 7. Um único Salvar em Sistema > InfinitePay (T6) 🔜
**Decisão:** novo endpoint **atômico** `PUT /infinitepay/sistema` (permissão `INFINITEPAY_GERENCIAR`). Corpo: `{ "configuracao": <ConfiguracaoRequest>, "automatico": <AutomaticoRequest> }` (ambos opcionais; campo ausente = mantém o valor; segredos em branco mantêm o salvo). Tudo numa **única transação**: valida as duas partes (configuração primeiro, depois a baixa automática, incluindo o interruptor e as pendências) e **só grava se tudo for válido** — qualquer erro (400/409) **não grava nada**, então não há falha parcial a tratar. Resposta 200: `{ "configuracao": <ConfiguracaoResponse>, "automatico": <AutomaticoResponse> }` (os mesmos objetos dos GET). Os endpoints antigos (`PUT /infinitepay/configuracao`, `PUT /infinitepay/automatico`) **continuam funcionando**. O interruptor é gravado junto com o Salvar (validação 409 de pendências exibida formalmente).

## 8. Resumo de endpoints
| Rota | Situação |
|---|---|
| `GET /contas-receber` (totais e linhas com `valorPago`) | 🔜 |
| `GET /contas-receber/{id}` (totais e `consignacao`) | 🔜 |
| `GET /pre-acertos-consignacao/{id}/financeiro`, `GET /acertos-consignacao/{id}/financeiro` | 🔜 |
| `GET /contas-receber/clientes/{id}/consignacoes` (+ `/{consignacaoId}/titulos`, `/sem-consignacao/titulos`) | 🔜 |
| `GET /contas-pagar/clientes/{id}` | 🔜 |
| `POST /infinitepay/revisoes/{id}/reprocessar` (corpo `{confirmar:true}`, campo `aviso`) | 🔜 |
| `GET /infinitepay/revisoes`, `/{id}`, `/fila/estado`, `/alertas` (RESOLVER implica ver; 400 padronizado; `receptor`) | 🔜 |
| `PUT /infinitepay/sistema` (salvar único e atômico) | 🔜 |
