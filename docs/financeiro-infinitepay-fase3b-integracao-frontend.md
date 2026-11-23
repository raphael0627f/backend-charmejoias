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
