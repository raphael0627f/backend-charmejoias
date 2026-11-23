# Baixa automática InfinitePay (fase 3B) — contrato para o Frontend (fase 2: base do ERP)

Complementa `financeiro-link-pagamento-integracao-frontend.md` (fase 3A) e o plano `financeiro-infinitepay-fase3b-plano.md`. Migrations **V80, V81 e V82** (aditivas). Erros no formato `RestErrorMenssage` (`menssage`). Esta fase entrega a **base**; o processamento da fila e a baixa automática vêm na fase 3 e a revisão/botão "Verificar pagamento" na fase 4. **Nenhum fluxo de baixa foi ligado ainda.**

## 1. Configuração da baixa automática — `/infinitepay/automatico` (permissão `INFINITEPAY_GERENCIAR`)
`GET /infinitepay/automatico` → 200:
```json
{ "baixaAutomaticaAtiva": false, "modoAutomaticoPadrao": false, "receptorUrl": "https://pagamentos.charmejoias.com.br",
  "tokenEntradaConfigurado": true, "tokenEntradaFinal": "****1234", "segredoErpConfigurado": true, "segredoErpFinal": "****9876",
  "intervaloFilaMinutos": 2, "bancoPadrao": {"id":1,"nome":"..."}, "formaPix": {"id":10,"nome":"PIX"}, "formaCartao": {"id":11,"nome":"Cartão de crédito"},
  "prontoParaAutomatico": true, "pendencias": [], "atualizadoEm": "...", "atualizadoPor": {...} }
```
Os **segredos nunca são devolvidos** (só se existem e os 4 últimos caracteres).

`PUT /infinitepay/automatico` — todos os campos opcionais (ausente = mantém; segredo vazio = mantém):
`{ baixaAutomaticaAtiva, modoAutomaticoPadrao, receptorUrl, tokenEntrada, segredoErp, intervaloFilaMinutos, bancoPadraoId, formaPixId, formaCartaoId }` (use `0` em `bancoPadraoId`/`formaPixId`/`formaCartaoId` para limpar).
- `receptorUrl`: **HTTPS**, só o endereço (sem caminho/parâmetros), **nunca** `workers.dev` nem endereço local (a URL do webhook é gravada por link e **não pode ser editada**).
- `tokenEntrada` e `segredoErp`: 32 a 200 caracteres, letras, números, `-` e `_`; devem ser **diferentes** entre si.
- `intervaloFilaMinutos`: 1 a 60 (padrão **2**). **Não há janela de horário**: a busca roda a cada N minutos sempre que o servidor estiver ligado, e uma busca completa ao ligar (fase 3).
- `baixaAutomaticaAtiva` (**interruptor**): só liga com a configuração completa (`prontoParaAutomatico`); **desligar é sempre permitido** (parada de emergência, sem deploy). Ligada, não é possível remover peça essencial (409).
- `pendencias`: lista formal do que falta (InfiniteTag/chave, URL, token, segredo, forma PIX, forma cartão).
- Erros: 400 (valor inválido, banco/forma inexistente ou inativo), 409 (ligar sem configuração completa).

`POST /infinitepay/receptor/testar` (mesma permissão) → `{ ok, pendentes, maisAntigoEm, versao }` ou erro formal (receptor indisponível / segredo recusado). Para o botão "Testar receptor".

## 2. Link de pagamento com modo automático — `POST /contas-receber/{id}/links-pagamento`
`LinkRequest` ganhou (opcionais, aditivos): `modo` (`"MANUAL"` | `"AUTOMATICO"`; ausente = padrão da configuração `modoAutomaticoPadrao`) e `bancoId` (obrigatório no automático **se não houver banco padrão**).
- **AUTOMATICO:** exige configuração completa **e** baixa automática ligada (409 formal caso contrário), banco ativo (400) — o banco recebe o dinheiro na baixa; a **forma** vem do meio de captura (PIX / cartão), mapeada na configuração. O link é criado na InfinitePay com o `webhook` apontando ao receptor.
- **MANUAL:** idêntico à fase 3A (sem webhook).
`LinkResponse` ganhou: `modo`, `bancoId`, `bancoNome`, `transactionNsu` (preenchida quando pago automaticamente). Novo valor de `status`: **`PAGO_AUTOMATICO`** (link pago e baixado pelo webhook).
Recomendação de tela: no diálogo, opção "Baixa automática" (seletor de banco; avisar que a URL do receptor não muda depois de gerado o link). Se `GET /infinitepay/automatico` informar `prontoParaAutomatico=false` ou `baixaAutomaticaAtiva=false`, desabilitar a opção com tooltip formal.

## 3. Recebimento — novos campos e origem
Em `recebimentos[]` do detalhe do título (`ContaReceberDetalheDTO.Recebimento`): `origem` agora pode ser `BANCO`, `CREDITO_CLIENTE` ou **`INFINITEPAY`** (baixa automática); novos campos **`valorTaxaCliente`** (taxa paga pelo cliente, informativa) e **`automatico`** (boolean).
- Recebimento `INFINITEPAY` **entra no caixa** como o de banco (banco e forma preenchidos); `valorTaxaCliente` **não** entra no caixa nem em relatórios (a baixa é pelo valor do título).
- **Usuário nulo:** a baixa automática não tem usuário (`createdBy: null`). **A tela deve exibir "Automático (InfinitePay)"**, nunca célula em branco (quando `automatico = true`).
- Texto de ajuda sugerido para a taxa: "Diferença entre o valor do título e o valor pago pelo cliente (parcelamento com taxa repassada ao cliente). Não faz parte do valor recebido do título."
- Filtros de recebimento (período/banco) passam a considerar também `INFINITEPAY` (tudo que não é crédito do cliente).

## 4. Permissões novas (perfil ADMIN, V80)
`INFINITEPAY_REVISAO_VISUALIZAR` e `INFINITEPAY_REVISAO_RESOLVER` (telas da fila de revisão chegam na fase 4; as permissões já existem na tela de perfis).

## 5. Pontos incertos (documentação oficial da InfinitePay)
O formato real do webhook (método, Content-Type, eventos além de `paid`) e a política de reenvio **não são documentados**; o ERP guarda o payload bruto e envia o inesperado para revisão (fases 3/4). Nenhum link real foi gerado nesta fase.

---

# Complemento (fase 3/4) — contratos que o Frontend precisa

**Situação de cada endpoint:** ✅ = já no backend (commit da F3); 🔜 = (**F4 já implementada no backend, commits locais da branch; vale o contrato abaixo**). Erros seguem `RestErrorMenssage` (`{"menssage": "..."}`, com dois "s"); sem prefixo `/api`.

## 6. Sincronização e saúde do receptor (tela Sistema > InfinitePay)
- ✅ `POST /infinitepay/fila/sincronizar` — "Verificar agora". Permissão: `INFINITEPAY_GERENCIAR` **ou** `INFINITEPAY_REVISAO_RESOLVER`. Corpo: nenhum. Busca a fila do receptor e processa tudo (mesma rotina e **mesma trava** do ciclo agendado; nunca em paralelo). Espera até 30 s. Resposta 200:
  `{ buscados, ingeridos, baixados, revisao, ignorados, erros, valorBaixado, recebimentos: [ids], mensagem, emAndamento }`.
  `emAndamento = true` = ainda rodando (mensagem "Verificação em andamento…"): mostrar aviso e permitir consultar depois; **não** é erro. `erros > 0` com `mensagem` = falha formal (receptor fora do ar etc.): exibir a `mensagem`. Receptor não configurado: 200 com `mensagem = "Receptor de pagamentos não configurado."` e contadores zerados.
- ✅ `GET /infinitepay/fila/estado` — permissão `INFINITEPAY_GERENCIAR` ou `INFINITEPAY_REVISAO_VISUALIZAR`. Resposta: `{ ultimaExecucao, ultimoSucesso, ultimoErro, aguardandoProcessamento, emRevisao }` (datas ISO; `ultimoErro` nulo = último ciclo ok). Sugestão: exibir "Última verificação: …" e, se `ultimoErro`, aviso vermelho.
- ✅ `POST /infinitepay/receptor/testar` (já na F2) — "Testar receptor" (chama `/saude` do receptor; resposta `ReceptorSaude`: `ok`, `pendentes`, `versao`, `mensagem`).
- O agendamento é automático (a cada `intervaloFilaMinutos`, padrão 2, sempre que o servidor estiver ligado, **sem janela de horário**; busca completa ao ligar). Não há endpoint para agendar.

## 7. Verificar pagamento (botão no título, pré-acerto e acerto) — 🔜 F4
`POST /infinitepay/verificar-pagamento` — permissão `CONTA_RECEBER_RECEBER`. Limite: **1 chamada a cada 10 s por usuário** (excesso → **429** `{"menssage":"Aguarde alguns segundos para verificar novamente."}`).
Request: `{ "tituloId": 123 }` (no pré-acerto/acerto o Frontend envia o `id` de cada título com `linkAutomaticoAtivo`; uma chamada por título).
Só vale se o título tem **link automático ativo**; senão 400 ("Este título não possui link de pagamento automático ativo.").
Faz a busca da fila (mesma trava do ciclo agendado) e processa o que chegou para o título. Resposta 200:
`{ "resultado": "<ESTADO>", "mensagem": "...", "titulo": <detalhe atualizado do título>, "recebimentoId": 99|null }`

| resultado | Significado | Tela |
|---|---|---|
| `PAGAMENTO_REGISTRADO` | Pago e baixado agora (ou já baixado) | Recarregar o título; toast "Pagamento confirmado e registrado." |
| `AINDA_NAO_PAGO` | Nada confirmado na InfinitePay | "Pagamento ainda não identificado." |
| `EM_REVISAO` | Há pagamento que exige decisão do administrador (valor menor, duplicidade etc.) | Aviso "Pagamento recebido aguardando revisão" (link para a revisão se o usuário tiver `INFINITEPAY_REVISAO_VISUALIZAR`) |
| `EM_ANDAMENTO` | Ciclo já em execução (trava) | "Verificação em andamento, tente em instantes." |
| `INDISPONIVEL` | InfinitePay/receptor fora do ar | Exibir a mensagem formal da API; tentar depois |

## 8. Fila de revisão — 🔜 F4 (`INFINITEPAY_REVISAO_VISUALIZAR` lê; `INFINITEPAY_REVISAO_RESOLVER` altera)
- `GET /infinitepay/revisoes` — filtros opcionais: `status` (padrão `REVISAO`; aceita qualquer **StatusEvento**), `motivo` (código), `de`/`ate` (`yyyy-MM-dd`, sobre `criadoEm`), `tituloId`, `clienteId`, `page`, `size` (padrão 20), `sort` (padrão `criadoEm,desc`). Resposta paginada (`content[]`, `totalElements`…). Item (`RevisaoResumo`): `{ id, criadoEm, status, motivoCodigo, motivo, evento, orderNsu, transactionNsu, tituloId, tituloNumero, clienteNome, valorLink, amountCentavos, paidAmountCentavos, diferencaCentavos, captureMethod, meio (rótulo), tentativas, recebimentoId, resolvidoEm, resolvidoPorNome, resolucao }`.
- `GET /infinitepay/revisoes/{id}` — detalhe: tudo acima + **`payloadBruto`** (texto, ≤ 16 KB; exibir em bloco monoespaçado com botão copiar; pode conter dados do pagador, por isso a tela é restrita a `INFINITEPAY_REVISAO_VISUALIZAR`) + `acoesPermitidas` (subconjunto de `["APLICAR","IGNORAR","REPROCESSAR"]`, conforme status e motivo) + `exigeBancoForma` (boolean: `true` em `FORMA_NAO_MAPEADA` e `BANCO_DESATIVADO`; o Frontend abre o diálogo de banco/forma quando for `true`) + `titulo` = `{ id, numero, situacao, valor, valorRecebido, saldoEmAberto, clienteId, clienteNome }` (nulo se o link for desconhecido) + `link` = `{ id, modo, status, valor, bancoId, bancoNome, transactionNsu }` (nulo se desconhecido). Em `RevisaoResumo`, `tituloId`, `tituloNumero` e `clienteNome` são os mesmos campos achatados.
- `POST /infinitepay/revisoes/{id}/aplicar` — "Aplicar no título". **Sem corpo na maioria dos casos**: o backend usa **o banco do link, a forma do meio de captura do próprio pagamento e a data do pagamento**; valor principal = valor do link; o que exceder (`paid_amount − amount`) vai para `valorTaxaCliente`. **Casos que exigem dados do usuário** (a API responde 400 com mensagem formal; o Frontend já sabe pelo `exigeBancoForma` do detalhe, abre o diálogo e envia com `{ "bancoId": n, "formaPagamentoId": n }`): `FORMA_NAO_MAPEADA` (forma ausente na configuração) e `BANCO_DESATIVADO`. Para `VALOR_MENOR` o aplicar exige confirmação explícita no corpo `{ "confirmarValorMenor": true }` (registra só o `paid_amount` como principal; o restante do título continua em aberto). `DUPLICIDADE` e `TITULO_CANCELADO` **não** têm "Aplicar" (só Ignorar): nenhum crédito automático é feito. Resposta 200: `RevisaoDetalhe` atualizado (status `RESOLVIDO_APLICADO`, `recebimentoId`). O recebimento resultante é `INFINITEPAY` (`automatico = true`) e continua **sem usuário**; quem resolveu fica em `resolvidoPor*` da revisão.
- `POST /infinitepay/revisoes/{id}/ignorar` — corpo obrigatório `{ "motivo": "texto de 5 a 500 caracteres" }`; status → `RESOLVIDO_IGNORADO`. 400 se vazio ou curto.
- `POST /infinitepay/revisoes/{id}/reprocessar` — sem corpo; reenfileira o evento (status → `RECEBIDO`) e processa na hora (ex.: depois de corrigir a configuração). Resposta: `RevisaoDetalhe`. Só para `REVISAO`/`ERRO_TEMPORARIO`.
- Erros comuns: 404 revisão inexistente; 409 já resolvida ("Esta revisão já foi resolvida."); 400 com `{"menssage": "..."}` (só o campo `menssage`; **não há `motivoCodigo` no erro**). O Frontend decide o diálogo de banco/forma por `motivoCodigo`/`exigeBancoForma` do **detalhe da própria revisão**, não pelo erro. As mensagens de erro de Aplicar são formais e autossuficientes, por exemplo: "A forma de pagamento do meio utilizado não está configurada. Informe banco e forma de pagamento.", "O banco de destino do link está desativado. Informe outro banco.", "Confirme a baixa do valor pago menor que o cobrado.", "Este pagamento não pode ser aplicado automaticamente (pagamento em duplicidade). Ignore-o com um motivo.", "Esta revisão já foi resolvida." (409).

## 9. Alertas — 🔜 F4
`GET /infinitepay/alertas` — permissão `INFINITEPAY_REVISAO_VISUALIZAR` ou `INFINITEPAY_GERENCIAR`. Alerta **só dentro do sistema** (sem e-mail). Resposta: `{ "total": n, "alertas": [ { "codigo", "gravidade": "INFO"|"ALERTA"|"ERRO", "mensagem", "quantidade", "rota" } ] }`. O campo `rota` é sempre `"/contas-receber/revisao-infinitepay"` para alertas de revisão/fila e `"/sistema/infinitepay"` para os de configuração/receptor (`RECEPTOR_INDISPONIVEL`, `AGUARDANDO_COM_INTERRUPTOR_DESLIGADO`). Códigos: `REVISOES_PENDENTES` (itens em `REVISAO`), `FILA_COM_ERRO` (`ERRO_TEMPORARIO` acumulado), `RECEPTOR_INDISPONIVEL` (último ciclo falhou), `ITENS_SEM_CONFIRMACAO` (pendentes no receptor há mais de 1 h), `AGUARDANDO_COM_INTERRUPTOR_DESLIGADO` (eventos `AGUARDANDO` com a baixa automática desligada). Sugestão: badge no menu + faixa na tela Sistema > InfinitePay; atualizar a cada 60 s só com a aba visível.

## 10. Pré-acerto e acerto (`DocumentoFinanceiroDTOs.Resumo`) — 🔜 F4 (campos aditivos)
- Em **cada `Titulo`** de `titulos[]`: `linkAutomaticoAtivo` (boolean: título com link AUTOMATICO e status ATIVO; habilita o botão "Verificar pagamento"), `valorTaxaCliente` (soma das taxas de cliente dos recebimentos do título; 0 se nenhuma) e `pagoPeloLink` (boolean: algum recebimento do título veio de baixa automática).
- No **`Resumo`** (agregado): `existeLinkAutomaticoAtivo` (boolean), `valorTaxaClienteTotal` e `pagoPeloLink`.
- `valorRecebido` e `saldoATitular` **não** incluem a taxa do cliente (informativa). Exibir à parte: "Taxa paga pelo cliente: R$ X (fora do valor recebido)".

## 11. Enums e rótulos
**StatusEvento** (`status` da revisão/evento): `AGUARDANDO` — "Aguardando ligar a baixa automática"; `RECEBIDO` — "Recebido, na fila"; `ERRO_TEMPORARIO` — "Tentando novamente"; `REVISAO` — "Em revisão"; `PROCESSADO` — "Baixado automaticamente"; `IGNORADO` — "Sem ação necessária"; (F4) `RESOLVIDO_APLICADO` — "Resolvido: aplicado ao título"; `RESOLVIDO_IGNORADO` — "Resolvido: ignorado".
**motivoCodigo** (rótulo — quando): `PAYLOAD_INESPERADO` "Dados inesperados" (JSON ou campos ausentes; o bruto fica guardado); `EVENTO_INFORMATIVO` "Aviso da InfinitePay" (`refunded`, `canceled`, `payment_failed`; nenhuma ação automática); `LINK_DESCONHECIDO` "Link não encontrado"; `LINK_MANUAL` "Link de baixa manual"; `TITULO_CANCELADO` "Pago após cancelamento do título"; `DUPLICIDADE` "Pagamento em duplicidade" (título já quitado ou link já não ativo); `VALOR_DIVERGENTE` "Valor diverge do link"; `VALOR_MENOR` "Valor pago menor que o cobrado"; `SALDO_INSUFICIENTE` "Saldo do título menor que o pago"; `FORMA_NAO_MAPEADA` "Meio de pagamento sem forma configurada"; `BANCO_DESATIVADO` "Banco do link desativado"; `CONFIRMACAO_FALHOU` "Não foi possível confirmar na InfinitePay (10 tentativas)"; `ERRO_AO_BAIXAR` "Erro ao registrar a baixa". O texto detalhado já vem pronto em `motivo`: exibir o rótulo do código e, abaixo, o `motivo`.
**Meio (`captureMethod`)**: `pix` → "PIX"; `credit_card` → "Cartão de crédito"; outro → exibir o valor bruto.
**Status do link**: `ATIVO`, `PAGO_MANUAL`, `PAGO_AUTOMATICO` — "Pago (automático)", `NAO_UTILIZAR`, `ENCERRADO_TITULO_RECEBIDO`, `ENCERRADO_TITULO_CANCELADO` (os já existentes mantêm os rótulos).

## 12. A observação da baixa vem pronta do backend
A `observacao` do recebimento automático é **montada pelo backend** (ex.: "Pago pelo link InfinitePay (baixa automática), cartão 3x. Taxa paga pelo cliente: R$ 10,00. NSU abc123."). O Frontend **apenas exibe**; não monta nem edita o texto. O mesmo vale para `motivo` e `mensagem` das respostas acima. Nos eventos do título (`RECEBIMENTO_REGISTRADO`, `LINK_PAGAMENTO_PAGO_AUTOMATICAMENTE`) o `usuario` vem nulo: exibir "Automático (InfinitePay)".

## 13. Correções de premissas
Não há janela comercial (a busca roda sempre que o servidor estiver ligado) e não há usuário de baixa (`usuario_baixa_id` não existe): o recebimento automático tem usuário nulo e `automatico = true`.

## 14. `GET /infinitepay/padroes` ganhou campos da baixa automática (✅)
Permissões inalteradas (`INFINITEPAY_GERENCIAR`, `CONTA_RECEBER_VISUALIZAR/GERENCIAR/RECEBER`); sem segredos. Novos campos: `baixaAutomaticaAtiva` (boolean), `prontoParaAutomatico` (boolean: configuração do receptor completa), `modoAutomaticoPadrao` (boolean) e `bancoPadrao` (`{ id, nome }` ou `null`). Quem gera link sem `INFINITEPAY_GERENCIAR` usa só este endpoint (não precisa de `GET /infinitepay/automatico`).

## 15. Ajustes finais da F4 (implementação)
- `GET /infinitepay/revisoes`: o filtro `clienteId` é **UUID** (id do cliente); `tituloId` é numérico. `RevisaoResumo`/`RevisaoDetalhe` ganharam `motivoRotulo` (rótulo do código). O **detalhe é achatado** (todos os campos do resumo no nível raiz + `payloadBruto`, `acoesPermitidas`, `exigeBancoForma`, `titulo`, `link`). `titulo.clienteId` é UUID.
- Estados `RESOLVIDO_APLICADO` / `RESOLVIDO_IGNORADO` são derivados no backend (evento já resolvido); o filtro `status` aceita os dois.
- `acoesPermitidas` só vem preenchida enquanto a revisão está aberta (`REVISAO`/`ERRO_TEMPORARIO`); `APLICAR` apenas para `VALOR_MENOR`, `FORMA_NAO_MAPEADA`, `BANCO_DESATIVADO` e `ERRO_AO_BAIXAR`.
- `GET /infinitepay/fila/estado` ganhou `itensNaoConfirmados`.
- Migration nova **V83** (coluna `motivo_codigo` em `infinitepay_evento`, aditiva).
- `POST /infinitepay/verificar-pagamento` com a baixa automática desligada responde 409 ("A baixa automática está desligada em Sistema > InfinitePay.").
