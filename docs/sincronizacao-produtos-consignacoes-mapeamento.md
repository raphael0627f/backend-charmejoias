# Sincronização de produtos e cópia de consignações entre empresas — mapeamento de dependências

Levantamento feito sobre o código e as migrations (até a V53). Objetivo: saber **tudo de que um produto e uma consignação dependem**, para enviar para a outra empresa sem quebrar a consistência. Nada foi implementado ainda.

## 1. Premissa central: os IDs não servem como chave entre empresas

Cada banco gera seus próprios IDs (`UUID` em produtos/clientes/joias, `BIGSERIAL` nas demais tabelas). O mesmo produto terá IDs diferentes nas duas empresas. Toda referência precisa ser trocada por uma **chave natural** na hora de enviar e resolvida de novo no destino.

| Entidade | ID no banco | Chave natural a usar | Único no banco? |
|---|---|---|---|
| Produto | UUID | `sku` | **Não** (só validação no código: `ProdutoValidation.skuExistenteCreate`) |
| Variação do produto (`produtos_variacoes`) | BIGSERIAL | `sku` da variação | Sim (`uq_produtos_variacoes_sku`) |
| NCM | `codigo` (texto, 8 dígitos) | o próprio `codigo` | Sim (é a PK) |
| Categoria | BIGSERIAL | `nome` | Não (só validação no código, adicionada em 8fca319) |
| Fornecedor | BIGSERIAL | `nome` | Não (idem) |
| Grupo | BIGSERIAL | `nome` + `tipo` (`PRODUTO`/`CLIENTE`) | Não (idem, por tipo) |
| Variacao / ItemVariacao (atributos: cor, tamanho…) | BIGSERIAL | `variacao.nome` + `item.nome` (+`valor`) | Não |
| Mostruário | BIGSERIAL | `descricao` | Não (só validação no código; a limpeza dos repetidos na produção já foi feita) |
| Joia (se ainda existir separada) | UUID | `codigo` | Não |
| Cliente | UUID | **não se aplica**: vira um único cliente fixo do destino | — |
| Consignação | BIGSERIAL | id da origem guardado como referência (ver seção 6) | — |

## 2. Dependências de um Produto (o que precisa existir no destino antes)

```
Produto
├─ Ncm .................... FK ncm_codigo (opcional) — chave = código; o destino precisa ter o NCM cadastrado
├─ Categoria .............. FK categoria_id (obrigatória na criação)
├─ Fornecedor ............. FK fornecedor_id (opcional)
├─ Grupos (tipo PRODUTO) .. N:N produtos_grupos
├─ Imagem ................. campo imagem_url aponta para um ARQUIVO em disco (app.storage.produtos-imagens-dir)
└─ Variações (só tipo VARIAVEL)
   └─ ProdutoVariacao ..... sku próprio (único), preços, estoque, ativo, código de barras (único)
      └─ ProdutoVariacaoItem → ItemVariacao → Variacao   (a "combinação": ex. Cor=Prata, Tamanho=16)
```

Campos do próprio produto que viajam: `sku`, `referencia`, `nome`, `tipo`, `codigoBarras`, `precoCusto`, `precoVenda`, `quantidadeEstoque`, `ativo`, `observacoes`, `origemProduto`, `unidadeComercial`, `codigoSituacaoTributariaIcms/Pis/Cofins`.

**Ordem de criação no destino (dependências primeiro):**
NCM → Categoria → Fornecedor → Grupos (PRODUTO) → Variacao/ItemVariacao → Produto (com grupos) → ProdutoVariacao (com itens) → imagem.

Não precisam ir: usuários de auditoria (`created_by`/`updated_by`) e timestamps (o destino gera os próprios).

## 3. Dependências de uma Consignação

```
Consignacao
├─ Cliente ................ FK cliente_id → TROCADO por 1 cliente fixo escolhido no destino
├─ Mostruário ............. FK mostruario_id (obrigatório pela API; nullable no banco)
├─ campos próprios ........ descricao, tipo (PRODUTO|JOIA), dataEntrega, dataPreAcertoConsignacao,
│                           dataAcertoConsignacao, confirmada
└─ ConsignacaoProduto (itens)
   ├─ quantidade e preco ... "foto" do preço no momento — NÃO é lido do produto, precisa ir junto
   └─ Produto + ProdutoVariacao(opcional)   (tipo PRODUTO)    ← precisam existir no destino, por sku
      ou Joia                               (tipo JOIA)       ← precisa existir no destino, por codigo
```

**Consequência:** uma consignação só pode ser criada no destino se **todos** os produtos (e variações) dos seus itens já existirem lá. Por isso a sincronização de produtos vem antes, e o envio da consignação precisa de uma checagem prévia (seção 5).

### O que depende de uma consignação mas NÃO deve ir na cópia
Pelo que foi descrito (cópia só da consignação), ficam de fora: pré-acerto, acerto, vínculos de rota (`RotaConsignacaoDeixar/Trazer`), vínculo com a montagem, acerto de joia, notas fiscais e pedidos. Todos esses referenciam a consignação **pela origem**; no destino a consignação nasce "limpa" (sem pré-acerto/acerto). Confirmar se é isso mesmo (pergunta 5).

## 4. Pontos do código atual que afetam a consistência

1. **`produtos.sku` não é único no banco**, só no código. Antes de sincronizar, é preciso conferir duplicidade **nos dois bancos**; senão a chave natural é ambígua. (O SKU de variação é único no banco e é checado contra os SKUs de produto no código.)
2. **`codigo_barras` é único no banco** (produto e variação). Produto simples recebe código **gerado automaticamente** (`gerarCodigoBarrasUnico`) na criação. Ao copiar para o destino, o código de barras da origem pode **colidir** com outro produto de lá, ou o destino gerar um diferente do que está impresso nas etiquetas. Decisão necessária (pergunta 3).
3. **Item inativo bloqueia a consignação**: criar consignação usa `findByIdAndAtivo` para produto e cliente. Se um produto estiver inativo no destino, a consignação que o usa não pode ser criada (a regra de negócio e a sincronização precisam concordar: sincronizar o `ativo` também).
4. **Mostruário é obrigatório na API de consignação** (`@NotNull`) mas opcional no banco (consignações antigas). No destino ele precisa existir ou ser mapeado para um mostruário fixo.
5. **Consignação confirmada não pode ser editada** (`ObjetoEmUsoException`) e só confirma com cliente. Se a cópia chegar `confirmada=true`, no destino ela fica travada; se chegar `false`, é editável. Decisão necessária (pergunta 6).
6. **Estoque** (`quantidadeEstoque`) é só um campo editável, nenhuma regra o movimenta. Sincronizar sobrescreve o estoque do destino, que pode estar certo lá (pergunta 2).
7. **Joia ainda é entidade separada na origem** (tabela `joias`, tipo `JOIA` nas consignações e pedidos), mas o **destino já foi unificado** (Joia virou `Produto` com `classe = JOIA`; ver seção 7). Os produtos sincronizados chegam no destino como `JOIA`, então a origem só precisa enviar `Produto` (e não a tabela `joias`).
8. **Imagem** é um arquivo em pasta do servidor, não um dado do banco: precisa ser enviada como arquivo junto (ou ficar de fora).
9. **Versões diferentes do sistema nas duas empresas**: se o schema do destino for outro (ex.: a Joia já unificada lá e aqui não), o payload quebra. Enviar uma versão de contrato no pacote e recusar versão incompatível.
10. **Exclusão**: produtos são desativados, não excluídos (`activate`/`deactivate`), então "sumiu do cadastro" não precisa ser propagado — só o `ativo`.

## 5. Regras de consistência propostas

- **Pacote por consignação, validado antes de gravar (dry-run):** o destino responde com a lista do que falta (produtos/variações/mostruário/cliente inexistentes, SKU divergente, produto inativo) sem gravar nada.
- **Tudo ou nada:** a consignação e seus itens são gravados numa única transação; se um item falha, nada é criado.
- **Sincronização de produtos como pré-requisito automático:** ao enviar uma consignação, enviar junto (ou antes) os produtos dela que não existem no destino, na ordem da seção 2.
- **Resolver sempre por chave natural**, nunca por ID; criar o que faltar em categoria/fornecedor/grupo/atributos **ou** exigir mapeamento manual (pergunta 4).
- **Idempotência:** o destino guarda de onde veio cada consignação (empresa de origem + id de origem). Reenviar a mesma consignação não duplica: avisa ou atualiza, conforme a regra escolhida.
- **Conflito de produto existente (mesmo SKU, dados diferentes):** definir quem manda (origem sobrescreve, destino manda, ou pede confirmação).
- **Versão do contrato** no pacote (item 9 da seção 4).
- **Auditoria:** registrar no destino quem enviou, quando e o que foi criado/atualizado (as colunas `created_by` ficam com um usuário de integração).

## 6. Perguntas em aberto (afetam o desenho)

1. **Como as duas empresas se comunicam?** API do destino chamada pela origem (precisa de autenticação por token de integração e o destino acessível pela internet), ou arquivo exportado e importado manualmente?
2. **Sincronização de produtos:** em um sentido (origem → destino) ou nos dois? Sobrescreve **preço** e **estoque** do destino ou só cria os que faltam?
3. **Código de barras** dos produtos: copiar o da origem, ou o destino gera o seu?
4. **Cadastros de apoio ausentes no destino** (categoria, fornecedor, grupo, NCM, atributos de variação): criar automaticamente ou bloquear e pedir mapeamento?
5. A consignação copiada deve levar **só** os dados e itens, sem pré-acerto/acerto/rota/montagem, certo?
6. A consignação chega **confirmada ou não**? E as datas de pré-acerto/acerto vão junto?
7. **Cliente de destino:** um cliente fixo por consignação enviada (o usuário escolhe na hora) ou um cliente padrão configurado uma vez?
8. ~~Unificação Joia → Produto antes da sincronização~~ — respondido: o destino já é unificado e os produtos entram como `JOIA` (seção 7).
9. Perguntas novas da seção 7 (SKU no destino, tipo da consignação, tabela de correspondência).

## 7. Backend de destino (`D:\ERPeijopp-backend-beijo`) — o que muda no desenho

Li o código do destino (último commit `71ff41d`, migrations até a V47). Ele é um **ramo diferente** do sistema: já tem a unificação Joia → Produto, mas **não tem** rota, montagem de mostruário nem várias migrations da origem. As duas bases **não têm o mesmo schema** (a numeração das migrations diverge), então o contrato de troca precisa ser versionado e independente das tabelas.

### 7.1 Como o destino trata Produto e a classe JOIA
- `Produto.classe` (`PRODUTO` | `JOIA`) é obrigatório (`V42`). `joias` e `joias_grupos` foram removidas (`V44`).
- **SKU e código de barras são únicos por classe, não no sistema todo** (`V45`): `UNIQUE (classe, sku)` em variações, `UNIQUE (classe, codigo_barras)` em produto e variação. A validação de SKU também é por classe (`skuExistenteCreate(sku, classe)`), e o gerador de SKU e de código de barras já é por classe.
- Criar/editar produto de classe `JOIA` exige a permissão `JOIA_GERENCIAR` (`exigirPermissao`). O usuário da integração precisa ter essa permissão no destino.
- Consignação no destino valida a classe do item contra o tipo: **tipo `JOIA` só aceita item de classe `JOIA`** e tipo `PRODUTO` só aceita classe `PRODUTO` (`ConsignacaoValidation.validarClasse`).
- O item da consignação no destino tem um campo extra, `extra` (booleano, padrão `false`), que a origem não tem.

### 7.2 Consequências para a sincronização
1. **A chave natural no destino é `(classe = JOIA, sku)`.** Os produtos da origem (que lá são classe `PRODUTO`) entram no destino como `JOIA`, concorrendo com os SKUs das joias que o destino já tem. **Risco real de colisão**: um SKU da origem (ex. `0041P`) pode já existir como outra joia no destino.
2. **O SKU não é uma chave de correspondência segura entre as duas empresas** (pode colidir, e pode ser renomeado). Proposta: o destino guarda uma **tabela de correspondência** com o **ID de origem** (o UUID do produto, que é estável) → produto do destino, além de `empresa de origem`. A busca na hora de enviar consignação passa a ser por esse ID de origem, não por SKU.
3. **SKU do produto no destino**: duas opções. (a) manter o SKU da origem quando estiver livre na classe `JOIA` e bloquear/avisar na colisão; (b) o destino gera o próximo SKU da classe `JOIA` (`proximoSku`) e a tabela de correspondência liga os dois. A opção (b) nunca colide, mas os SKUs deixam de ser iguais nas duas empresas.
4. **Código de barras**: só pode colidir com outro código da classe `JOIA` do destino (não mais com o sistema todo). Se for copiar o da origem, a verificação é por `(JOIA, codigo_barras)`.
5. **Variações**: o SKU de cada variação também é único por classe no destino; a mesma decisão do item 3 vale para elas. A combinação (atributos) continua por nome de variação + nome do item.
6. **Consignação copiada vira tipo `JOIA` no destino**, porque os itens agora são classe `JOIA`. Uma consignação de origem do tipo `PRODUTO` é gravada como `JOIA`; uma de origem do tipo `JOIA` (tabela `joias` da origem) **não tem produto equivalente** para enviar e fica fora do escopo, a menos que a origem também seja unificada.
7. **Campos que existem só de um lado**: `extra` (só no destino) assume `false`; recursos como rota/montagem (só na origem) simplesmente não existem no destino e não fazem parte do pacote.
8. **Cliente, mostruário, categoria, fornecedor, grupo e NCM** seguem a mesma regra da seção 2 e da seção 3, resolvidos no destino por nome (a verificação de nome repetido já existe nos dois lados? **conferir no destino**; ela foi adicionada só na origem).

### 7.3 O que preciso confirmar antes de desenhar
- No destino, o SKU dos produtos migrados do tipo joia segue algum padrão (`0001`, `0002`…)? Ele tem espaço para receber os SKUs da origem sem colidir?
- Escolha entre manter o SKU da origem (com bloqueio na colisão) ou gerar um novo no destino com tabela de correspondência.
- Os produtos que chegam como `JOIA` devem manter `tipo` (`SIMPLES`/`VARIAVEL`) igual ao da origem, com as variações?
- O destino tem hoje `categoria`/`fornecedor`/`grupo` com nomes repetidos? (a checagem de nome repetido que fizemos aqui não está no destino.)
