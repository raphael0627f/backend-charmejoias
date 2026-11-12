# Planejador de rotas: renomeação do módulo "Rotas" — guia de integração frontend

O módulo antigo **Rotas** passou a se chamar **Planejador de rotas** (a palavra "rota" agora também é usada na Agenda; ver `docs/agenda-rotas-integracao-frontend.md`). O comportamento não mudou, só os nomes.

| Antes | Agora |
|---|---|
| `/rotas`, `/rotas/{id}`, `/rotas/{id}/xlsx`, `/rotas/contagem`... | **`/planejador-rotas`** (mesmos sub-caminhos) |
| permissão `ROTA_GERENCIAR` | **`PLANEJADOR_ROTA_GERENCIAR`** |
| arquivo do XLSX `rota-<nome>.xlsx` | `planejador-rota-<nome>.xlsx` |

- Os **campos JSON** (request/response) continuam exatamente os mesmos (`nome`, `dataRota`, `itens` etc.); só a URL e a permissão mudam.
- Perfis que já tinham `ROTA_GERENCIAR` passam a ter `PLANEJADOR_ROTA_GERENCIAR` (a migration V56 atualiza `perfis_permissoes`); os dados do planejador permanecem intactos.
- Quem estiver logado com um token emitido antes do deploy ainda carrega a permissão antiga: precisa fazer login de novo para receber a nova.
- Mensagens de erro agora dizem "rota do planejador" (ex.: `"Rota do planejador não encontrada. id: 7"`).
- No banco as tabelas viraram `planejador_rotas`, `planejador_rota_clientes`, `planejador_rota_consignacoes_deixar` e `planejador_rota_consignacoes_trazer` (migration V56, só renomeia objetos).
- **Leitura também exige permissão:** todos os endpoints de `/planejador-rotas` (lista, detalhe, `/{id}/xlsx`, escrita) exigem `PLANEJADOR_ROTA_GERENCIAR`; sem ela a resposta é **403** (`menssage: "Acesso negado"`), inclusive nos GETs. O frontend deve esconder/bloquear o módulo para quem não tem a permissão.
