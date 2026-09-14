# Correção e reenvio de Nota Fiscal rejeitada

Documento para o frontend implementar o fluxo de "corrigir e reenviar" quando a SEFAZ rejeita uma nota.

---

## O que mudou

Antes, uma nota com status `REJEITADA` ficava travada: `PUT /notas-fiscais/{id}` só aceitava nota em `RASCUNHO`, então não tinha como corrigir os dados errados e reenviar pelo sistema — o usuário ficava sem saída.

Agora `PUT /notas-fiscais/{id}` aceita nota em `RASCUNHO` **ou** `REJEITADA`. Ao salvar a correção de uma nota rejeitada, o backend automaticamente:

- Volta o `status` dela pra `RASCUNHO`.
- Mantém o **mesmo `numero` e `serie`** (não perde a numeração — nota rejeitada nunca foi autorizada pela SEFAZ, então nunca "gastou" a numeração de verdade).
- Limpa o `motivoRejeicao` (fica `null`).
- Limpa o `contoraDocumentId` interno (não aparece na resposta, é só controle interno).

## Fluxo que o frontend deve implementar

1. Nota aparece na listagem com `status: "REJEITADA"` e o campo `motivoRejeicao` preenchido — mostrar esse motivo pro usuário (já é assim hoje, nada novo aqui).
2. Adicionar um botão tipo **"Corrigir e reenviar"** (ou reaproveitar o botão de editar) na tela de nota rejeitada, que:
   - Abre o formulário de edição normal, pré-preenchido com os dados atuais da nota (mesma tela que já existe pra editar rascunho).
   - Ao salvar, chama `PUT /notas-fiscais/{id}` normalmente (mesmo payload de sempre, sem nada de diferente).
   - A resposta volta com `status: "RASCUNHO"` e `motivoRejeicao: null` — nesse ponto a tela deve tratar a nota como um rascunho comum de novo.
3. Depois de editar, o usuário reenvia normalmente pelo botão que já existe: `POST /notas-fiscais/{id}/emitir`.

**Não precisa de nenhum endpoint novo.** É o mesmo `PUT` e o mesmo `POST /emitir` que já existiam — só que agora o `PUT` não bloqueia mais quando o status é `REJEITADA`.

## Validação que continua a mesma

Editar (`PUT`) continua **bloqueado** pra qualquer outro status que não seja `RASCUNHO` ou `REJEITADA` — `ENVIADA`, `AUTORIZADA`, `CANCELAMENTO_SOLICITADO`, `CANCELADA` continuam dando **400**:

```json
{
  "status": "400 BAD_REQUEST",
  "menssage": "Só é possível atualizar uma nota fiscal em rascunho ou rejeitada. id: 4, status atual: AUTORIZADA"
}
```

Nota **autorizada** que precisa de correção continua no fluxo de sempre (Carta de Correção via `POST /{id}/corrigir`, ou cancelamento via `POST /{id}/cancelar` + nota nova) — isso não muda em nada.

## Coisa técnica interna (não afeta o frontend, só pra constar)

Achamos e corrigimos um bug na integração com a Contora nesse processo: a chave de idempotência usada pra evitar duplicidade nas chamadas de emissão era baseada só no id interno da nota, então reenviar a mesma nota corrigida ia usar a mesma chave da tentativa rejeitada anterior — e a Contora provavelmente devolveria a resposta antiga (rejeitada) em vez de processar os dados corrigidos. Isso foi corrigido internamente com um contador de tentativas de envio; não muda nada no contrato da API que o frontend consome.
