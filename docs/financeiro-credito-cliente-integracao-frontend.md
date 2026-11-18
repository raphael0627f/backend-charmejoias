# Financeiro 6.3 — Crédito do cliente, fase 1: excedente do acerto (A DESTINAR) e ajuste do título do pré-acerto

Migration **V76** (tabelas novas `excedente_acerto` e `excedente_acerto_evento`; nada existente foi alterado). Sem excedente retroativo: vale a partir da confirmação do acerto. Erros no formato `RestErrorMenssage` (`menssage`).
Permissões novas (concedidas ao perfil ADMIN; relogar): `CREDITO_CLIENTE_VISUALIZAR`, `CREDITO_CLIENTE_GERENCIAR`, `CREDITO_CLIENTE_APLICAR` (as duas últimas passam a ser usadas nas fases seguintes).

## 1. Regra do excedente (base = o que o cliente efetivamente pagou)
Só o **acerto com título de pré-acerto** gera excedente automático. `T` = valor dos títulos ativos do pré-acerto da consignação; `R` = recebido deles; `A` = valor a pagar do acerto.
`excedente = max(0, R − A)`; nasce **A_DESTINAR** (situações: `A_DESTINAR`, `CREDITO`, `DEVOLUCAO`, `CANCELADO`; o destino é escolhido nas fases 2 e 4).

## 2. Ajuste do título do pré-acerto na confirmação (opção B, decisão do usuário)
Se `T > A` ao confirmar o acerto, o sistema **pede aceite explícito**:
- **GET `/acertos-consignacao/{id}/financeiro`** (acerto ainda não confirmado) traz o novo bloco **`ajusteProposto`** (ou `null` quando não há ajuste):
  `{ tituloPreAcerto, valorAcerto, recebido, valorAjustado, reducao, exigeAceite: true, mensagem }`.
  - `valorAjustado = max(A, R)`. Mensagem: `O título do pré-acerto (R$ T) está acima do acerto (R$ A). Ajustar o título para R$ N?`
  - Subcaso **acerto sem valor a pagar e nada pago** (`A = 0`, `R = 0`): `valorAjustado = 0` e a mensagem é `O título do pré-acerto será cancelado, pois nada foi pago e o acerto não tem valor a pagar.`
- **PUT `/acertos-consignacao/{id}/confirmar`** aceita corpo **opcional** `{ "ajustarTituloPreAcerto": true }` (sem corpo o comportamento anterior é mantido).
  - Com `T > A` e **sem** o aceite: **409** com a pergunta acima em `menssage`; a confirmação **não segue** e nada é alterado.
  - Com aceite (mesma transação, com a ordem de travas consignação → acerto → títulos): reduz o(s) título(s) do pré-acerto para `max(A, R)` consumindo o saldo em aberto **a partir da última parcela**; parcela sem recebimento totalmente reduzida é **cancelada**; parcela com recebimento nunca fica abaixo do recebido (vira `RECEBIDO` quando zera o aberto). Se `R > A` o excedente `R − A` nasce `A_DESTINAR`. Se falhar, nada fica pela metade.
  - `A = 0` e `R = 0`: o título do pré-acerto é **cancelado** (motivo `Cancelado automaticamente na confirmação do acerto sem valor a pagar.`) e o acerto gera, em seguida, o **título zerado** da 6.3. `A = 0` e `R > 0`: o título vai para `R` e o excedente é `R`.
  - Histórico do título: evento **`VALOR_AJUSTADO_ACERTO`** (`Valor do título ajustado de R$ X para R$ Y na confirmação do acerto. ...`) ou `TITULO_CANCELADO`; links de pagamento ativos de título ajustado viram `NAO_UTILIZAR` (evento `LINK_PAGAMENTO_NAO_UTILIZAR`); de título cancelado viram `TITULO_CANCELADO` e a resposta de confirmar traz o campo **`aviso`** (`Os links de pagamento já enviados ... ainda podem ser pagos.`), ausente quando não há link.
  - Sem `ajusteProposto` (`T ≤ A` ou título integralmente recebido): nenhuma pergunta; confirma como antes. Título integralmente recebido acima do acerto (`R = T > A`) cria só o excedente.
- O front deve: ler `ajusteProposto` antes de confirmar; se não nulo, exibir `mensagem` com Confirmar/Cancelar; ao aceitar, enviar `{ "ajustarTituloPreAcerto": true }`. Tratar também o 409 (concorrência ou GET desatualizado).

## 3. GET `/acertos-consignacao/{id}/financeiro` — campos novos
- `excedenteAcerto`: `{ id, valor, situacao, destino, criadoEm }` ou `null` (sem excedente ou cancelado). **Atenção:** o campo numérico já existente `excedente` continua significando "titulado acima do valor do acerto" (aviso); `excedenteAcerto` é o excedente persistido.
- `ajusteProposto`: ver acima.

## 4. Pendências de excedentes (somente leitura nesta fase) — permissão `CREDITO_CLIENTE_VISUALIZAR` (ou GERENCIAR/APLICAR)
- **GET `/excedentes-acerto?situacao=&busca=&page=&size=`** → `{ content: [Item], page, size, totalElements, totalPages }`; `situacao` ∈ `A_DESTINAR|CREDITO|DEVOLUCAO|CANCELADO` (inválida → 400); `busca` por código/nome do cliente ou número do acerto/consignação. Ordenação: mais recentes primeiro.
- **GET `/excedentes-acerto/{id}`** → `{ excedente: Item, eventos: [{ id, evento, detalhe, usuario, dataHora }] }` (404 `Excedente não encontrado.`).
- `Item`: `{ id, valor, situacao, destino, acertoId, consignacaoId, cliente: { id, codigo, nome, documento }, criadoEm, atualizadoEm }`.
- Eventos do excedente: `EXCEDENTE_CRIADO`, `EXCEDENTE_AJUSTADO`, `EXCEDENTE_CANCELADO`, `EXCEDENTE_REATIVADO`.

## 5. Efeito de cancelar/estornar depois da confirmação do acerto
O excedente acompanha o recebido do pré-acerto: estornar recebimento de título do pré-acerto **recalcula** o excedente `A_DESTINAR` (zerou → `CANCELADO`; novo recebimento → `REATIVADO`/ajustado). Se o excedente **já foi destinado** (crédito ou devolução), cancelar o título ou estornar o recebimento é **bloqueado** (400): `O título pertence a um pré-acerto cujo excedente já foi destinado (crédito). Desfaça o destino do excedente antes de estornar o recebimento.`
