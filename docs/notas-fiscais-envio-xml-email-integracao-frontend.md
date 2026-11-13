# Notas fiscais — envio dos XMLs por e-mail (contrato para o frontend)

Sem migration neste recurso. Nova dependência: `spring-boot-starter-mail`.

**A conta de e-mail (SMTP) agora é configurada pelo administrador na tela de Sistema** (`docs/configuracao-email-integracao-frontend.md`, salva no banco). As variáveis `MAIL_*` abaixo passaram a ser apenas **fallback**: valem somente quando não existe nenhuma configuração salva no banco.

## Endpoint

`POST /notas-fiscais/enviar-xml-email` — permissão `NOTA_FISCAL_GERENCIAR` (sem ela: 403).
Os GETs de XML (`/notas-fiscais/{id}/xml`, `/xmls`) exigem apenas autenticação; o envio para fora da empresa exige a permissão acima.

Request:
```json
{
  "notaIds": [29, 30],
  "para": ["fiscal@cliente.com.br"],
  "cc": ["copia@cliente.com.br"],
  "assunto": "XMLs das notas fiscais",
  "corpo": "Prezados,\n\nSegue em anexo o zip com os XMLs.\n\nAtenciosamente."
}
```
- `notaIds`: ids das notas (`id` da nota, não o número). Obrigatório, até **200** (repetidos são unificados).
- `para`: obrigatório, de 1 a **10** e-mails; `cc`: opcional, até **10**. Repetidos (sem diferenciar maiúsculas) são unificados.
- `assunto`: obrigatório, até 200 caracteres, sem quebra de linha. `corpo`: obrigatório, até 5000 caracteres, texto simples
  (as quebras de linha são preservadas).

Resposta 200:
```json
{ "enviadas": [300], "ignoradasSemXml": [2], "tamanhoZipBytes": 4164, "destinatarios": ["fiscal@cliente.com.br", "copia@cliente.com.br"] }
```
`enviadas` e `ignoradasSemXml` trazem o **número** das notas. Nota sem nenhum XML disponível fica fora do zip e vem em
`ignoradasSemXml`; se nenhuma nota tiver XML, nada é enviado (400). `destinatarios` = Para + Cc.

## Conteúdo do e-mail

O e-mail sai em **HTML padronizado** (cabeçalho com logotipo/nome da empresa, mensagem, quadro com as notas e o zip, rodapé com os dados da empresa) e também em texto simples. O texto do usuário é escapado; quebras de linha viram parágrafos e links http(s) viram links. Detalhes em `docs/email-modelo-html.md`.

- Remetente: a conta SMTP fixa da empresa (nome de exibição opcional, configurado na tela de Sistema). **Reply-To**: o e-mail do usuário logado (se ele tiver e-mail cadastrado).
- Anexo `nfe-xmls-<yyyyMMdd-HHmm>.zip`, uma pasta por nota:
  - `NF-<número>/<chave>-nfe.xml` (autorizado);
  - `NF-<número>/cancelamento.xml` (nota cancelada);
  - `NF-<número>/cce-<seq>.xml` (cada carta de correção aceita).
  Se duas notas selecionadas tiverem o mesmo número, a pasta da segunda vira `NF-<número>-<id>/`.

## Erros (`menssage`)

| Situação | Status | `menssage` |
|---|---|---|
| Sem notas / sem destinatário / sem assunto / sem corpo / textos acima do limite | 400 | mensagem do campo, com `errors[]` |
| Mais de 200 notas | 400 | `Selecione no máximo 200 notas fiscais por envio.` |
| E-mail inválido | 400 | `O e-mail informado em Para é inválido: <e-mail>.` (ou `Cc`) |
| Mais de 10 e-mails | 400 | `Informe no máximo 10 e-mails em Para.` (ou `Cc`) |
| Assunto com quebra de linha | 400 | `O assunto não pode conter quebras de linha.` |
| Nota sem número (rascunho sem numeração) | 400 | `Selecione apenas notas fiscais já emitidas: ...` |
| Nenhuma nota com XML | 400 | `Nenhuma das notas fiscais selecionadas possui XML disponível para envio.` |
| Zip acima de 20 MB | 400 | `O arquivo zip com os XMLs excede o tamanho máximo de 20 MB. Divida o envio em partes, selecionando menos notas.` |
| Nota inexistente | 404 | `Nota fiscal não encontrada.` |
| Servidor de e-mail não configurado (nada ativo no banco e sem `MAIL_*`) | 503 | `Envio de e-mail não configurado. Solicite ao administrador que configure o e-mail em Sistema.` |
| Senha salva ilegível (chave de criptografia trocada) | 503 | `Não foi possível ler a senha salva. Informe a senha novamente na configuração de e-mail.` |
| Falha do servidor SMTP (conexão, autenticação, recusa) | 502 | `Não foi possível enviar o e-mail: <motivo em português, ex.: falha de autenticação: usuário ou senha incorretos…> Verifique a configuração do e-mail em Sistema ou tente novamente mais tarde.` |

O envio é síncrono (timeout de 30 s no SMTP). O log registra quem enviou, para quem e quais notas (nunca a senha).

## Variáveis de ambiente (FALLBACK)

Usadas só se não houver configuração salva no banco (compatibilidade e testes). Uma configuração salva mas **inativa** desativa o envio, sem cair nas variáveis.

| Variável | Padrão | Descrição |
|---|---|---|
| `MAIL_HOST` | vazio | Servidor SMTP. **Vazio = envio desativado** (a aplicação sobe normalmente; só o envio responde 503) |
| `MAIL_PORT` | 587 | Porta |
| `MAIL_USERNAME` | vazio | Usuário SMTP (vazio = sem autenticação) |
| `MAIL_PASSWORD` | vazio | Senha (nunca no repositório) |
| `MAIL_FROM` | vazio | Remetente (vazio = usa `MAIL_USERNAME`) |
| `MAIL_STARTTLS` | true | STARTTLS (porta 587) |
| `MAIL_SSL` | false | SSL direto (porta 465; nesse caso `MAIL_STARTTLS=false`) |
| `MAIL_TIMEOUT_MS` | 30000 | Timeouts de conexão/leitura/escrita |
| `MAIL_MAX_NOTAS` | 200 | Máximo de notas por envio |
| `MAIL_MAX_ZIP_BYTES` | 20971520 | Tamanho máximo do zip (20 MB) |

### Gmail com senha de app

1. Na conta Google do remetente, ative a verificação em duas etapas.
2. Em Segurança → Senhas de app, gere uma senha para "E-mail" (16 caracteres).
3. Defina no ambiente do servidor (não no repositório):
   `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_USERNAME=conta@gmail.com`, `MAIL_PASSWORD=<senha de app>`,
   `MAIL_FROM=conta@gmail.com`, `MAIL_STARTTLS=true`.
4. Reinicie a aplicação. O Gmail limita o anexo a 25 MB; o limite do sistema é 20 MB.

Preferencialmente configure tudo pela tela de Sistema (Gmail: servidor `smtp.gmail.com`, porta 587, STARTTLS, senha de app). No desenvolvimento local também é possível usar as variáveis acima; sem nenhuma configuração o envio responde 503.
