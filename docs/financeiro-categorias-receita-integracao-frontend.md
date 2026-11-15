# Financeiro 6.1 — Fase 1: categorias de receita, permissões do Contas a Receber e origens no extrato

Migration **V67** (nova; as migrations V64 a V66 não foram alteradas). Todas as rotas sem prefixo `/api`; erros no formato
`{ "timestamp", "status", "menssage", "path" }`. Permissões novas só vão para o perfil `ADMIN`: **é necessário relogar** para o token trazê-las.

## Permissões novas

| Permissão | Finalidade |
| --- | --- |
| `CONTA_RECEBER_VISUALIZAR` | Consultar títulos a receber (fases seguintes) e ler os cadastros usados por eles |
| `CONTA_RECEBER_GERENCIAR` | Criar, editar, alterar vencimento e cancelar títulos a receber |
| `CONTA_RECEBER_RECEBER` | Registrar recebimentos (inclusive em lote) |
| `CONTA_RECEBER_ESTORNAR` | Estornar recebimentos. **Isolada não concede acesso às telas** (como `CONTA_PAGAR_ESTORNAR`) |

Quem recebe: apenas o perfil `ADMIN` (seed da V67); os demais perfis são configurados na tela de Perfis. Rótulos sugeridos para a tela de Perfis:
"Contas a Receber: visualizar", "Contas a Receber: gerenciar", "Contas a Receber: receber" e "Contas a Receber: estornar recebimentos".

Leitura dos cadastros compartilhados (`GET /bancos`, `GET /formas-pagamento`, `GET /centros-custo`): passam a aceitar também
`CONTA_RECEBER_VISUALIZAR`, `CONTA_RECEBER_GERENCIAR` e `CONTA_RECEBER_RECEBER`, além das permissões que já liam (Contas a Pagar e `CADASTRO_FINANCEIRO_GERENCIAR`).
A escrita dos cadastros continua exclusiva de `CADASTRO_FINANCEIRO_GERENCIAR`.

## Categorias de receita: `/categorias-receita`

Mesmo padrão das categorias de despesa (`/categorias-despesa`), em **dois níveis** (grupo > subgrupo), **sem o campo `tipo`** (a receita não é classificada por custo/despesa).

| Operação | Rota | Permissão |
| --- | --- | --- |
| Árvore (grupos com `subcategorias`) | `GET /categorias-receita?ativo=true\|false` | Qualquer `CONTA_RECEBER_VISUALIZAR/GERENCIAR/RECEBER`, `CONTA_PAGAR_VISUALIZAR/GERENCIAR/PAGAR` ou `CADASTRO_FINANCEIRO_GERENCIAR` |
| Lista plana (grupo seguido dos subgrupos) | `GET /categorias-receita/plana?ativo=&busca=` | idem |
| Detalhe | `GET /categorias-receita/{id}` | idem |
| Criar grupo ou subgrupo | `POST /categorias-receita` (201 + `Location`) | `CADASTRO_FINANCEIRO_GERENCIAR` |
| Editar | `PUT /categorias-receita/{id}` | `CADASTRO_FINANCEIRO_GERENCIAR` |
| Reativar | `PATCH /categorias-receita/{id}/ativar` | `CADASTRO_FINANCEIRO_GERENCIAR` |
| Desativar | `PATCH /categorias-receita/{id}/inativar` (nome técnico mantido; no texto exibido: "Desativar") | `CADASTRO_FINANCEIRO_GERENCIAR` |
| Excluir | `DELETE /categorias-receita/{id}` (204) | `CADASTRO_FINANCEIRO_GERENCIAR` |

### Corpo de criação/edição
```json
{ "nome": "Vendas de joias", "paiId": 1, "ordem": 1 }
```
- `nome` obrigatório (até 100 caracteres); `paiId` nulo = grupo (nível 1); `paiId` de um grupo = subgrupo (nível 2); `ordem` opcional (padrão 0).

### Resposta
```json
{ "id": 2, "nome": "Vendas de joias", "paiId": 1, "paiNome": "Vendas", "ordem": 1, "ativo": true,
  "createdAt": "...", "updatedAt": "...", "createdBy": {...}, "updatedBy": {...},
  "subcategorias": [ ... ] }
```
`subcategorias` só aparece na árvore (grupos); na lista plana e no detalhe o campo é omitido.

### Regras
- No máximo **dois níveis**: o pai precisa ser um grupo; um subgrupo não pode ser pai (**400**); um grupo com subgrupos não pode se tornar subgrupo (**400**); uma categoria não pode ser pai de si mesma (**400**).
- Nome **único dentro do mesmo pai**, sem diferenciar maiúsculas/minúsculas e com espaços nas pontas ignorados (**409**: `Já existe um grupo de categorias de receita com o nome "X".` ou `Já existe uma categoria de receita com o nome "X" no grupo "Y".`).
- Criar subgrupo em grupo desativado: **400** (`O grupo "X" está desativado.`).
- Desativar grupo com subgrupos ativos: **409** (`Não é possível desativar o grupo "X", pois ele possui subgrupos ativos. Desative os subgrupos primeiro.`).
- Reativar subgrupo com grupo desativado: **409** (`Não é possível reativar o subgrupo, pois o grupo "X" está desativado. Reative o grupo antes de reativar o subgrupo.`).
- Excluir grupo com subgrupos: **409**. Excluir categoria **em uso** por título a receber: **409** (a verificação de uso será ligada ao título a receber na Fase 2; **nesta fase não há uso**, portanto a exclusão de categoria sem subgrupos sempre é permitida).
- `GET ?ativo=true` filtra também os subgrupos; um grupo desativado some junto com os seus subgrupos.

### Sementes (V67)
Vendas (Vendas de joias; Vendas de semijoias; Vendas de acessórios) · Consignação (Acertos de consignação; Diferenças de acerto) ·
Serviços (Reparos e consertos; Personalização e gravação; Outros serviços) · Receitas financeiras (Juros recebidos; Multas recebidas; Rendimentos de aplicações) ·
Outras receitas (Recuperação de despesas; Venda de bens do ativo; Receitas diversas). Total: 5 grupos e 14 subgrupos, todos editáveis.

## Origens novas no extrato (`GET /movimentacoes-banco`)

O filtro `origem` e o campo `origem` da movimentação passam a aceitar também:
- `RECEBIMENTO_CONTA_RECEBER`: **entrada** gerada por um recebimento de título a receber;
- `ESTORNO_RECEBIMENTO`: **saída** que reverte um recebimento estornado.

Nesta fase nenhuma movimentação com essas origens é gerada (o recebimento é da Fase 3); a coluna de ligação ao título a receber também fica para a Fase 3.
Movimentações dessas origens não poderão ser estornadas pela tela de movimentações (o estorno será feito no recebimento), como já ocorre com as do Contas a Pagar.

## Autorização antes da validação (403 antes de 400)

O interceptor de autorização do Financeiro passa a cobrir `/categorias-receita/**` e `/contas-receber/**`: usuário sem permissão recebe **403** mesmo com corpo vazio ou inválido.

## Erros

| Situação | Status |
| --- | --- |
| Sem token | 401 |
| Sem permissão | 403 |
| Nome vazio/maior que 100, terceiro nível, pai inexistente ou desativado, categoria pai de si mesma | 400 |
| Categoria não encontrada | 404 |
| Nome duplicado no mesmo pai, desativar grupo com subgrupos ativos, reativar subgrupo com grupo desativado, excluir grupo com subgrupos | 409 |
