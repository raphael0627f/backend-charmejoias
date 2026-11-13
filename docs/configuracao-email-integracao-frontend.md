# Configuração do e-mail de envio (SMTP) — contrato para o frontend

Tela no grupo **Sistema**. A configuração fica no banco (migration `V63__configuracao-email.sql`: tabela `configuracao_email`
com **uma única linha**) e é usada pelo envio dos XMLs das notas fiscais (`POST /notas-fiscais/enviar-xml-email`, ver
`notas-fiscais-envio-xml-email-integracao-frontend.md`). Não é mais necessário configurar variáveis de ambiente no servidor.

## Permissão

`CONFIGURACAO_EMAIL_GERENCIAR` — **somente o perfil ADMIN** (mesmo padrão da tela de Backups: permissão própria concedida por
migration ao perfil ADMIN). Vale para **GET, PUT e POST /testar** (a tela guarda credenciais). Sem a permissão: `403`.
A permissão aparece na lista de permissões da tela de perfis, mas só o ADMIN a recebe por padrão.

## GET `/configuracao-email`

Devolve a configuração atual **sem a senha**. Se nada foi salvo, devolve os padrões vazios.

```json
{
  "host": "smtp.gmail.com",
  "porta": 587,
  "usuario": "conta@gmail.com",
  "senhaConfigurada": true,
  "remetente": "conta@gmail.com",
  "nomeRemetente": "ERP Charme Joias",
  "starttls": true,
  "ssl": false,
  "timeoutMs": 30000,
  "ativo": true,
  "usandoVariaveisAmbiente": false,
  "atualizadoEm": "2026-11-13T03:00:25.204658",
  "atualizadoPor": { "id": "…", "usuario": "admin", "primeiroNome": "RAPHAEL", "sobrenome": null }
}
```

Sem configuração salva: `host`/`usuario`/`remetente` nulos, `porta: 587`, `starttls: true`, `ssl: false`, `timeoutMs: 30000`,
`ativo: false`, `senhaConfigurada: false`. `usandoVariaveisAmbiente: true` indica que nada foi salvo no banco mas o servidor tem
as variáveis `MAIL_*` (fallback, ver abaixo) — o envio funciona assim mesmo.

## PUT `/configuracao-email`

```json
{
  "host": "smtp.gmail.com",
  "porta": 587,
  "usuario": "conta@gmail.com",
  "senha": "senha-de-app",
  "remetente": "conta@gmail.com",
  "nomeRemetente": "ERP Charme Joias",
  "starttls": true,
  "ssl": false,
  "timeoutMs": 30000,
  "ativo": true
}
```
Resposta 200: o mesmo formato do GET (sem a senha).

### Regras da senha

- A senha **nunca** é devolvida pela API nem aparece em log; a resposta traz só `senhaConfigurada` (true/false).
- `senha` vazia ou omitida **mantém a senha anterior**. Só uma senha preenchida a substitui.
- `usuario` vazio = SMTP sem autenticação: nesse caso nenhuma senha é guardada (a anterior é apagada).
- Em repouso a senha é criptografada com AES-GCM. A chave é derivada de `APP_SEGREDO_CRIPTOGRAFIA` (ou, se ela não existir, do
  `JWT_SECRET`). **Trocar essa chave torna a senha salva ilegível**: o envio responde 503 com
  `Não foi possível ler a senha salva. Informe a senha novamente na configuração de e-mail.` e basta salvar de novo informando a senha.

### Validações (400, `menssage`)

| Regra | Mensagem |
|---|---|
| `starttls` e `ssl` ambos true | `As opções STARTTLS e SSL não podem ser utilizadas ao mesmo tempo. Escolha apenas uma.` |
| `ativo: true` sem servidor | `O campo Servidor SMTP é obrigatório.` |
| `ativo: true` sem porta | `O campo Porta é obrigatório.` |
| `ativo: true` sem remetente | `O campo E-mail do remetente é obrigatório.` |
| Porta fora de 1–65535 | `A porta deve estar entre 1 e 65535.` |
| Remetente inválido | `O e-mail do remetente é inválido: …` |
| `timeoutMs` fora de 1000–120000 | `O tempo limite deve estar entre 1000 e 120000 milissegundos.` |
| Tamanhos / quebras de linha | `O campo … deve ter no máximo … caracteres.` / `O nome do remetente não pode conter quebras de linha.` |

Com `ativo: false` é possível salvar a configuração incompleta (rascunho); o que for informado ainda precisa ser válido.
`starttls` omitido = true; `ssl` omitido = false; `timeoutMs` omitido = 30000.
Padrões comuns: porta 587 + `starttls: true` (Gmail/Office 365), ou porta 465 + `ssl: true` + `starttls: false`.

## POST `/configuracao-email/testar`

Envia um e-mail de teste ("Teste de configuração de e-mail") para o endereço informado.

```json
{ "para": "destino@empresa.com.br", "configuracao": { "host": "…", "porta": 587, "…": "…" } }
```
- `para`: obrigatório. `configuracao`: **opcional** — com ela testa os dados da tela ainda não salvos (mesmo formato do PUT;
  `senha` vazia = usa a senha salva); sem ela testa a configuração salva (mesmo inativa) ou, sem nada salvo, as variáveis `MAIL_*`.
- Timeout curto (no máximo 10 s). Sucesso: `200 { "sucesso": true, "mensagem": "E-mail de teste enviado para destino@empresa.com.br." }`.
- Falha: `502` com o motivo em português, sem credenciais, por exemplo:
  - `Não foi possível enviar o e-mail de teste: falha de autenticação: usuário ou senha incorretos (no Gmail, utilize uma senha de app).`
  - `… servidor não encontrado. Verifique o endereço do servidor SMTP.`
  - `… não foi possível conectar ao servidor. Verifique o endereço, a porta e o firewall.`
  - `… falha na conexão segura. Verifique as opções STARTTLS/SSL e a porta (587 com STARTTLS; 465 com SSL).`
  - `… o servidor não respondeu a tempo. …` / `… o servidor recusou o remetente ou o destinatário.`
- `400` para destino inválido ou quando não há nenhuma configuração para testar.

## Qual configuração o envio usa

1. Linha salva no banco **ativa** → usa o banco.
2. Linha salva **inativa** → envio desativado de propósito (503), mesmo que existam variáveis `MAIL_*`.
3. **Nenhuma linha** no banco → fallback para as variáveis de ambiente `MAIL_*` (compatibilidade), se houver `MAIL_HOST`.
4. Nada utilizável → `503 Envio de e-mail não configurado. Solicite ao administrador que configure o e-mail em Sistema.`
   (o frontend pode apontar para a tela de configuração).

## Auditoria

`atualizadoEm` e `atualizadoPor` (usuário que salvou). O log registra quem alterou, servidor, porta e se ficou ativo — nunca a senha.
