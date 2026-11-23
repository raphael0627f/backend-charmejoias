# Fase 3B — Baixa automática por webhook da InfinitePay (PLANO DETALHADO, sem implementação)

Status: planejamento. Versão de 2026-11-23, após as respostas do usuário às 9 perguntas. Nenhum código de produção, migration ou chamada à InfinitePay foi feito. Última migration existente: V79; as previstas (V80 a V82) dependem de aviso e aprovação prévios e de **backup do banco antes de cada deploy**.

## 1. Decisões do usuário (2026-11-23) — seção 11 respondida

| # | Decisão |
|---|---------|
| 1 | Plataforma **Cloudflare** (Worker + D1). O domínio `charmejoias.com.br` já está na Cloudflare (plano Free, DNS ativo). |
| 2 | Receptor em domínio próprio: **`pagamentos.charmejoias.com.br`** (Custom Domain do Worker; demais entradas de DNS sem proxy). `webhook_url` é gravado **por link e não pode ser editado**: **nunca usar `workers.dev` em link real**. |
| 3 | **Taxa paga pelo cliente** (`paid_amount > amount`): baixa **somente o valor do título** (`amount`). A taxa (`paid_amount − amount`) é **gravada** (`valor_taxa_cliente`, V82) e **visível**: no recebimento dentro do título, no pré-acerto/acerto ao lado do pagamento pelo link e na observação da baixa ("Pago pelo link InfinitePay, cartão 3x. Taxa paga pelo cliente: R$ X"). **Não entra no caixa nem nos relatórios financeiros**; aparece separada, como informação, com explicação na tela (diferença entre o valor do título e o pago pelo cliente, parcelamento com taxa do cliente, `assured_installments`). |
| 4 | Duplicidade / título já quitado: **sempre revisão manual** (sem crédito automático). |
| 5 | `paid_amount < amount` (desconto PIX/cartão): vai para **REVISÃO** com motivo; não baixa sozinho. |
| 6 | Busca da fila a cada **2 min** (configurável) em **horário comercial** do servidor e **busca completa ao ligar**. Novo: botão **"Verificar pagamento"** no **título**, **pré-acerto** e **acerto** (só com link ativo). O polling não pode deixar o sistema lento: uma chamada curta por ciclo, em segundo plano. |
| 7 | Retenção no Cloudflare: **90 dias** (após o ack, apaga sozinho). Alertas **somente dentro do sistema**, para o administrador (aviso em destaque, p. ex. em Contas a Receber). **Sem e-mail.** |
| 8 | **Sem piloto real de R$ 1,00**: entra direto em modo automático. Payload inesperado/campo ausente vai para **REVISÃO** (nunca baixar errado); payload bruto guardado; **interruptor** de configuração para desligar a baixa automática sem novo deploy. |
| 9 | **Sem usuário** nas baixas automáticas (revisado em 2026-11-23): o recebimento grava data e hora e deixa o usuário (criado por/alterado por) **vazio (nulo)**; não se usa o ADMIN nem se cria usuário de sistema. A origem `INFINITEPAY`, o campo `automatico = true` e a observação (montada pelo backend) deixam claro que a baixa foi automática; a tela exibe "Automático (InfinitePay)". |

## 2. Levantamento da documentação oficial (resumo; mantido)

Fonte: https://www.infinitepay.io/checkout-documentacao. O que a página não afirma está marcado **INCERTO**.
- **Reenvio:** status fora de 2xx = falha e nova tentativa; número de tentativas, intervalo e prazo **INCERTOS**; responder, de preferência, em < 1 s.
- **Consulta por `order_nsu`:** **não existe** (`payment_check` exige `handle`, `order_nsu`, `transaction_nsu`, `slug`). Logo não há "conferência de links pendentes" pela InfinitePay.
- **URL do webhook:** por link, na criação (`webhook_url` ou objeto `webhook {url, events}`); **imutável**; sem configuração de conta. **INCERTO:** coexistência dos dois campos; método HTTP e Content-Type (presumir POST/JSON); payload dos eventos que não `paid`.
- **Eventos:** `created`, `paid`, `settled`, `canceled`, `refunded`, `payment_failed`. Payload do `paid`: `invoice_slug`, `amount`, `paid_amount`, `installments`, `capture_method`, `transaction_nsu`, `order_nsu`, `receipt_url`, `items`. **Sem assinatura**, sem lista de IPs.
- **Taxa:** `paid_amount` pode ser maior que `amount` (a doc manda não exigir igualdade); comportamento com desconto **INCERTO** (por isso a decisão 5).
- **`payment_check`:** 200 com `success`, `paid`, `amount`, `paid_amount`, `installments`, `capture_method`; divergência devolve `success:false`/`paid:false`.
- **Cloudflare Free** (fontes secundárias; confirmar nas páginas oficiais): Workers ~100 mil req/dia; D1 5 GB, 100 mil linhas escritas/dia; CPU 10–30 ms por invocação (**INCERTO**). Folga ampla para o volume.

Como o formato real do webhook é incerto e **não haverá piloto** (decisão 8), a proteção é estrutural: o ERP só baixa após `payment_check` **e** após validar um conjunto fechado de campos; qualquer divergência vai para revisão com payload bruto guardado.

## 3. Arquitetura

```
 Cliente paga          InfinitePay            CLOUDFLARE (sempre ligado)                          ERP LOCAL (seg–sex, horário comercial)
 ─────► link ─────►  checkout ──webhook──►  https://pagamentos.charmejoias.com.br/w/{token}
                                              Worker: valida token, guarda no D1, responde 200
                                              (NÃO decide nada)                                        ┐
                                                                                                       │ a cada 2 min (e ao ligar; e botão "Verificar pagamento")
                                              GET /fila  (Bearer segredo do ERP)  ◄────────────────────┘
                                                                                      │ para cada item:
                                                                                      ├─► POST /payment_check (InfinitePay)
                                                                                      ├─► valida campos, valor, estado do título
                                                                                      ├─► baixa (sem usuário, origem INFINITEPAY) OU → REVISÃO
                                              POST /fila/ack {ids}  ◄──────────────────┘
                                              Worker: marca ack; o ERP pede POST /fila/limpar (itens com ack > 90 dias)
```

Princípios: servidor local **nunca exposto** (só conexões de saída); o receptor só recebe e guarda e **não** chama a InfinitePay nem conhece a chave `inchk_`; quem decide é o ERP, sempre após `payment_check`.

## 4. Escopo Cloudflare (Worker, D1, domínio, token, retenção)

- **Worker** `charme-pagamentos` (JavaScript/TypeScript, `wrangler`), implantado na conta do usuário. **Custom Domain** `pagamentos.charmejoias.com.br` (a rota é criada pelo Cloudflare; não usar `workers.dev` nos links; desativar a rota `workers.dev` do Worker após o domínio ativo, para impedir uso acidental).
- **D1** `charme-pagamentos-fila`: tabela `fila(id, recebido_em, evento, order_nsu, transaction_nsu, invoice_slug, corpo, ack_em)`, `UNIQUE(transaction_nsu, evento)` (quando o payload não trouxer `transaction_nsu`, a chave usa um hash do corpo, e o item é guardado mesmo assim: nunca descartar).
- **Rotas:** `POST /w/{token}` (InfinitePay; JSON ≤ 16 KB; 200 para qualquer corpo recebido e guardado, 404 se o token estiver errado; guardar mesmo corpo estranho, o ERP decide); `GET /fila?limite=50` (ERP; itens sem `ack_em`, mais antigos primeiro, com `corpo` bruto); `POST /fila/ack` (ERP; idempotente); `GET /saude` (ERP; `{ok, pendentes, maisAntigoEm, versao}`).
- **Segredos** (Cloudflare *secrets*; nunca em código ou repositório): `TOKEN_ENTRADA` (na URL do webhook; ≥ 128 bits; aceitar lista de tokens ativos para rotação) e `SEGREDO_ERP` (Bearer do ERP; comparação em tempo constante). Dois valores distintos.
- **Retenção (decisão do usuário, opção A):** **sem cron** e **sem subdomínio workers.dev de conta**. Durante a busca, o ERP (no máximo 1 vez a cada 24 h) chama `POST /fila/limpar` (Bearer `SEGREDO_ERP`, corpo `{"dias":90}`), que apaga **somente** itens **com ack** há mais de 90 dias; itens **sem ack nunca são apagados** (evita perder pagamento); `/saude` os expõe e o ERP alerta o administrador.
- **Rate limit** em `/w/*` (regra do Cloudflare, ex. 60/min por IP) e corpo ≤ 16 KB.
- **Observabilidade:** logs do Worker sem corpo completo (apenas ids/tamanho).
- **Versionamento:** código do Worker em pasta própria do repositório do backend (`infra/cloudflare-receptor/`), com README de implantação; **não** vai ao pacote do ERP.

## 5. Escopo ERP

Pacote existente `com.manager.app.infinitepay` (cliente, links, configuração, ciclo de vida). Novos componentes:
- **`ReceptorClient`**: cliente HTTP de saída (timeouts 5 s/10 s) para `/fila`, `/fila/ack`, `/saude`; segredo nunca em log.
- **`BaixaAutomaticaService`**: orquestra buscar → processar → ack, com trava e idempotência.
- **`InfinitePayEvento`** + repositório (tabela `infinitepay_evento`, V80): auditoria e fila de revisão.
- **Job** `@Scheduled` (a cada N minutos, dentro do horário comercial configurado) + gatilho `ApplicationReadyEvent` (busca completa ao ligar, até esvaziar a fila, limite de ciclos).
- **Payment_check** (já existe o cliente da InfinitePay; estender com o método).
- **Baixa:** reutiliza `ContaReceberRecebimentoService` (mesmas travas), `origem=INFINITEPAY`, banco do link, forma por `capture_method`, observação automática montada pelo backend. **Usuário nulo**: o processamento roda em contexto sem autenticação (thread dedicada); a baixa não tem usuário.
- **Revisão, alerta e botão "Verificar pagamento".**

### 5.1 Processamento de um item (regra única, usada pelo job e pelo botão)
1. Lê o corpo; se não for JSON válido ou faltar qualquer campo obrigatório (`order_nsu`, `transaction_nsu`, `invoice_slug`/`slug`, `amount`, `paid_amount`, `capture_method`) → `REVISAO` ("payload inesperado"), corpo bruto guardado, **ack** (o receptor já não precisa guardar; o ERP tem o bruto).
2. Idempotência: `UNIQUE(transaction_nsu, evento)` em `infinitepay_evento`; se já existir `PROCESSADO`/`REVISAO`/`IGNORADO`, só confirma o ack.
3. Localiza o link por `order_nsu`; não achou → `REVISAO` ("link desconhecido").
4. Evento ≠ `paid`/`settled`: `canceled`, `refunded`, `payment_failed` apenas **sinalizam** (`REVISAO` informativo/alerta; não estornam).
5. `payment_check` (`handle` da configuração). Falha de rede/5xx → `ERRO_TEMPORARIO`, **sem ack**, nova tentativa no ciclo seguinte (limite de tentativas; depois `REVISAO`). `paid:false` → `IGNORADO` com motivo.
6. Validações: `amount` (centavos) == valor do link; `order_nsu` confere; título existe e não cancelado; saldo do título ≥ valor.
   - Título cancelado → `REVISAO` ("pago após cancelamento"). Título já quitado/saldo insuficiente/pagamento duplicado → `REVISAO` ("duplicidade"; **nunca crédito automático**).
   - `paid_amount < amount` → `REVISAO` ("valor pago menor que o do link — possível desconto").
   - `capture_method` desconhecido (sem forma mapeada) → `REVISAO`.
7. Tudo certo → baixa por **`amount`** (não por `paid_amount`); `valor_taxa_cliente = max(0, paid_amount − amount)` gravado; link → `PAGO_AUTOMATICO`; demais links do título encerrados (ciclo de vida existente); `PROCESSADO`; ack.
8. **Interruptor** `baixa_automatica_ativa`: com ele desligado, o job continua buscando e guardando em `infinitepay_evento` com status `RECEBIDO_AGUARDANDO` mas **não baixa**; o administrador pode ligar de novo e reprocessar. (Alternativa: desligar também a busca; escolha: manter a busca, pois não perde nada e evita acúmulo no Cloudflare.)

### 5.2 Busca, concorrência e desempenho
- **Trava única**: `pg_try_advisory_lock` (chave fixa) por execução; se ocupada, o ciclo/clique espera ou devolve "verificação em andamento". Itens também travados por linha (`SELECT ... FOR UPDATE SKIP LOCKED` em `infinitepay_evento`) e idempotência por `UNIQUE`, de modo que job e botão nunca processam o mesmo `transaction_nsu` duas vezes. Ordem de travas: advisory → evento → título (mesma ordem global do financeiro: título antes de excedente).
- **Sem lentidão**: um `GET /fila?limite=50` curto por ciclo, executado em *thread* própria do agendador (não na requisição do usuário); timeout de 10 s; se a fila vier vazia, o ciclo termina em milissegundos. Após falha, recuo progressivo (2, 4, 8 min, máx. 30) e alerta.
- **Sem janela de horário**: a busca roda a cada N minutos (padrão 2, configurável) **sempre que o servidor estiver ligado**, em qualquer dia e hora (o receptor recebe 24 h por dia). **Ao iniciar**: busca completa imediata, em segundo plano, sem atrasar a subida. Volume: 720 buscas/dia = 720 requisições/dia ao Worker (limite gratuito ~100 mil/dia), mais as leituras de D1 (uma consulta por busca; limite de 5 milhões de linhas lidas/dia) e as escritas dos eventos: folga superior a 99%.

### 5.3 Botão "Verificar pagamento"
- Aparece no **título a receber**, no **pré-acerto** e no **acerto** somente se houver link de pagamento **ativo** (automático) vinculado.
- Endpoint: `POST /infinitepay/verificar-pagamento` com `{contaReceberId}` ou `{preAcertoId}`/`{acertoId}` (resolve os títulos); executa a mesma rotina de 5.1 (busca na hora no receptor, processa, ack) **restrita** aos eventos cujo `order_nsu` pertença a esses títulos, mas drenando e guardando os demais itens sem baixa fora de escopo (ou processando-os também: decisão simples = processa tudo que chegou, pois a regra é idempotente).
- Resposta: `{pagamentosRegistrados: [{recebimentoId, valor, taxaCliente}], revisoes: n, mensagem}`; mensagens: "Pagamento de R$ X registrado." / "Nenhum pagamento novo." / "Receptor indisponível; tente novamente em instantes." / "Verificação em andamento."
- Permissão: `CONTA_RECEBER_RECEBER` (quem pode baixar manualmente). Limite de uso: 1 chamada/10 s por usuário (anti-abuso).
- Concorrência: mesma trava de 5.2; se o job estiver rodando, o botão aguarda até 5 s e então responde "verificação em andamento".

### 5.4 Configuração (Sistema > InfinitePay) — V81
Campos novos (segredos só de escrita, máscara na leitura, perfil administrativo, mesmo padrão da configuração de e-mail): `baixa_automatica_ativa` (interruptor), `modo_automatico_padrao`, `receptor_url` (padrão `https://pagamentos.charmejoias.com.br`), `receptor_token_entrada` (usado só para montar `webhook_url`), `receptor_segredo_erp`, `intervalo_fila_minutos` (padrão 2), `banco_padrao_id`, mapeamento `capture_method` → forma de pagamento (PIX, cartão de crédito). Botões: "Testar receptor" (`/saude`), "Verificar agora". `webhook_url` é montado como `<receptor_url>/w/<token>` e **validado**: recusar a geração de link automático se a URL contiver `workers.dev`, `localhost` ou não for HTTPS do domínio configurado.

### 5.5 Dados (migrations; **backup antes do deploy**, aviso e aprovação prévios)
- **V80** — `infinitepay_evento` (`id`, `transaction_nsu`, `evento`, `order_nsu`, `link_id`, `status` [`RECEBIDO`, `PROCESSADO`, `REVISAO`, `IGNORADO`, `ERRO_TEMPORARIO`, `AGUARDANDO`], `motivo`, `payload` (texto, limite 16 KB), `amount_centavos`, `paid_amount_centavos`, `capture_method`, `recebimento_id`, `tentativas`, `criado_em`, `processado_em`, `resolvido_por`, `resolvido_em`, `resolucao`); `UNIQUE(transaction_nsu, evento)`; índices por `status`, `order_nsu`; permissões `INFINITEPAY_REVISAO_VISUALIZAR` e `INFINITEPAY_REVISAO_RESOLVER` ao ADMIN.
- **V81** — `conta_receber_link_pagamento`: `modo` (`MANUAL`/`AUTOMATICO`), `banco_id`, `transaction_nsu`, status `PAGO_AUTOMATICO`; configuração da InfinitePay: campos de 5.4.
- **V82** — `conta_receber_recebimento`: origem `INFINITEPAY` (ajusta `ck_recebimento_origem` e `ck_recebimento_origem_banco`: exige banco e forma), `valor_taxa_cliente NUMERIC(15,2) NOT NULL DEFAULT 0`, `link_id`, `transaction_nsu`; relatórios e saldo de caixa continuam somando **apenas** `valor_principal`/valor baixado (a taxa é coluna informativa e **não** soma em nenhum agregado financeiro; revisar todas as leituras, como na fase 3 do crédito).
Nenhuma migration existente é editada.

### 5.6 Fila de revisão, alerta e telas
- Casos: payload inesperado; link desconhecido; pago após cancelamento; título já quitado/duplicidade; valor menor (desconto); `capture_method` desconhecido; `canceled`/`refunded`/`payment_failed` (informativos); falha repetida do `payment_check`.
- Ações (cada uma com usuário, data e motivo): **Aplicar no título** (baixa manual guiada, quando o título permite; exige `CONTA_RECEBER_RECEBER`), **Ignorar** (motivo ≥ 5), **Reprocessar**. (Converter em crédito do cliente **não** é oferecido nesta fase, conforme decisão 4; pode entrar depois como ação manual.)
- Endpoints `/infinitepay/revisoes` (`GET` lista/detalhe com payload bruto; `POST /{id}/aplicar`, `/ignorar`, `/reprocessar`), `GET /infinitepay/receptor/saude`, `POST /infinitepay/fila/sincronizar`, `POST /infinitepay/verificar-pagamento`.
- **Alerta ao administrador (somente no sistema):** endpoint `GET /infinitepay/alertas` (para quem tem `INFINITEPAY_REVISAO_VISUALIZAR`) com: revisões pendentes (contagem), receptor sem resposta há mais de X min no horário comercial, item mais antigo sem ack > 1 dia útil, eventos `ERRO_TEMPORARIO` acima do limite, baixa automática desligada. O front exibe faixa em destaque em Contas a Receber e selo no menu. **Sem e-mail.**
- **Taxa do cliente**: expor `valorTaxaCliente`, `origem` e `pagoPeloLink` nos DTOs de recebimento do título, no resumo financeiro do pré-acerto/acerto (ao lado do pagamento pelo link) e no detalhe; texto de ajuda padronizado devolvido pelo backend ou fixo no front.

## 6. Fases de implementação

Ordem proposta: **F0 → F1 → F2 → F3 → F4 → F5 → F6**. F1 (Cloudflare) e F2 (ERP base) podem andar em paralelo após F0.

### F0 — Preparação e acessos (0,5 dia; usuário/Maestro)
Entregável: acessos e nomes definidos (seção 7). Aceite: conta/zone confirmadas, `wrangler` autenticado, tokens gerados e guardados fora do repositório. Riscos: atraso de acessos.

### F1 — Receptor na Cloudflare (1,5–2 dias; Forja + usuário no painel)
- Entregável: Worker + D1 + Custom Domain `pagamentos.charmejoias.com.br`; rotas `/w/{token}`, `/fila`, `/fila/ack`, `/fila/limpar` (retenção de 90 dias acionada pelo ERP), `/saude`; segredos; README de implantação (sem cron).
- Aceite: `POST` de payload fictício no domínio real grava no D1; `GET /fila` sem/errado Bearer = 401; token errado = 404; ack idempotente; reenvio do mesmo `transaction_nsu` não duplica; item com ack > 90 dias é apagado (testado com data forjada); `workers.dev` desativado.
- Migrations: nenhuma. Testes: `wrangler dev`/Miniflare + script de testes de contrato; teste real somente com payloads fictícios (nunca a InfinitePay). Riscos: propagação do Custom Domain/certificado (horas); limites do plano Free; CPU 10 ms (manter o Worker mínimo).

### F2 — Base do ERP: configuração, cliente do receptor, migrations V80–V82 (2,5 dias; Forja)
- Entregável: V80, V81, V82 (com **backup antes do deploy**, aviso e aprovação); configuração (5.4) com interruptor; `ReceptorClient`; modo automático por link com `webhook_url` montado e validado; `banco_id` e forma por `capture_method`.
- Aceite: configuração salva sem devolver segredos; geração de link automático exige banco; URL do webhook rejeitada se `workers.dev`/não HTTPS; links manuais inalterados; migrations aplicam em cópia do banco real sem erro. Testes: JUnit (validação de URL, mapeamento de formas), Flyway em banco de teste. Riscos: ajuste dos CHECKs do recebimento (revisar **todas** as leituras de recebimento para a taxa nunca entrar no caixa).

### F3 — Processamento e baixa automática (4 dias; Forja)
- Entregável: `BaixaAutomaticaService` (5.1), job agendado (sem janela de horário) e busca ao ligar, trava/idempotência, `payment_check`, baixa pelo serviço existente (sem usuário, origem `INFINITEPAY`, observação automática), taxa gravada, encerramento do link, `AGUARDANDO` com interruptor desligado.
- Aceite: cenários de 5.1 todos cobertos; duas buscas simultâneas e job + botão não duplicam baixa; saldo do banco e movimentação corretos (uma `movimentacao_banco` por pagamento, valor = `amount`); taxa fora do caixa e dos relatórios; interruptor impede baixa sem deploy. Testes: JUnit com receptor e InfinitePay **simulados**; concorrência (várias *threads*); regressão de caixa. Riscos: deadlock (ordem de travas), campo/formato de payload real diferente do suposto (mitigado por revisão).

### F4 — Revisão, alertas e botão "Verificar pagamento" (3 dias; Forja)
- Entregável: endpoints de revisão (5.6), alertas, `POST /infinitepay/verificar-pagamento`, permissões, DTOs com taxa; documentação `docs/financeiro-infinitepay-fase3b-integracao-frontend.md`.
- Aceite: cada caso de revisão aparece com motivo e payload bruto; ações registram usuário/data/motivo; botão respeita permissão, limite de uso e trava; mensagens conforme 5.3. Testes: JUnit + roteiro de API na 8080 (sem InfinitePay real). Riscos: regras de "aplicar no título" em títulos cancelados/quitados.

### F5 — Simulador estendido e teste ponta a ponta local (3 dias; Forja)
- Entregável: simulador (porta 9099) com `POST /payment_check` configurável (paid, não pago, `paid_amount` maior/menor, 5xx, timeout) e emissão de webhook para um receptor local; roteiro ponta a ponta: webhook → Worker local (`wrangler dev`) → ERP busca → `payment_check` simulado → recebimento → ack.
- Aceite: todos os cenários (pago, taxa do cliente, desconto, duplicado, cancelado, payload estranho, link desconhecido, receptor fora do ar, interruptor desligado) com resultado esperado; relatório com evidências. Riscos: o simulador reproduz o formato **suposto**; o formato real só se confirma no primeiro pagamento real.

### F6 — Frontend, QA e implantação (Vitral 4–5 dias; Sonar 2 dias; Forja 1 dia de apoio/implantação)
- Entregável: telas (seção 8), QA do Sonar, implantação: backup, aprovar V80–V82, subir ERP, configurar Sistema > InfinitePay, ligar interruptor.
- **Sem piloto de R$ 1,00** (decisão 8): recomendação de segurança *dentro* da decisão: no primeiro uso real, manter o painel de revisão aberto e conferir manualmente o primeiro pagamento; o interruptor permite desligar a baixa em segundos. Opcional (a critério do usuário): ligar o modo automático com "revisar tudo" nas primeiras 48 h (apenas revisão, sem baixa) — **não implementado a menos que solicitado**.
- Aceite: QA aprovado; checklist de implantação cumprido; plano de rollback testado em cópia.

### Rollback (todas as fases)
Desligar `baixa_automatica_ativa` (sem deploy) e, se necessário, a busca; links já criados continuam acumulando no receptor (nada se perde) e podem ser baixados manualmente; migrations são aditivas e não exigem reversão; o Worker pode ser desativado no painel. Backup do banco antes de cada deploy com migration.

## 7. O que preciso do usuário / Maestro
1. Acesso para implantar o Worker: **token de API da Cloudflare** com escopo mínimo (Workers Scripts: Edit; D1: Edit; Workers Routes/Custom Domains na zona `charmejoias.com.br`) **ou** o usuário executar os comandos que eu fornecer. Os tokens nunca vão para o repositório nem para conversas; informar por canal seguro.
2. Confirmar nome do Worker (`charme-pagamentos`) e do banco D1 (`charme-pagamentos-fila`); confirmar que o registro DNS de `pagamentos` pode ser criado pelo Custom Domain (o Cloudflare gerencia o registro).
3. Definir/gerar `TOKEN_ENTRADA` e `SEGREDO_ERP` (posso fornecer um gerador local; os valores são guardados nos *secrets* do Worker e na configuração do ERP, que não devolve segredos).
4. ~~Usuário ADMIN e janela comercial~~: **removidos** (baixa sem usuário; sem janela de horário).
5. Aprovação de cada migration (V80, V81, V82) e **janela de deploy com backup**.
6. O usuário informa InfiniteTag e chave `inchk_` na tela de configuração (agentes nunca as recebem).
7. Confirmar o mapeamento de formas de pagamento (PIX, Cartão de crédito) e o banco padrão.

## 8. O que o Vitral precisa fazer (após o contrato da F4)
- **Botão "Verificar pagamento"** nos 3 lugares (detalhe do título a receber, seção Financeiro do pré-acerto e do acerto), visível só com link ativo automático; estados (carregando, "Pagamento de R$ X registrado", "Nenhum pagamento novo", "Receptor indisponível", "Verificação em andamento").
- **Taxa do cliente visível**: no recebimento do título, no pré-acerto/acerto ao lado do pagamento pelo link e na observação; texto explicativo; rótulo de origem **"InfinitePay (automático)"**; **fora** de totais de caixa.
- **Diálogo de geração de link**: opção "Baixa automática" (banco obrigatório; forma pelo método de pagamento), aviso formal de que o link automático não pode mudar de receptor.
- **Fila de revisão** (Contas a Receber > Revisão InfinitePay): lista com filtros, detalhe com payload bruto, ações Aplicar/Ignorar/Reprocessar.
- **Aviso em destaque ao administrador** (faixa em Contas a Receber e selo no menu) a partir de `GET /infinitepay/alertas`.
- **Sistema > InfinitePay**: campos do receptor, interruptor da baixa automática, intervalo, banco/formas, "Testar receptor", "Verificar agora".
- Tutorial e novidades.

## 9. Segurança (resumo)
Servidor local não exposto; token na URL + dois segredos distintos e rotacionáveis; `payment_check` obrigatório e validação fechada de campos; corpo ≤ 16 KB; só HTTPS; sem dados pessoais além do mínimo; retenção 90 dias após ack (limpeza acionada pelo ERP); segredos nunca em código/log/resposta; baixa idempotente com travas pessimistas; `webhook_url` validado contra `workers.dev`/localhost; baixas sem usuário, com origem `INFINITEPAY`, `automatico = true` e observação explícitas para rastreabilidade.

## 10. Esforço e riscos

| Fase | Responsável | Dias úteis |
|------|-------------|-----------|
| F0 Preparação/acessos | Usuário/Maestro | 0,5 |
| F1 Receptor Cloudflare | Forja | 1,5–2 |
| F2 Base ERP + V80–V82 | Forja | 2,5 |
| F3 Processamento e baixa | Forja | 4 |
| F4 Revisão/alertas/botão | Forja | 3 |
| F5 Simulador + ponta a ponta | Forja | 3 |
| F6 Frontend + QA + implantação | Vitral 4–5; Sonar 2; Forja 1 | 7–8 |
| **Total backend/receptor** | | **~15 a 15,5** (calendário ~3 semanas com paralelismo F1∥F2) |

Riscos principais: (1) **formato real do webhook incerto e sem piloto** — mitigado por validação fechada, payload bruto, revisão por padrão e interruptor; (2) política de reenvio desconhecida — mitigada pelo receptor sempre no ar; (3) `webhook_url` imutável — domínio próprio estável; (4) taxa do cliente fora do caixa exige revisão de **todas** as leituras de recebimento; (5) dependência da Cloudflare para fluxo financeiro; (6) mudança de limites do plano Free; (7) desconto PIX/cartão tratado como revisão (volume maior de revisões no início); (8) ausência de assinatura — dependência total do `payment_check`.

## 11. Perguntas — RESPONDIDAS (ver seção 1)
Todas as 9 perguntas do plano anterior foram respondidas em 2026-11-23. **Pontos remanescentes menores** (não bloqueiam o início da F1/F2): (a) ~~login do ADMIN e janela comercial~~ (removidos); (b) se o usuário deseja o modo opcional "revisar tudo nas primeiras 48 h" (seção 6, F6); (c) forma de entrega do token da Cloudflare (seção 7, item 1).

## 12. Revisões de 2026-11-23 (após a F2)
- **Sem janela comercial** e **sem `usuario_baixa_id`/ADMIN** (itens removidos acima). A busca roda a cada 2 minutos (configurável) sempre que o servidor estiver ligado.
- **Retenção pelo ERP** (opção A): nada de cron nem de subdomínio `workers.dev`; `POST /fila/limpar` no receptor.
- **Contratos detalhados** (verificar pagamento, revisão, alertas, saúde, sincronização, resumo financeiro, enums e rótulos): `docs/financeiro-infinitepay-fase3b-integracao-frontend.md`.
- **Aplicar no título** (revisão) usa banco, forma e data do próprio pagamento/link; só exige dados do usuário nos casos "forma não mapeada" e "banco desativado" (ver contrato).
