package com.manager.app.fluxocaixa;

import com.manager.app.fluxocaixa.dto.FluxoCaixaDTOs;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// consultas agregadas do fluxo de caixa (SQL nativo, tudo somado no banco; nada é carregado em memória além das linhas já agrupadas por dia).
// REALIZADO parte dos recebimentos/pagamentos (não estornados, sem crédito do cliente) e NÃO da movimentação bancária, que é o mesmo fato.
@Repository
@RequiredArgsConstructor
public class FluxoCaixaRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public enum Lado {
        RECEBER, PAGAR
    }

    public record AtrasadoLinha(BigDecimal valor, long quantidade) {
    }

    public record PrevistoPagar(Map<LocalDate, BigDecimal> total, Map<LocalDate, BigDecimal> estimado) {
    }

    public record CategoriaLinha(Long id, String nome, String pai, BigDecimal valor) {
    }

    public record BancoLinha(Long id, String nome) {
    }

    // ---------- filtros ----------

    private static void filtrosTitulo(Lado lado, FluxoCaixaFiltro f, StringBuilder sql, MapSqlParameterSource p) {
        if (!f.centroCustoIds().isEmpty()) {
            sql.append(" AND t.centro_custo_id IN (:cc)");
            p.addValue("cc", f.centroCustoIds());
        }
        List<Long> categorias = lado == Lado.RECEBER ? f.categoriaReceitaIds() : f.categoriaDespesaIds();
        if (!categorias.isEmpty()) {
            sql.append(" AND t.categoria_id IN (:cat)");
            p.addValue("cat", categorias);
        }
        if (f.clienteId() != null) {
            sql.append(" AND t.cliente_id = :cliente");
            p.addValue("cliente", f.clienteId());
        }
        if (lado == Lado.PAGAR && f.favorecido() != null) {
            sql.append(" AND LOWER(t.favorecido) = :favorecido");
            p.addValue("favorecido", f.favorecido().toLowerCase(Locale.ROOT));
        }
    }

    private static void filtroBanco(String alias, FluxoCaixaFiltro f, StringBuilder sql, MapSqlParameterSource p) {
        if (!f.bancoIds().isEmpty()) {
            sql.append(" AND ").append(alias).append(".banco_id IN (:bancos)");
            p.addValue("bancos", f.bancoIds());
        }
    }

    private static final String SALDO_ABERTO_RECEBER = "(t.valor - t.valor_recebido)";
    private static final String SALDO_ABERTO_PAGAR = "(t.valor - t.valor_pago)";

    private Map<LocalDate, BigDecimal> porDia(String sql, MapSqlParameterSource p) {
        Map<LocalDate, BigDecimal> m = new LinkedHashMap<>();
        jdbc.query(sql, p, rs -> {
            m.put(rs.getObject("dia", LocalDate.class), rs.getBigDecimal("valor"));
        });
        return m;
    }

    // ---------- realizado ----------

    public Map<LocalDate, BigDecimal> realizadoReceber(FluxoCaixaFiltro f, LocalDate de, LocalDate ate) {
        MapSqlParameterSource p = new MapSqlParameterSource("de", de).addValue("ate", ate);
        StringBuilder sql = new StringBuilder("SELECT x.data_recebimento AS dia, COALESCE(SUM(x.valor_total), 0) AS valor FROM conta_receber_recebimento x "
                + "JOIN conta_receber t ON t.id = x.conta_receber_id WHERE x.estornada = false AND x.origem <> 'CREDITO_CLIENTE' "
                + "AND x.data_recebimento BETWEEN :de AND :ate");
        filtroBanco("x", f, sql, p);
        filtrosTitulo(Lado.RECEBER, f, sql, p);
        sql.append(" GROUP BY x.data_recebimento");
        return porDia(sql.toString(), p);
    }

    public Map<LocalDate, BigDecimal> realizadoPagar(FluxoCaixaFiltro f, LocalDate de, LocalDate ate) {
        MapSqlParameterSource p = new MapSqlParameterSource("de", de).addValue("ate", ate);
        StringBuilder sql = new StringBuilder("SELECT x.data_pagamento AS dia, COALESCE(SUM(x.valor_total), 0) AS valor FROM conta_pagar_pagamento x "
                + "JOIN conta_pagar t ON t.id = x.conta_pagar_id WHERE x.estornada = false AND x.data_pagamento BETWEEN :de AND :ate");
        filtroBanco("x", f, sql, p);
        filtrosTitulo(Lado.PAGAR, f, sql, p);
        sql.append(" GROUP BY x.data_pagamento");
        return porDia(sql.toString(), p);
    }

    // ---------- previsto (saldo em aberto por vencimento) ----------

    public Map<LocalDate, BigDecimal> previstoReceber(FluxoCaixaFiltro f, LocalDate de, LocalDate ate) {
        MapSqlParameterSource p = new MapSqlParameterSource("de", de).addValue("ate", ate);
        StringBuilder sql = new StringBuilder("SELECT t.vencimento AS dia, COALESCE(SUM" + SALDO_ABERTO_RECEBER + ", 0) AS valor FROM conta_receber t "
                + "WHERE t.situacao <> 'CANCELADO' AND t.valor > t.valor_recebido AND t.vencimento BETWEEN :de AND :ate");
        filtrosTitulo(Lado.RECEBER, f, sql, p);
        sql.append(" GROUP BY t.vencimento");
        return porDia(sql.toString(), p);
    }

    public PrevistoPagar previstoPagar(FluxoCaixaFiltro f, LocalDate de, LocalDate ate) {
        MapSqlParameterSource p = new MapSqlParameterSource("de", de).addValue("ate", ate);
        StringBuilder sql = new StringBuilder("SELECT t.vencimento AS dia, COALESCE(SUM" + SALDO_ABERTO_PAGAR + ", 0) AS valor, "
                + "COALESCE(SUM(CASE WHEN t.estimado THEN " + SALDO_ABERTO_PAGAR + " ELSE 0 END), 0) AS estimado FROM conta_pagar t "
                + "WHERE t.situacao <> 'CANCELADO' AND t.valor > t.valor_pago AND t.vencimento BETWEEN :de AND :ate");
        filtrosTitulo(Lado.PAGAR, f, sql, p);
        sql.append(" GROUP BY t.vencimento");
        Map<LocalDate, BigDecimal> total = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> estimado = new LinkedHashMap<>();
        jdbc.query(sql.toString(), p, rs -> {
            LocalDate dia = rs.getObject("dia", LocalDate.class);
            total.put(dia, rs.getBigDecimal("valor"));
            estimado.put(dia, rs.getBigDecimal("estimado"));
        });
        return new PrevistoPagar(total, estimado);
    }

    // ---------- atrasados (vencimento anterior a hoje, saldo em aberto) ----------

    public AtrasadoLinha atrasados(Lado lado, FluxoCaixaFiltro f, LocalDate hoje) {
        MapSqlParameterSource p = new MapSqlParameterSource("hoje", hoje);
        boolean receber = lado == Lado.RECEBER;
        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM" + (receber ? SALDO_ABERTO_RECEBER : SALDO_ABERTO_PAGAR) + ", 0) AS valor, COUNT(*) AS qtd FROM "
                + (receber ? "conta_receber" : "conta_pagar") + " t WHERE t.situacao <> 'CANCELADO' AND t.valor > "
                + (receber ? "t.valor_recebido" : "t.valor_pago") + " AND t.vencimento < :hoje");
        filtrosTitulo(lado, f, sql, p);
        return jdbc.queryForObject(sql.toString(), p, (rs, n) -> new AtrasadoLinha(rs.getBigDecimal("valor"), rs.getLong("qtd")));
    }

    // ---------- saldos (única fonte: movimentação bancária) ----------

    private static final String SINAL = "CASE WHEN m.tipo = 'ENTRADA' THEN m.valor ELSE -m.valor END";

    public BigDecimal saldoAte(List<Long> bancoIds, LocalDate antesDe) {
        MapSqlParameterSource p = new MapSqlParameterSource("data", antesDe);
        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(" + SINAL + "), 0) FROM movimentacao_banco m WHERE m.data < :data");
        if (!bancoIds.isEmpty()) {
            sql.append(" AND m.banco_id IN (:bancos)");
            p.addValue("bancos", bancoIds);
        }
        return jdbc.queryForObject(sql.toString(), p, BigDecimal.class);
    }

    public Map<LocalDate, BigDecimal> movimentacaoLiquida(List<Long> bancoIds, LocalDate de, LocalDate ate) {
        MapSqlParameterSource p = new MapSqlParameterSource("de", de).addValue("ate", ate);
        StringBuilder sql = new StringBuilder("SELECT m.data AS dia, COALESCE(SUM(" + SINAL + "), 0) AS valor FROM movimentacao_banco m WHERE m.data BETWEEN :de AND :ate");
        if (!bancoIds.isEmpty()) {
            sql.append(" AND m.banco_id IN (:bancos)");
            p.addValue("bancos", bancoIds);
        }
        sql.append(" GROUP BY m.data");
        return porDia(sql.toString(), p);
    }

    // nomes para o cabeçalho da exportação (tabela vem de lista fixa, nunca do usuário)
    public List<String> nomesPorId(String tabela, List<Long> ids) {
        if (ids.isEmpty() || !List.of("categoria_receita", "categoria_despesa", "centro_custo").contains(tabela)) return List.of();
        return jdbc.query("SELECT nome FROM " + tabela + " WHERE id IN (:ids) ORDER BY LOWER(nome)", new MapSqlParameterSource("ids", ids), (rs, n) -> rs.getString("nome"));
    }

    public String nomeCliente(java.util.UUID id) {
        List<String> r = jdbc.query("SELECT nome FROM clientes WHERE id = :id", new MapSqlParameterSource("id", id), (rs, n) -> rs.getString("nome"));
        return r.isEmpty() ? null : r.get(0);
    }

    public String nomeUsuario(String login) {
        List<String> r = jdbc.query("SELECT TRIM(COALESCE(primeiro_nome, '') || ' ' || COALESCE(sobrenome, '')) AS nome FROM usuarios WHERE usuario = :u", new MapSqlParameterSource("u", login),
                (rs, n) -> rs.getString("nome"));
        return r.isEmpty() || r.get(0) == null || r.get(0).isBlank() ? login : r.get(0);
    }

    public List<BancoLinha> bancos(List<Long> bancoIds) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = "SELECT b.id, b.nome FROM banco b" + (bancoIds.isEmpty() ? "" : " WHERE b.id IN (:ids)") + " ORDER BY LOWER(b.nome)";
        if (!bancoIds.isEmpty()) p.addValue("ids", bancoIds);
        return jdbc.query(sql, p, (rs, n) -> new BancoLinha(rs.getLong("id"), rs.getString("nome")));
    }

    // [bancoId -> valor] do saldo antes de uma data, da movimentação líquida e dos recebimentos/pagamentos do período
    public Map<Long, BigDecimal> saldoPorBancoAte(List<Long> bancoIds, LocalDate antesDe) {
        MapSqlParameterSource p = new MapSqlParameterSource("data", antesDe);
        StringBuilder sql = new StringBuilder("SELECT m.banco_id AS id, COALESCE(SUM(" + SINAL + "), 0) AS valor FROM movimentacao_banco m WHERE m.data < :data");
        if (!bancoIds.isEmpty()) {
            sql.append(" AND m.banco_id IN (:bancos)");
            p.addValue("bancos", bancoIds);
        }
        sql.append(" GROUP BY m.banco_id");
        return porId(sql.toString(), p);
    }

    public Map<Long, BigDecimal> movimentacaoPorBanco(List<Long> bancoIds, LocalDate de, LocalDate ate) {
        MapSqlParameterSource p = new MapSqlParameterSource("de", de).addValue("ate", ate);
        StringBuilder sql = new StringBuilder("SELECT m.banco_id AS id, COALESCE(SUM(" + SINAL + "), 0) AS valor FROM movimentacao_banco m WHERE m.data BETWEEN :de AND :ate");
        if (!bancoIds.isEmpty()) {
            sql.append(" AND m.banco_id IN (:bancos)");
            p.addValue("bancos", bancoIds);
        }
        sql.append(" GROUP BY m.banco_id");
        return porId(sql.toString(), p);
    }

    public Map<Long, BigDecimal> realizadoPorBanco(Lado lado, FluxoCaixaFiltro f, LocalDate de, LocalDate ate) {
        MapSqlParameterSource p = new MapSqlParameterSource("de", de).addValue("ate", ate);
        boolean receber = lado == Lado.RECEBER;
        StringBuilder sql = new StringBuilder("SELECT x.banco_id AS id, COALESCE(SUM(x.valor_total), 0) AS valor FROM "
                + (receber ? "conta_receber_recebimento x JOIN conta_receber t ON t.id = x.conta_receber_id WHERE x.estornada = false AND x.origem <> 'CREDITO_CLIENTE' AND x.data_recebimento"
                : "conta_pagar_pagamento x JOIN conta_pagar t ON t.id = x.conta_pagar_id WHERE x.estornada = false AND x.data_pagamento") + " BETWEEN :de AND :ate");
        filtroBanco("x", f, sql, p);
        filtrosTitulo(lado, f, sql, p);
        sql.append(" GROUP BY x.banco_id");
        return porId(sql.toString(), p);
    }

    private Map<Long, BigDecimal> porId(String sql, MapSqlParameterSource p) {
        Map<Long, BigDecimal> m = new LinkedHashMap<>();
        jdbc.query(sql, p, rs -> {
            m.put(rs.getLong("id"), rs.getBigDecimal("valor"));
        });
        return m;
    }

    // ---------- categorias ----------

    private String tabelaCategoria(Lado lado) {
        return lado == Lado.RECEBER ? "categoria_receita" : "categoria_despesa";
    }

    private List<CategoriaLinha> categorias(String sql, MapSqlParameterSource p) {
        return jdbc.query(sql, p, (rs, n) -> new CategoriaLinha(rs.getLong("id"), rs.getString("nome"), rs.getString("pai"), rs.getBigDecimal("valor")));
    }

    public List<CategoriaLinha> categoriasRealizado(Lado lado, FluxoCaixaFiltro f, LocalDate de, LocalDate ate) {
        MapSqlParameterSource p = new MapSqlParameterSource("de", de).addValue("ate", ate);
        boolean receber = lado == Lado.RECEBER;
        StringBuilder sql = new StringBuilder("SELECT c.id, c.nome, pai.nome AS pai, COALESCE(SUM(x.valor_total), 0) AS valor FROM "
                + (receber ? "conta_receber_recebimento x JOIN conta_receber t ON t.id = x.conta_receber_id" : "conta_pagar_pagamento x JOIN conta_pagar t ON t.id = x.conta_pagar_id")
                + " JOIN " + tabelaCategoria(lado) + " c ON c.id = t.categoria_id LEFT JOIN " + tabelaCategoria(lado) + " pai ON pai.id = c.pai_id WHERE x.estornada = false"
                + (receber ? " AND x.origem <> 'CREDITO_CLIENTE' AND x.data_recebimento" : " AND x.data_pagamento") + " BETWEEN :de AND :ate");
        filtroBanco("x", f, sql, p);
        filtrosTitulo(lado, f, sql, p);
        sql.append(" GROUP BY c.id, c.nome, pai.nome");
        return categorias(sql.toString(), p);
    }

    // previsto (de..ate) ou atrasado (vencimento < hoje): saldo em aberto por categoria
    public List<CategoriaLinha> categoriasAberto(Lado lado, FluxoCaixaFiltro f, LocalDate de, LocalDate ate, LocalDate hojeParaAtrasado) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        boolean receber = lado == Lado.RECEBER;
        StringBuilder sql = new StringBuilder("SELECT c.id, c.nome, pai.nome AS pai, COALESCE(SUM" + (receber ? SALDO_ABERTO_RECEBER : SALDO_ABERTO_PAGAR) + ", 0) AS valor FROM "
                + (receber ? "conta_receber" : "conta_pagar") + " t JOIN " + tabelaCategoria(lado) + " c ON c.id = t.categoria_id LEFT JOIN " + tabelaCategoria(lado)
                + " pai ON pai.id = c.pai_id WHERE t.situacao <> 'CANCELADO' AND t.valor > " + (receber ? "t.valor_recebido" : "t.valor_pago"));
        if (hojeParaAtrasado != null) {
            sql.append(" AND t.vencimento < :hoje");
            p.addValue("hoje", hojeParaAtrasado);
        } else {
            sql.append(" AND t.vencimento BETWEEN :de AND :ate");
            p.addValue("de", de).addValue("ate", ate);
        }
        filtrosTitulo(lado, f, sql, p);
        sql.append(" GROUP BY c.id, c.nome, pai.nome");
        return categorias(sql.toString(), p);
    }

    // maior vencimento já materializado entre os títulos a pagar (horizonte conhecido da projeção)
    public LocalDate maiorVencimentoPagar() {
        Date d = jdbc.getJdbcOperations().queryForObject("SELECT MAX(vencimento) FROM conta_pagar WHERE situacao <> 'CANCELADO'", Date.class);
        return d == null ? null : d.toLocalDate();
    }

    // ---------- detalhe ----------

    public static final Map<String, String> ORDENACOES = Map.of("data", "data", "valor", "valor", "numero", "numero", "pessoa", "LOWER(pessoa)",
            "categoria", "LOWER(categoria)");

    // situacao: REALIZADO (data do recebimento/pagamento), PREVISTO (vencimento entre de e ate) ou ATRASADO (vencimento < hoje; de/ate ignorados)
    public FluxoCaixaDTOs.Detalhe detalhe(Lado lado, String situacao, FluxoCaixaFiltro f, LocalDate de, LocalDate ate, LocalDate hoje, String campoOrdem, boolean desc,
                                          int page, int size) {
        boolean receber = lado == Lado.RECEBER;
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder from = new StringBuilder();
        String select;
        if (situacao.equals("REALIZADO")) {
            String tabela = receber ? "conta_receber_recebimento" : "conta_pagar_pagamento";
            String ligacao = receber ? "conta_receber_id" : "conta_pagar_id";
            String dataCol = receber ? "x.data_recebimento" : "x.data_pagamento";
            from.append(" FROM ").append(tabela).append(" x JOIN ").append(receber ? "conta_receber" : "conta_pagar").append(" t ON t.id = x.").append(ligacao)
                    .append(" JOIN banco b ON b.id = x.banco_id LEFT JOIN forma_pagamento fp ON fp.id = x.forma_pagamento_id");
            from.append(receber ? " LEFT JOIN clientes cl ON cl.id = t.cliente_id" : "");
            from.append(" JOIN ").append(tabelaCategoria(lado)).append(" cat ON cat.id = t.categoria_id LEFT JOIN centro_custo cc ON cc.id = t.centro_custo_id");
            from.append(" WHERE x.estornada = false").append(receber ? " AND x.origem <> 'CREDITO_CLIENTE'" : "").append(" AND ").append(dataCol).append(" BETWEEN :de AND :ate");
            p.addValue("de", de).addValue("ate", ate);
            filtroBanco("x", f, from, p);
            select = "SELECT t.id AS titulo_id, t.numero AS numero, t.descricao AS descricao, " + (receber ? "COALESCE(cl.nome, t.pagador_nome, '')" : "t.favorecido") + " AS pessoa, "
                    + "cat.nome AS categoria, cc.nome AS centro, b.nome AS banco, fp.nome AS forma, x.valor_total AS valor, " + dataCol + " AS data, false AS estimado, "
                    + "CAST(NULL AS BIGINT) AS dias";
        } else {
            String saldo = receber ? SALDO_ABERTO_RECEBER : SALDO_ABERTO_PAGAR;
            from.append(" FROM ").append(receber ? "conta_receber" : "conta_pagar").append(" t");
            from.append(receber ? " LEFT JOIN clientes cl ON cl.id = t.cliente_id" : "");
            from.append(" JOIN ").append(tabelaCategoria(lado)).append(" cat ON cat.id = t.categoria_id LEFT JOIN centro_custo cc ON cc.id = t.centro_custo_id");
            from.append(" WHERE t.situacao <> 'CANCELADO' AND t.valor > ").append(receber ? "t.valor_recebido" : "t.valor_pago");
            if (situacao.equals("ATRASADO")) {
                from.append(" AND t.vencimento < :hoje");
                p.addValue("hoje", hoje);
            } else {
                from.append(" AND t.vencimento BETWEEN :de AND :ate");
                p.addValue("de", de).addValue("ate", ate);
            }
            select = "SELECT t.id AS titulo_id, t.numero AS numero, t.descricao AS descricao, " + (receber ? "COALESCE(cl.nome, t.pagador_nome, '')" : "t.favorecido") + " AS pessoa, "
                    + "cat.nome AS categoria, cc.nome AS centro, CAST(NULL AS VARCHAR) AS banco, CAST(NULL AS VARCHAR) AS forma, " + saldo + " AS valor, t.vencimento AS data, "
                    + (receber ? "false" : "t.estimado") + " AS estimado, "
                    + (situacao.equals("ATRASADO") ? "CAST((CAST(:hoje AS DATE) - t.vencimento) AS BIGINT)" : "CAST(NULL AS BIGINT)") + " AS dias";
        }
        StringBuilder filtros = new StringBuilder();
        filtrosTitulo(lado, f, filtros, p);
        String base = select + from + filtros;
        String agregado = "SELECT COUNT(*) AS qtd, COALESCE(SUM(valor), 0) AS total FROM (" + base + ") q";
        long[] quantidade = {0};
        BigDecimal[] total = {BigDecimal.ZERO};
        jdbc.query(agregado, p, rs -> {
            quantidade[0] = rs.getLong("qtd");
            total[0] = rs.getBigDecimal("total");
        });
        String ordem = ORDENACOES.get(campoOrdem) + (desc ? " DESC" : " ASC") + ", numero ASC";
        p.addValue("limite", size).addValue("deslocamento", (long) page * size);
        String tipo = receber ? "ENTRADA" : "SAIDA";
        String tipoTitulo = receber ? "RECEBER" : "PAGAR";
        List<FluxoCaixaDTOs.Item> itens = jdbc.query("SELECT * FROM (" + base + ") q ORDER BY " + ordem + " LIMIT :limite OFFSET :deslocamento", p,
                (rs, n) -> new FluxoCaixaDTOs.Item(tipo, situacao, new FluxoCaixaDTOs.TituloRef(tipoTitulo, rs.getLong("titulo_id"), rs.getLong("numero")),
                        rs.getString("descricao"), rs.getString("pessoa"), rs.getString("categoria"), rs.getString("centro"), rs.getString("banco"), rs.getString("forma"),
                        rs.getBigDecimal("valor"), rs.getObject("data", LocalDate.class), rs.getBoolean("estimado"),
                        rs.getObject("dias") == null ? null : rs.getLong("dias")));
        int paginas = size == 0 ? 0 : (int) ((quantidade[0] + size - 1) / size);
        return new FluxoCaixaDTOs.Detalhe(page, size, quantidade[0], paginas, total[0], new ArrayList<>(itens));
    }
}
