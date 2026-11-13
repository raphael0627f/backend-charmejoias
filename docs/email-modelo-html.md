# E-mails em HTML — modelo padronizado

Todos os e-mails enviados pelo sistema (envio de XMLs das notas fiscais e e-mail de teste de **Sistema > E-mail**) saem
em **HTML com design padronizado** e também em **texto simples** (`multipart/alternative`; o cliente de e-mail escolhe).
O modelo é o serviço `com.manager.app.email.EmailTemplateService`.

## Estrutura do e-mail

- **Cabeçalho:** faixa fina dourada + faixa escura (`#1F1F23`) com o **logotipo** (inline por CID, `src="cid:logo"`) e o nome da
  empresa em dourado. Sem logotipo, só o nome da empresa em texto.
- **Corpo:** título (o assunto), a mensagem do usuário em parágrafos (linha em branco = novo parágrafo; quebra simples = `<br>`),
  e um **quadro de resumo** (título + linhas rótulo/valor).
  - **Envio de XML:** "N nota(s) fiscal(is) em anexo", números (`NF 300, NF 301`), nome e tamanho do zip; notas sem XML aparecem
    em "Sem XML disponível (não incluídas)".
  - **Teste:** explica que é uma mensagem de teste e mostra servidor:porta, segurança, autenticação e remetente (**nunca a senha**).
- **Rodapé:** razão social, CNPJ, telefone e endereço do cadastro de **Empresa** (o que estiver vazio é omitido) e a frase
  "Mensagem enviada automaticamente pelo sistema. Em caso de dúvida, responda a este e-mail."
- Mantidos: anexo zip, Reply-To (e-mail do usuário logado), From com nome de exibição, Cc.

## Segurança do conteúdo

Todo texto digitado pelo usuário (assunto, mensagem, valores do resumo) é **escapado** (`& < > " '`): nenhum HTML/script dele chega
ao e-mail. Só links `http://` e `https://` viram `<a>` (com `rel="noopener noreferrer"`, `target="_blank"`); `javascript:` e outros
esquemas permanecem texto. A pontuação final colada ao link fica fora dele.

## Aparência e compatibilidade

- Layout em **tabelas com estilos inline**, sem CSS externo, JavaScript nem imagens externas; `lang="pt-BR"`, meta viewport,
  charset UTF-8, `alt` na imagem; largura máxima 600 px (atributo `width` para o Outlook + `max-width`), fluido no celular;
  Arial/Helvetica 14–15 px; palavras longas quebram (links, nomes de arquivo).
- **Paleta** (dourado = cor primária do sistema, `--brand-pink: #d4af37` no `globals.css`):

| Uso | Cor |
|---|---|
| Destaque (faixas, detalhes sobre o escuro) | `#D4AF37` |
| Dourado escuro (títulos do resumo e links sobre branco, contraste AA) | `#7A5C00` |
| Cabeçalho | `#1F1F23` |
| Fundo geral / cartão | `#F4F4F5` / `#FFFFFF` com borda `#E4E4E7` |
| Texto / secundário / rodapé | `#27272A` / `#71717A` / `#52525B` |
| Fundo do resumo | `#FBF8EC` |

## Dados da Empresa e logotipo

- Empresa: `Empresa` (razão social, nome fantasia, CNPJ, telefone, endereço). No envio de XML usa-se a empresa da (primeira) nota
  incluída; no e-mail de teste, a primeira empresa cadastrada. **O cadastro não tem e-mail nem site** (por isso não aparecem no
  rodapé) **nem logotipo**.
- Logotipo: por padrão `src/main/resources/images/email-logo.png` (classpath, vai dentro do jar), versão leve (216x240, ~50 KB) da
  logo oficial Charme Jóias (`images/logo_charme.png`, a mesma do relatório de pedido, que permanece intacta). `MAIL_LOGO_PATH`
  é um override opcional (arquivo PNG, JPG ou GIF até 512 KB; SVG não funciona em e-mail), exibido em 56 px de altura sobre
  um quadro branco. Se a imagem não puder ser lida, o e-mail sai com o nome da empresa em texto.

## Como acrescentar um novo e-mail

1. Injete `EmailTemplateService` no serviço que envia.
2. Monte o conteúdo:
   ```java
   var modelo = new EmailTemplateService.Modelo("Título", mensagemDoUsuarioOuNulo, "Título do resumo (ou nulo)",
           List.of(new EmailTemplateService.Linha("Rótulo", "Valor")),
           templateService.empresaPorId(empresaId));   // ou empresaPadrao()
   var conteudo = templateService.montar(modelo);
   ```
3. Crie o helper em modo `MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED` e chame `templateService.aplicar(helper, conteudo)`
   **antes** de `addAttachment` (define texto + HTML e o logotipo inline).
4. Resolva o remetente/servidor com `ConfiguracaoEmailService.resolverParaEnvio()` e `definirRemetente(...)`.

Exemplos: `NotaFiscalEmailXmlService` (com anexo) e `ConfiguracaoEmailService.testar` (sem anexo).

## Testes e pré-visualização

- Unitários: `EmailTemplateServiceTest` (escape, parágrafos, links, rodapé, logo, multipart).
- Preview HTML: `EMAIL_PREVIEW_PATH=<arquivo.html> ./mvnw test -Dtest=EmailTemplateServiceTest#gerarPreview` (o `cid:` vira data URI).
- Envio real contra SMTP local de teste: `EMAIL_SMTP_FAKE=localhost:2525 ./mvnw test -Dtest=NotaFiscalEnvioXmlSmtpFakeTest`.
- **Não validado em clientes reais** (Gmail, Outlook, Apple Mail): o layout segue as boas práticas de compatibilidade, mas convém
  um envio real para conferir.
