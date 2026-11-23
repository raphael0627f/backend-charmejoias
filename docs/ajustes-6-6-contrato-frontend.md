# Ajustes 6.6 — contrato do backend para o Frontend

Branch `feat/ajustes-6-6` (backend). Erros seguem `RestErrorMenssage` (`{"menssage": "..."}`, dois "s"); sem prefixo `/api`. Todos os campos novos são **aditivos**: nada do que existe foi removido ou renomeado. Valores monetários em reais (`BigDecimal`, 2 casas) e datas ISO.
Legenda: ✅ implementado e validado no backend (branch `feat/ajustes-6-6`) · 🔜 pendente. Atualizado após a implementação: onde difere da primeira versão do contrato, vale este texto.

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
| 5 | Situação dos clientes (`/contas-receber/clientes/...situacao`) | `recebidoNoPeriodo` já é o **valor real** (`valor_total`, não estornado) e **exclui crédito** (é caixa do período); em aberto/vencido por principal | **Sem alteração** (já era o total real; o crédito do cliente fica de fora de propósito, pois é caixa do período). Em aberto/vencido inalterados |
| 6 | Aba Financeiro do cliente (nova) | não existia por consignação | `valorPago` = total real + indicador de crédito (ver seção 3) |
| 7 | Recebimento em lote (`ResultadoLote.totalRecebido`) | soma do **valor recebido de cada item** | inalterado (é o resultado da operação, já em total real do recebimento) |
| 8 | Caixa / movimentações bancárias | `valor_total` do recebimento | inalterado (já era o total real; taxa do cliente nunca entra) |
| 9 | Relatórios, exportações e dashboard | — | **Não existe relatório/exportação/dashboard de Contas a Receber no backend** (busca no código); nada a alterar |

---

## 1. Título (detalhe) — totais e origem na consignação ✅
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

## 2. Lista de Contas a Receber ✅
- `totais` (`GET /contas-receber`): `{ quantidade, valorTotal, valorRecebido, valorAberto, valorVencido, valorPago, valorCreditoAplicado }`. **Card "Recebido" → `valorPago`** (tooltip formal: "Valor efetivamente recebido, incluindo juros e multa e descontando descontos. Inclui crédito do cliente aplicado, quando houver."). Mostrar o aviso de crédito se `valorCreditoAplicado > 0`.
- Cada linha: `+ valorPago`, `+ valorCreditoAplicado`.

## 3. Pré-acerto e acerto — resumo financeiro (T2) ✅
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

## 4. Aba Financeiro do cliente (T3) ✅
Permissão: a mesma de `GET /contas-receber/clientes/{id}/situacao` (`CONTA_RECEBER_VISUALIZAR`/`GERENCIAR`/`RECEBER`).

**Regra do documento por consignação (confirmada no código):** a consignação tem **no máximo um pré-acerto** (índice único) e **no máximo um acerto**. Se existe **acerto**, vale **só o acerto** (ele já contém todos os produtos acertados da consignação); senão, o **pré-acerto**. Nunca se soma pré-acerto + acerto (não há contagem em dobro). Consignação sem acerto nem pré-acerto não tem valores e só aparece se tiver títulos. O resumo do conjunto (`resumo`) não soma vendido/comissão/a pagar das consignações com selo `CANCELADA`.

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
                selo, quantidadeTitulos, quantidadeCancelados,
                valorTitulado }                                                   // valorTitulado = soma dos títulos não cancelados (para comparar com valorAPagar)
```
**Selo** (`selo`), nesta ordem de precedência: `CANCELADA` (consignação cancelada ou todos os títulos cancelados) → `SEM_VALOR_A_PAGAR` (documento com `valorAPagar = 0` **e** sem saldo em aberto nem pagamento nos títulos; se houver saldo ou pagamento, vale a situação real dos títulos) → `SEM_TITULO` (nenhum título; documento com valor a pagar) → `QUITADA` (em aberto = 0) → `PARCIAL_VENCIDA` / `VENCIDA` (há saldo vencido; "parcial" quando já houve pagamento) → `PARCIAL` / `EM_ABERTO`. Rótulos sugeridos: Cancelada, Sem valor a pagar, Sem título, Quitada, Parcial vencida, Vencida (com `vencimentoMaisAntigo`), Parcial, Em aberto.
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

### 5.1 Padronização de erros 400 (F3, F4) ✅
- `GET /infinitepay/revisoes?sort=...` com campo inválido → **400** `{"menssage": "Campo de ordenação inválido."}` (sem stack trace). Campos aceitos: `id`, `criadoEm`, `status`, `motivoCodigo`, `transactionNsu`, `orderNsu`, `tentativas`, `resolvidoEm`. O mesmo tratamento vale para **todas** as listas paginadas do sistema (ordenação por campo inexistente → 400 formal).
- `de > ate` → 400 `"A data inicial não pode ser posterior à data final."`; `motivo` inválido → 400 `"Motivo de revisão inválido."`; `status`/`clienteId` inválidos continuam 400.

### 5.2 Reprocessar (F1 — MÉDIO) ✅
`POST /infinitepay/revisoes/{id}/reprocessar` agora **exige confirmação no corpo**: `{ "confirmar": true }` (sem isso → 400 `"Confirme o reprocessamento para continuar."`). Comportamento:
- Se a InfinitePay **confirmar** o pagamento → baixa (como hoje) e o item sai da revisão.
- Se a InfinitePay **não confirmar** ou a consulta falhar → o item **permanece em `REVISAO`** (nunca vai sozinho para "Sem ação necessária"/IGNORADO); a resposta é **200** com o detalhe e o campo novo **`aviso`** (string formal): *"A InfinitePay não confirmou o pagamento neste momento. O item permanece em revisão."* (ou, se a consulta falhou: *"Não foi possível consultar a InfinitePay agora. O item permanece em revisão; tente novamente mais tarde."*). `aviso` é `null` nos demais casos. **Sem mensagem técnica** ("payment_check").
- Só a ação explícita **Ignorar** (com motivo) move para ignorado.
- (O ciclo automático de busca, para um aviso `paid` que a InfinitePay não confirma, continua marcando "Sem ação necessária": protege contra aviso falso; reprocessar é a ação manual segura.)

### 5.3 Permissões (F5) ✅
`INFINITEPAY_REVISAO_RESOLVER` **implica poder ver**: os GET `/infinitepay/revisoes`, `/infinitepay/revisoes/{id}`, `/infinitepay/fila/estado` e `/infinitepay/alertas` aceitam `INFINITEPAY_REVISAO_VISUALIZAR` **ou** `INFINITEPAY_REVISAO_RESOLVER` (alertas: também `INFINITEPAY_GERENCIAR`). Para o menu/tela, o front pode considerar `VISUALIZAR || RESOLVER`.

### 5.4 Textos (F5) ✅
Mensagens com o nome do produto usam a grafia **"InfinitePay"** (ex.: "Aviso da InfinitePay").

### 5.5 Configuração (F6, F7) ✅
- `PUT /infinitepay/automatico`: `intervaloFilaMinutos` **fracionário** (ex.: `1.5`) → **400** `"O intervalo de verificação deve ser um número inteiro de minutos, entre 1 e 60."`.
- Com a **baixa automática ligada**, remover o **banco padrão** (`bancoPadraoId = 0`/nulo) → **400** `"Não é possível remover o banco padrão com a baixa automática ligada. Desligue a baixa automática antes."` (como já ocorre com as formas PIX/cartão).

### 5.6 Alerta `BAIXA_DESLIGADA` (item 12) ✅
`GET /infinitepay/alertas` ganha o alerta **`BAIXA_DESLIGADA`** (substitui `AGUARDANDO_COM_INTERRUPTOR_DESLIGADO`): gravidade `ALERTA`, `rota = "/sistema/infinitepay"`, `quantidade` = links automáticos ativos + pagamentos aguardando no ERP + avisos pendentes (sem confirmação) retidos na Cloudflare. Texto: *"A baixa automática está desligada. Os pagamentos recebidos pelos links automáticos permanecem aguardando e serão baixados somente após a ativação."* Condição: baixa **desligada** **e** integração configurada (InfiniteTag + chave) **e** (há links automáticos ativos **ou** pagamentos aguardando no ERP **ou** avisos pendentes no receptor > 0). Avisos já confirmados (ack) e apenas retidos por 90 dias não disparam o alerta. O alerta só é devolvido a quem tem `INFINITEPAY_GERENCIAR`.

### 5.7 Pago a menor com desconto configurado (item 13) ✅
O desconto **existe** no link (`descontoPix`/`descontoCartao`, em %). Regra: se o valor pago for **exatamente** o valor do link com o desconto do meio de pagamento (PIX ou cartão; cálculo em centavos, arredondando meio centavo para cima), a baixa é feita **direto**, sem revisão: principal = valor do título/link e **desconto** = diferença (o caixa recebe o valor realmente pago). Qualquer outro valor menor continua em revisão (`VALOR_MENOR`). Sem mudança de contrato; a observação da baixa informa o desconto aplicado.

---

## 6. Estado da fila com contagem na Cloudflare (T5) ✅
`GET /infinitepay/fila/estado` mantém os campos atuais e ganha o objeto **`receptor`**:
```
receptor: { disponivel: boolean, totalRetidos, pendentes, confirmadosRetidos, maisAntigoEm, maisAntigoRetidoEm, retencaoDias }
// maisAntigoEm = aviso pendente mais antigo; maisAntigoRetidoEm = registro retido mais antigo (inclui confirmados); campos nulos quando disponivel = false
```
Receptor indisponível ou não configurado → `disponivel = false` e demais campos `null` (**nunca erro**). Texto sugerido: "Registros retidos na Cloudflare: N (excluídos automaticamente após 90 dias)", mais a data do mais antigo. "Aguardando processamento" continua sendo só as pendentes do ERP. O Worker v1.2.0 (deploy feito pelo Maestro) fornece `totalRetidos`, `confirmadosRetidos`, `maisAntigoRetidoEm` e `retencaoDias`; com o Worker atual (1.1.0) `disponivel` vem `true`, `pendentes` e `maisAntigoEm` preenchidos e os campos novos `null` (o front deve tratar `null` como "não informado"). O estado do receptor é guardado por 30 segundos no servidor.

## 7. Um único Salvar em Sistema > InfinitePay (T6) ✅
**Decisão:** novo endpoint **atômico** `PUT /infinitepay/sistema` (permissão `INFINITEPAY_GERENCIAR`). Corpo: `{ "configuracao": <ConfiguracaoRequest>, "automatico": <AutomaticoRequest> }` (ambos opcionais; campo ausente = mantém o valor; segredos em branco mantêm o salvo). Tudo numa **única transação**: valida as duas partes (configuração primeiro, depois a baixa automática, incluindo o interruptor e as pendências) e **só grava se tudo for válido** — qualquer erro (400/409) **não grava nada**, então não há falha parcial a tratar. Resposta 200: `{ "configuracao": <ConfiguracaoResponse>, "automatico": <AutomaticoResponse> }` (os mesmos objetos dos GET). Os endpoints antigos (`PUT /infinitepay/configuracao`, `PUT /infinitepay/automatico`) **continuam funcionando**. O interruptor é gravado junto com o Salvar (validação 409 de pendências exibida formalmente). Corpo vazio (`{}` ou sem as duas partes) → 400 `"Informe os dados da configuração."`.

## 8. Resumo de endpoints
| Rota | Situação |
|---|---|
| `GET /contas-receber` (totais e linhas com `valorPago`) | ✅ |
| `GET /contas-receber/{id}` (totais e `consignacao`) | ✅ |
| `GET /pre-acertos-consignacao/{id}/financeiro`, `GET /acertos-consignacao/{id}/financeiro` | ✅ |
| `GET /contas-receber/clientes/{id}/consignacoes` (+ `/{consignacaoId}/titulos`, `/sem-consignacao/titulos`) | ✅ |
| `GET /contas-pagar/clientes/{id}` | ✅ |
| `POST /infinitepay/revisoes/{id}/reprocessar` (corpo `{confirmar:true}`, campo `aviso`) | ✅ |
| `GET /infinitepay/revisoes`, `/{id}`, `/fila/estado`, `/alertas` (RESOLVER implica ver; 400 padronizado; `receptor`) | ✅ |
| `PUT /infinitepay/sistema` (salvar único e atômico) | ✅ |

---

## 9. Correções do QA (atualização)

### 9.1 Reprocessar: resultado explícito (M1) ✅
A resposta de `POST /infinitepay/revisoes/{id}/reprocessar` (o detalhe da revisão) ganhou **`baixado`** (boolean: `true` somente quando existe recebimento, isto é, `recebimentoId != null`) e o `aviso` passa a cobrir também o caso "a InfinitePay confirmou, mas não houve baixa":
- `FORMA_NAO_MAPEADA`: *"O pagamento foi confirmado, mas o meio de pagamento não possui forma configurada. Utilize Aplicar informando banco e forma."*
- `BANCO_DESATIVADO`: *"O pagamento foi confirmado, mas o banco de destino do link está desativado. Utilize Aplicar informando outro banco e a forma de pagamento."*
- demais motivos que permanecem em revisão: *"O pagamento foi confirmado, mas não foi baixado automaticamente (<motivo>). O item permanece em revisão: confira o detalhe e utilize Aplicar ou Ignorar."*
- InfinitePay não confirma / consulta falha: os avisos da seção 5.2 (inalterados).
**Regra para a tela:** exibir "o pagamento foi baixado" **somente** se `baixado = true`; se `aviso` vier preenchido, exibi-lo; o status resultante está em `status` (`REVISAO` ou `PROCESSADO`).

### 9.2 Filtro por número do título (M2) ✅
`GET /infinitepay/revisoes` aceita **`tituloNumero`** (o número visível ao usuário, ex.: 1222) além de `tituloId` (id interno, mantido). **A tela deve enviar o número digitado como `tituloNumero`.** As demais listas com filtro de título não existem no backend (nenhuma outra recebe `tituloId`).

### 9.3 Ordenação inválida em todas as listas (A2) ✅
`GET /contas-receber`, `GET /contas-pagar` e `GET /contas-receber/clientes/situacao` aceitam também `sort=campo[,asc|desc]` (alias de `ordenar`/`direcao`); campo inexistente → **400** `"Campo de ordenação inválido."`. Os demais endpoints paginados (Spring) já respondiam 400 com o mesmo texto.

### 9.4 Selo "parcial" (A4) ✅
`PARCIAL` e `PARCIAL_VENCIDA` (nas consignações e no grupo "Sem consignação") só ocorrem quando algum título está **recebido parcialmente**. Um título quitado ao lado de outro vencido sem recebimento parcial resulta em `VENCIDA` (ou `EM_ABERTO`).

### 9.5 Demais correções do QA
- **Texto "InfinitePay"** (A1): a mensagem do Aplicar em item informativo passa a manter a grafia "InfinitePay" (antes saía em minúsculas).
- **Evento `failed`** (A5): tratado como `EVENTO_INFORMATIVO`, igual a `payment_failed` (nada é baixado).
- **Verificar pagamento (B2/C7)**: `POST /infinitepay/verificar-pagamento` responde `EM_ANDAMENTO` com *"A confirmação do pagamento na InfinitePay está em nova tentativa. Consulte novamente em instantes."* quando algum aviso do título está em `ERRO_TEMPORARIO` (consulta à InfinitePay falhou e será repetida); `INDISPONIVEL` somente quando a falha é ao buscar a fila do receptor, sem aviso do título em nova tentativa.
- **Item reprocessado com sucesso (F-D)**: fica `status = PROCESSADO` ("Baixado automaticamente"), com `baixado = true` e `recebimentoId` preenchido. Não existe status novo "reprocessado".
- **Devolução (O1)**: o vencimento informado em `POST /excedentes-acerto/{id}/destinar` (`vencimento`) é gravado integralmente no título a pagar; sem informar, usa hoje. O backend não altera a data (teste incluso); a diferença vista na tela (20/12 → 23/12) deve ser verificada no envio da data pelo front (fuso/formatação).
- **Aviso duplicado do mesmo pagamento (C10)**: o receptor guarda avisos sem `transaction_nsu` com a chave `sha256:<resumo>` (evento `desconhecido` se o corpo não traz `event`/`type`). No ERP: (1) esses avisos são processados por último no ciclo; (2) se o corpo tem `order_nsu` mas não tem `transaction_nsu` e o link já está pago, o aviso é marcado `IGNORADO` ("Aviso sem identificador de transação referente a um pagamento já baixado.") — sem baixa duplicada, sem revisão, sem erro; (3) se o link não estiver pago, segue para revisão `PAYLOAD_INESPERADO`, como antes (nunca baixa sem `transaction_nsu`). A baixa em si é idempotente pelo `transaction_nsu` e pelo estado do link/título. No Worker, evento ausente = `desconhecido` (guarda qualquer formato; a classificação é do ERP: com todos os campos de pagamento é tratado como pagamento e confirmado no `payment_check`).

---

## 10. Rodada 2 do QA

### 10.1 Formato real do webhook e corrida "settled" × "paid" (R1) ✅
Formato real observado no Worker real: o campo do tipo é **`event_type`** (não `event`); a InfinitePay envia dois avisos do mesmo pagamento, com ~1 s de diferença:
1. `event_type = "settled"`, **sem** `transaction_nsu`, `paid_amount` e `capture_method` (o Worker guarda com chave `sha256:<resumo>` e evento `desconhecido`);
2. `event_type = "paid"`, completo, com `transaction_nsu` (é este que baixa, após o `payment_check`).

Regras no ERP:
- O tipo do evento é lido de `event`, `event_type`, `type` ou `evento` (o Worker v1.2.0 também; **o campo do Worker é só rótulo, o ERP classifica pelo corpo**).
- Aviso **sem `transaction_nsu`** com `order_nsu`:
  - link **já pago** → `IGNORADO` ("Aviso sem identificador de transação referente a um pagamento já baixado.");
  - link **não pago**, dentro da janela (**15 minutos** desde que o ERP guardou o aviso; propriedade `app.infinitepay.aviso-sem-transacao-janela-segundos`, padrão 900) → **continua aguardando**: não vira revisão, não baixa; é reavaliado a cada ciclo;
  - link não pago após a janela → `REVISAO` `PAYLOAD_INESPERADO` ("Aviso de pagamento sem identificador de transação e sem aviso completo posterior...") — sinal de que o "paid" se perdeu.
- Esse aviso **é confirmado (ack) no receptor** assim que guardado no ERP (não há reentrega); a espera acontece no ERP.
- "Aguardando processamento" (`/infinitepay/fila/estado`) **não conta** esses avisos em espera. Avisos `sha256:` são processados por último em cada ciclo.
- Aviso `sha256:` de link não pago **nunca** baixa (mantido por segurança).
Cenários validados: (1) settled+paid na mesma busca; (2) settled sozinho e paid depois; (3) settled sozinho e paid nunca (após a janela → revisão). Sem impacto de API para o front.

### 10.2 Ordenação inválida nas listas de ordem fixa (R2) ✅
`GET /bancos`, `GET /contas-receber/clientes/{id}/situacao`, `GET /contas-receber/clientes/{id}/consignacoes`, `.../consignacoes/{cid}/titulos`, `.../sem-consignacao/titulos` e `GET /contas-pagar/clientes/{id}` não ordenam por parâmetro: qualquer `sort` informado → **400** `"Campo de ordenação inválido."`.

### 10.3 Simulador (QA)
`POST http://127.0.0.1:9099/__pagamento` aceita `"formato":"real"`: emite o `settled` (sem `transaction_nsu`) e depois o `paid` (`event_type`), com `atraso_paid_segundos` (padrão 1), `settled` (padrão true) e `paid_webhook` (padrão true; `false` simula o paid perdido). Exemplo: `{"formato":"real","order_nsu":"<orderNsu>","transaction_nsu":"QA-REAL-1","amount":1500,"atraso_paid_segundos":30}`.
