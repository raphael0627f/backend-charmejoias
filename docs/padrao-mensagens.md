# Padrão de mensagens ao usuário (backend)

Aplica-se às mensagens de erro, validação, conflito e acesso negado devolvidas pela API (campo `menssage`,
`@NotBlank/@NotNull/@Size message=`, exceções de negócio). Logs internos, PDFs, etiquetas, XLSX e migrations não fazem parte.

## Regras

1. Tom profissional e objetivo, sem gírias, contrações, exclamações ou frases rebuscadas.
2. Frases curtas, terminadas em ponto. Dizer o que ocorreu e, quando útil, o que fazer.
3. Português correto e com acentuação, com concordância de gênero (pedido **encontrado**, rota **encontrada**).
4. Mesmo termo para a mesma coisa: Cliente, Produto, Consignação, Pré-acerto, Acerto, Montagem, Planejador de rotas, Mostruário.
   No meio da frase, em minúsculas (exceto siglas: SKU, NCM, CNPJ, CFOP, UF, DANFE).
5. Nomes técnicos de campo (`clienteId`, `refreshToken`, `yyyy-MM-dd`) não aparecem para o usuário.
6. Datas nas mensagens: `dd/MM/aaaa` (ex.: `Já existe um marcador na data 04/02/2031.`), via `TextoUtils.data(...)`.
   Ao pedir uma data ao usuário, orientar o formato `aaaa-mm-dd` (formato aceito pela API).
7. Identificadores técnicos não aparecem para o usuário final: nos 404 e em "em uso" o `Id: N`/`Código: X` fica só no log
   (o `RestExceptionHandler` o remove da resposta; a exceção continua com o detalhe completo para o suporte).
   Nos demais erros de negócio o rótulo, quando existir, é capitalizado: `Id: 5`, `Código: X`, `SKU: X`.
8. Mantêm-se os mesmos parâmetros e a mesma exceção/status HTTP; só o texto muda.

## Modelos

| Situação | Modelo |
|---|---|
| Não encontrado (404) | `Cliente não encontrado.` |
| Já existe (409) | `Já existe um cliente cadastrado com este código: X.` |
| Em uso (409) | `Não é possível desativar a categoria, pois ela está em uso.` |
| Campo obrigatório | `O campo Nome é obrigatório.` |
| Regra de negócio (400) | `A consignação já está confirmada. Id: 5` |
| Orientação | `Conclua ou cancele essa montagem antes de criar outra.` |

## Exemplos (antes → depois)

| Antes | Depois |
|---|---|
| `Pedido não encontrada. id: 5` | `Pedido não encontrado.` |
| `Cliente existente com este código: X` | `Já existe um cliente cadastrado com este código: X` |
| `Categoria está em uso e não pode ser desativada` | `Não é possível desativar a categoria, pois ela está em uso.` |
| `Código é obrigatório` | `O campo Código é obrigatório.` |
| `Refresh token inválido` | `Token de atualização inválido.` |
| `Marcador do tipo TRABALHA exige uma rota (rotaId)` | `Marcador do tipo TRABALHA exige uma rota.` |
| `Período obrigatório: informe inicio e fim (yyyy-MM-dd)` | `O período é obrigatório. Informe as datas inicial e final no formato aaaa-mm-dd.` |
| `Acesso negado` | `Acesso negado.` |

## Erros do Spring MVC (handlers em `RestExceptionHandler`)

Todos devolvem `RestErrorMenssage` (`menssage`, `status`, `path`, `timestamp`). Validação de corpo/parâmetros inclui também
`errors: [{ "field": "...", "defaultMessage": "..." }]` (formato consumido pelo frontend em `parseApiError`).

| Situação | Status | `menssage` |
|---|---|---|
| Bean validation (`@Valid`) | 400 | primeira mensagem da lista (`O campo Nome é obrigatório.`) + `errors[]` |
| JSON inválido / corpo ausente | 400 | `O corpo da requisição está ausente ou mal formatado. Revise os dados informados.` |
| Tipo de parâmetro/path inválido | 400 | `O valor informado para id é inválido.` |
| Parâmetro obrigatório ausente | 400 | `O parâmetro x é obrigatório.` |
| Rota inexistente | 404 | `Recurso não encontrado.` |
| Método HTTP não permitido | 405 | `Método não permitido para este recurso.` |

Toda anotação `@NotNull/@NotBlank/@NotEmpty/@Size` de DTO deve ter `message` em português.
