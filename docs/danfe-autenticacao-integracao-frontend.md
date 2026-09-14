# DANFE avulso + Sistema de Autenticação (JWT) — guia de integração frontend

Este documento cobre duas entregas de backend:

1. **DANFE a partir de XML avulso** + **listagem/download dos XMLs de NFe salvos**.
2. **Sistema de autenticação completo** (login, refresh token, logout, cadastro de usuário, perfis e permissões) — **breaking change**: a partir de agora, **toda a API exige um token JWT válido**, exceto `/auth/login` e `/auth/refresh`.

---

## 1. DANFE a partir de XML avulso

Diferente do DANFE que já existia (`GET /notas-fiscais/{id}/danfe`, que só funciona pra notas emitidas por este sistema), este endpoint novo gera um DANFE a partir de **qualquer** XML de NFe já autorizada pela SEFAZ, mesmo de uma nota emitida fora do sistema. Não grava nada no banco — é uma conversão pura XML → PDF.

### `POST /notas-fiscais/danfe/gerar`

- **Content-Type**: `multipart/form-data`
- **Campo do form**: `xml` (arquivo)
- O XML enviado **precisa ser uma NFe já autorizada** (tag raiz `nfeProc` com `protNFe`/`chNFe`/`nProt` preenchidos). Um XML de rascunho/não autorizado é rejeitado.

**Resposta de sucesso**: `200`, `Content-Type: application/pdf`, `Content-Disposition: inline; filename=danfe-<chaveDeAcesso>.pdf` — dá pra abrir direto num `<iframe>`/nova aba, igual os outros relatórios PDF do sistema.

**Erros** (todos `400`, formato padrão `{timestamp, status, menssage, path}`):
| Situação | Mensagem |
|---|---|
| Nenhum arquivo enviado | "Arquivo XML é obrigatório" |
| Arquivo sem extensão `.xml` e sem content-type XML | "Arquivo deve ser um XML" |
| XML mal formado | "XML inválido ou malformado: ..." |
| XML sem `nfeProc` na raiz | "XML não é uma NFe autorizada (elemento raiz esperado: nfeProc)" |
| `nfeProc` sem `protNFe` | "XML não é uma NFe autorizada (protNFe ausente)" |
| Falta chave de acesso ou protocolo | "XML não é uma NFe autorizada (chave de acesso ou protocolo de autorização ausente)" |

Exemplo (fetch):
```js
const formData = new FormData();
formData.append("xml", arquivoXmlSelecionado);

const resp = await fetch("/notas-fiscais/danfe/gerar", {
  method: "POST",
  headers: { Authorization: `Bearer ${token}` }, // lembrar do token, ver seção 2
  body: formData,
});
const blob = await resp.blob(); // PDF
```

---

## 2. Listagem e download dos XMLs de NFe salvos

Os XMLs de nota autorizada/cancelamento/carta de correção sempre foram salvos em disco, mas não existia como consultar o que já tinha sido salvo. Agora dá.

### `GET /notas-fiscais/xmls?page=&size=`

Lista os arquivos `.xml` salvos, paginado, ordenado por data de modificação (mais recente primeiro). **Não é uma tabela do banco** — é literalmente uma listagem de diretório, então não tem filtro por texto/data nesta v1.

```json
{
  "content": [
    { "nomeArquivo": "26-33260907953085000171550020000000231049316517-NFe.xml", "tamanhoBytes": 7105, "ultimaModificacao": "2026-09-10T12:52:00.034" },
    { "nomeArquivo": "Cancelamento NFe-26.xml", "tamanhoBytes": 7503, "ultimaModificacao": "2026-09-10T12:52:00.146" }
  ],
  "totalElements": 2,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

### `GET /notas-fiscais/xmls/{nomeArquivo}`

Baixa um XML específico pelo nome exato retornado na listagem (usar o nome como veio, sem inventar path). `Content-Type: application/xml`, `Content-Disposition: attachment` (é pra baixar, não pra visualizar inline). `404` se o nome não existir; `400` se o nome for suspeito (contém `/`, `\` ou `..` — proteção contra path traversal, não é pra acontecer num uso normal da tela).

---

## 3. Sistema de autenticação — visão geral

**A partir de agora, toda a API exige `Authorization: Bearer <token>` em todo request**, exceto `/auth/login` e `/auth/refresh`. Sem token válido, qualquer endpoint responde `401`. **O frontend precisa implementar isso antes de qualquer outra tela voltar a funcionar.**

Conceitos:
- **Access token** (JWT): usado no header `Authorization: Bearer <token>` de toda requisição. Dura **15 minutos**.
- **Refresh token**: string opaca (não é JWT), usada só no endpoint `/auth/refresh` pra conseguir um access token novo sem pedir login de novo. Dura **7 dias**, mas **é de uso único** — cada chamada em `/auth/refresh` invalida o refresh token usado e devolve um par novo (access + refresh). **Guardar sempre o refresh token mais recente**, o anterior para de funcionar.
- **Login/senha**: usuário e senha de um cadastro de `Usuario` (não é o `Cliente`).
- **Perfil e permissões**: cada `Usuario` tem um `Perfil` (ex: "ADMIN", "VENDEDOR"), que agrupa um conjunto de `Permissao`. As permissões do usuário logado vêm dentro do próprio token (e em `/auth/me`), pro frontend decidir o que mostrar/esconder na UI.

### Estratégia recomendada para o frontend

1. Guardar `token` e `refreshToken` (ex: `localStorage`, ou memória + refresh cookie — decisão do frontend).
2. Anexar `Authorization: Bearer <token>` em toda chamada à API.
3. Quando um request voltar `401`, tentar `POST /auth/refresh` com o `refreshToken` guardado; se der certo, repetir o request original com o novo `token`. Se `/auth/refresh` também falhar (`400`), redirecionar pro login.
4. Como o access token dura só 15 minutos, isso vai acontecer com frequência normal de uso — vale a pena automatizar esse retry num interceptor único (axios interceptor, wrapper de fetch, etc.), não tratar caso a caso em cada tela.

---

## 4. Endpoints de autenticação (`/auth`)

### `POST /auth/login` — não exige token

```json
// request
{ "usuario": "admin", "senha": "admin123" }
```
```json
// response 200
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "OCXgSZoOuXwX79iXIHsqUv8wWzd6Dx-Z...",
  "expiresInSeconds": 899,
  "usuario": "admin",
  "perfil": "ADMIN",
  "permissoes": ["CLIENTE_GERENCIAR", "PRODUTO_GERENCIAR", "..."]
}
```
Senha errada ou usuário inativo → `401` `{"menssage": "Credenciais inválidas"}`.

### `POST /auth/refresh` — não exige token (usa o refresh token no corpo)

```json
// request
{ "refreshToken": "OCXgSZoOuXwX79iXIHsqUv8wWzd6Dx-Z..." }
```
Resposta: **mesmo formato de `/auth/login`** (novo `token` + novo `refreshToken` + `expiresInSeconds` + dados do usuário). Refresh token inexistente, já usado (rotacionado) ou expirado → `400` `{"menssage": "Refresh token inválido"}` ou `{"menssage": "Refresh token inválido ou expirado"}`.

### `POST /auth/logout` — exige token

```json
// request
{ "refreshToken": "..." }
```
`204` sem corpo. Revoga o refresh token informado **e** o access token que está sendo usado nessa mesma chamada (mesmo que ainda não tivesse expirado, ele para de funcionar imediatamente — testar depois do logout dá `401`). Chamar isso ao clicar em "Sair".

### `GET /auth/me` — exige token

Devolve o perfil/permissões de quem está logado — útil pra popular o estado da aplicação depois de um F5 (usando o token salvo) sem precisar logar de novo.
```json
{ "usuario": "admin", "perfil": "ADMIN", "permissoes": ["CLIENTE_GERENCIAR", "..."] }
```

---

## 5. Cadastro de usuário (`/usuarios`) — exige permissão `USUARIO_GERENCIAR`

Não existe tela de "criar minha conta" — só um admin (alguém com `USUARIO_GERENCIAR`) cadastra outro usuário, igual todo outro cadastro do sistema (Cliente, Fornecedor etc. também são cadastrados pela equipe).

### Campos do `Usuario`

| Campo | Obrigatório? | Observação |
|---|---|---|
| `usuario` | **sim** | nome de login, único no sistema |
| `senha` | **sim no create**; opcional no update | no `PUT`, deixar em branco/omitir = "não trocar a senha" |
| `primeiroNome` | não | |
| `sobrenome` | não | |
| `cpf` | não | |
| `documento` | não | |
| `celular` | não | |
| `email` | não | |
| `observacao` | não | |
| `endereco` | não | **um único endereço** (não é lista, diferente do `Cliente`) — mesmo shape de `EnderecoDTO` (`logradouro`, `numero`, `complemento`, `bairro`, `municipio`, `uf`, `cep`, `ibge`) |
| `perfilId` | não | id de um `Perfil` já cadastrado |
| `ativo` | não (default `false` se omitido) | soft-delete — usuário inativo não consegue logar |

A resposta (`UsuarioResponseDTO`) **nunca inclui `senha`**.

### Endpoints

- `GET /usuarios?busca=&ativo=&page=&size=` — `busca` filtra por `usuario`, `primeiroNome`, `sobrenome`, `email`.
- `GET /usuarios/{id}`
- `POST /usuarios` — só `USUARIO_GERENCIAR`
- `PUT /usuarios` — só `USUARIO_GERENCIAR`. Corpo precisa do `id`.
- `PUT /usuarios/activate/{id}` — reativa um usuário desativado
- `DELETE /usuarios/{id}` — soft-delete (desativa, não apaga)

Exemplo mínimo de criação:
```json
POST /usuarios
{ "usuario": "vendedor1", "senha": "senha123", "ativo": true }
```

Exemplo completo:
```json
POST /usuarios
{
  "usuario": "maria.souza",
  "senha": "SenhaForte123",
  "primeiroNome": "Maria",
  "sobrenome": "Souza",
  "cpf": "12345678900",
  "celular": "11999999999",
  "email": "maria@empresa.com",
  "ativo": true,
  "perfilId": 2,
  "endereco": { "logradouro": "Rua Tal", "numero": "100", "bairro": "Centro", "municipio": "São Paulo", "uf": "SP", "cep": "01000000" }
}
```

---

## 6. Cadastro de perfis (`/perfis`) — exige permissão `USUARIO_GERENCIAR`

Um `Perfil` é um nome (ex: "ADMIN", "VENDEDOR", "GERENTE") + um conjunto de `Permissao` (lista completa na seção 7). CRUD simples, sem soft-delete (é excluído de verdade).

- `GET /perfis?busca=&page=&size=` — `busca` filtra por `nome`.
- `GET /perfis/{id}`
- `POST /perfis`
- `PUT /perfis`
- `DELETE /perfis/{id}` — `409` se algum `Usuario` ainda estiver usando esse perfil.

```json
POST /perfis
{ "nome": "VENDEDOR", "permissoes": ["CLIENTE_GERENCIAR", "CONSIGNACAO_GERENCIAR"] }
```
```json
// response
{
  "id": 2,
  "nome": "VENDEDOR",
  "permissoes": ["CLIENTE_GERENCIAR", "CONSIGNACAO_GERENCIAR"],
  "createdAt": "...",
  "updatedAt": "..."
}
```

**Importante pro frontend**: se as permissões de um perfil forem editadas, quem já está logado com aquele perfil só recebe a mudança no próximo login ou refresh (o conjunto de permissões vai embutido no token, não é reconsultado a cada request). Não é um bug — é a troca feita conscientemente por performance.

---

## 7. Catálogo de permissões (`Permissao`)

Uma permissão por ação de escrita/sensível de cada módulo. **Leitura (`GET`) nunca exige permissão específica — só estar logado.** Use essa lista pra montar a tela de "editar perfil" (checkboxes) e pra decidir o que esconder/desabilitar na UI conforme `permissoes` do usuário logado:

| Permissão | Libera |
|---|---|
| `USUARIO_GERENCIAR` | Criar/editar/desativar `Usuario`; criar/editar/excluir `Perfil` |
| `CLIENTE_GERENCIAR` | Criar/editar/ativar/desativar `Cliente` |
| `PRODUTO_GERENCIAR` | Criar/editar/ativar/desativar `Produto`; upload e exclusão de imagem |
| `JOIA_GERENCIAR` | Criar/editar/ativar/desativar `Joia` |
| `CATEGORIA_GERENCIAR` | Criar/editar/ativar/desativar `Categoria` |
| `FORNECEDOR_GERENCIAR` | Criar/editar/ativar/desativar `Fornecedor` |
| `GRUPO_GERENCIAR` | Criar/editar/ativar/desativar `Grupo` |
| `NCM_GERENCIAR` | Criar/editar/ativar/desativar `Ncm` |
| `TRANSPORTADORA_GERENCIAR` | Criar/editar/ativar/desativar `Transportadora` |
| `EMPRESA_GERENCIAR` | Criar/editar `Empresa`; enviar certificado digital |
| `MOSTRUARIO_GERENCIAR` | Criar/editar/excluir `Mostruario` |
| `COMISSAO_GERENCIAR` | Criar/editar/excluir `Comissao` |
| `CONSIGNACAO_GERENCIAR` | Criar/editar/confirmar `Consignacao`; criar/editar `PreAcertoConsignacao` |
| `ACERTO_CONSIGNACAO_GERENCIAR` | Criar/editar/confirmar `AcertoConsignacao` |
| `ACERTO_JOIA_GERENCIAR` | Criar/editar `AcertoJoia` |
| `NOTA_FISCAL_GERENCIAR` | Criar/editar rascunho de `NotaFiscal` |
| `NOTA_FISCAL_EMITIR` | Emitir NFe (`POST /notas-fiscais/{id}/emitir`) |
| `NOTA_FISCAL_CANCELAR` | Cancelar NFe |
| `NOTA_FISCAL_CORRIGIR` | Emitir carta de correção |
| `SPC_CONSULTAR` | Fazer consulta SPC (`POST /spc/consultas`) |
| `SPC_REGISTRAR` | Incluir/excluir devedor no SPC |

**Fora de escopo nesta v1** (não confundir com bug): o DANFE avulso, a listagem de XMLs, e a atualização de status de NFe (`POST /notas-fiscais/{id}/status`) não têm permissão própria — só exigem estar autenticado, igual qualquer leitura.

---

## 8. Erros de autenticação/autorização — formato

Mesmo formato de erro já usado no resto da API:
```json
{ "timestamp": "...", "status": "401 UNAUTHORIZED", "menssage": "Credenciais inválidas ou ausentes", "path": "uri=/clientes" }
```
- **`401`**: token ausente, expirado, malformado, ou revogado (logout) — ação do frontend: tentar `/auth/refresh`; se falhar também, mandar pro login.
- **`403`**: token válido, mas o usuário não tem a permissão exigida por aquele endpoint — ação do frontend: **não** tentar refresh (não vai resolver), mostrar mensagem de acesso negado. Dá pra evitar isso na origem escondendo/desabilitando botões conforme `permissoes` do usuário (seção 7).

---

## 9. Usuário admin de bootstrap

Pra o primeiro acesso ao sistema (nenhum usuário existia antes dessa feature), já existe um usuário seedado:
- **usuario**: `admin`
- **senha**: `admin123`
- **perfil**: `ADMIN`, com todas as permissões da seção 7.

**Trocar essa senha assim que possível** (via `PUT /usuarios` com o `id` do admin e um novo `senha`) — ela é conhecida/documentada de propósito só pra destravar o primeiro login.
