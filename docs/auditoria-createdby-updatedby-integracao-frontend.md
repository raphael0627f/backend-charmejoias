# Auditoria: `createdBy` / `updatedBy` nas respostas da API

## O que mudou

Agora a API rastreia automaticamente **quem criou** e **quem editou por último** cada registro dos módulos principais. Isso é preenchido sozinho pelo backend a partir do usuário logado (token JWT) — o frontend não precisa (e não deve) enviar esses campos em nenhum `POST`/`PUT`, eles são somente leitura.

## Formato do campo

Em toda resposta que tem `createdBy`/`updatedBy`, o valor é um objeto resumido do usuário ou `null`:

```json
{
  "createdBy": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "usuario": "admin",
    "primeiroNome": "Raphael",
    "sobrenome": "Ferreira"
  },
  "updatedBy": null
}
```

- `createdBy` vem `null` apenas para registros muito antigos, criados antes dessa funcionalidade existir, ou para o usuário "admin" seedado por migration (não existe um usuário autenticado nesse momento de bootstrap).
- `updatedBy` vem `null` enquanto o registro nunca foi editado (só criado).
- Em telas de listagem, exibir pelo menos `usuario` (login) já é suficiente; `primeiroNome`/`sobrenome` ficam disponíveis pra exibir o nome completo se preferir.

## Onde aparece

Adicionado em **todas as respostas de detalhe/listagem** dos módulos abaixo (tanto o objeto completo retornado em `GET /{id}`, `POST`, `PUT`, quanto a versão resumida usada em listagens paginadas, quando existir):

| Módulo | Endpoints afetados |
|---|---|
| Cliente | `/clientes` |
| Produto (e variações) | `/produtos` |
| Joia | `/joias` |
| Consignação | `/consignacoes` |
| Pré-acerto de Consignação | `/pre-acertos-consignacao` |
| Acerto de Consignação | `/acertos-consignacao` |
| Acerto de Joia | `/acertos-joia` |
| Nota Fiscal (e correções) | `/notas-fiscais` |
| Comissão | `/comissoes` |
| Mostruário | `/mostruarios` |
| Empresa | `/empresas` |
| Usuário | `/usuarios` |
| Perfil | `/perfis` |
| Consulta SPC | `/spc/consultas` (só `createdBy` — é um registro de consulta, não é editável) |
| Registro SPC | `/spc/registros` (só `createdBy` — idem) |

**Não** foi adicionado em:
- `Fornecedor` e `Pedido` — esses módulos já não expõem `createdAt`/`updatedAt` na API hoje (decisão anterior do projeto), então não expusemos auditoria neles também, pra manter consistência. Se precisar disso nesses dois módulos, é só avisar.

## O que o frontend precisa fazer

1. **Não enviar** `createdBy`/`updatedBy` em nenhum `POST`/`PUT` — são campos somente leitura, preenchidos pelo backend. Se enviados, são ignorados.
2. Nas telas de detalhe/edição, opcionalmente exibir "criado por X em {createdAt}" e "última edição por Y em {updatedAt}", usando o `usuario` (ou nome completo) do objeto.
3. Nas listagens, se fizer sentido pra UX, adicionar uma coluna/tooltip "criado por" — é opcional, os dados já estão disponíveis em toda listagem que citamos acima.
4. Tratar `createdBy`/`updatedBy` como possivelmente `null` sempre — nunca assumir que virá preenchido.
5. Nenhuma mudança é necessária em fluxos que não usam esses campos — são 100% aditivos e não quebram nada existente.
