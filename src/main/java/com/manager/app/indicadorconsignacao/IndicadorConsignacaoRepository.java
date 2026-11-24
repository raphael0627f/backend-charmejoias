package com.manager.app.indicadorconsignacao;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// consultas agregadas dos indicadores de consignação (SQL nativo; tudo somado no banco).
// Vendas: documento da consignação = ACERTO se existir, senão PRÉ-ACERTO; consignação cancelada fora; título cancelado fora; recebimento estornado fora. Dívida (inadimplência): todo título não cancelado, de acerto ou de pré-acerto.
@Repository
@RequiredArgsConstructor
public class IndicadorConsignacaoRepository {

    private final NamedParameterJdbcTemplate jdbc;

    // ibges: nulo = sem filtro de cidade/rota; vazio = a cidade/rota não tem código IBGE (nenhum cliente casa)
    public record Filtro(LocalDate inicio, LocalDate fim, LocalDate hoje, UUID clienteId, List<String> ibges) {
    }

    public record VendasLinha(long acertos, BigDecimal vendido, BigDecimal comissao) {
    }

    public record GiroLinha(long acertos, BigDecimal vendido, BigDecimal devolvido, long qtdVendida, long qtdDevolvida) {
    }

    public record PrazoLinha(long pagos, long somaDias, long pagosComAtraso, long somaDiasAtraso, long pagosNoPrazo) {
    }

    public record ClienteVendas(UUID clienteId, long acertos, BigDecimal vendido) {
    }

    public record ClienteInad(UUID clienteId, long titulos, BigDecimal valor) {
    }

    public record ClientePrazo(UUID clienteId, PrazoLinha prazo) {
    }

    public record MesVendas(String mes, long acertos, BigDecimal vendido, BigDecimal comissao) {
    }

    public record MesInad(String mes, long titulos, BigDecimal valor) {
    }

    public record ItemLinha(String tipo, String id, String nome, long quantidade, BigDecimal valor) {
    }

    public record CategoriaLinha(Long id, String nome, long quantidade, BigDecimal valor) {
    }

    public record MeioLinha(Long formaId, String nome, long quantidade, BigDecimal valor, BigDecimal taxa) {
    }

    public record ClienteNome(UUID id, String nome, String cidade) {
    }

    // ---------- fragmentos ----------

    private static void filtroCliente(String coluna, Filtro f, StringBuilder sql, MapSqlParameterSource p) {
        if (f.clienteId() != null) {
            sql.append(" AND ").append(coluna).append(" = :cliente");
            p.addValue("cliente", f.clienteId());
        }
        if (f.ibges() != null) {
            if (f.ibges().isEmpty()) {
                sql.append(" AND 1 = 0");
            } else {
                sql.append(" AND EXISTS (SELECT 1 FROM enderecos e WHERE e.cliente_id = ").append(coluna).append(" AND e.principal = true AND e.ibge IN (:ibges))");
                p.addValue("ibges", f.ibges());
            }
        }
    }

    private static final String DATA_ACERTO = "COALESCE(c.data_acerto_consignacao, CAST(a.created_at AS DATE))";

    // quantidade devolvida de cada item da consignação no acerto (mesma regra de AcertoConsignacao.devolvidoDe)
    private static final String DEVOLVIDO = "LEFT JOIN LATERAL (SELECT SUM(ap.quantidade) AS qtd FROM acertos_consignacao_produtos ap WHERE ap.acerto_consignacao_id = a.id "
            + "AND ((ap.produto_id IS NOT NULL AND ap.produto_id = cp.produto_id AND ap.produto_variacao_id IS NOT DISTINCT FROM cp.produto_variacao_id) "
            + "OR (ap.joia_id IS NOT NULL AND ap.joia_id = cp.joia_id))) d ON true";

    private static final String ACERTO_PAGO = "(EXISTS (SELECT 1 FROM conta_receber t WHERE t.acerto_id = a.id AND t.situacao <> 'CANCELADO') "
            + "AND NOT EXISTS (SELECT 1 FROM conta_receber t2 WHERE t2.acerto_id = a.id AND t2.situacao NOT IN ('CANCELADO', 'RECEBIDO')))";

    // acertos confirmados do período com valores calculados; "pago" = acerto com título e todos os títulos não cancelados quitados
    private static String cteAcertos(Filtro f, MapSqlParameterSource p) {
        p.addValue("ini", f.inicio()).addValue("fim", f.fim());
        StringBuilder sql = new StringBuilder("WITH ac AS (SELECT a.id AS acerto_id, c.cliente_id AS cliente_id, " + DATA_ACERTO + " AS data_acerto, COALESCE(a.desconto, 0) AS comissao, "
                + "COALESCE(SUM(cp.preco * (cp.quantidade - COALESCE(d.qtd, 0))), 0) AS vendido, COALESCE(SUM(cp.preco * COALESCE(d.qtd, 0)), 0) AS devolvido, "
                + "COALESCE(SUM(cp.quantidade - COALESCE(d.qtd, 0)), 0) AS qtd_vendida, COALESCE(SUM(COALESCE(d.qtd, 0)), 0) AS qtd_devolvida, " + ACERTO_PAGO + " AS pago "
                + "FROM acertos_consignacao a JOIN consignacoes c ON c.id = a.consignacao_id JOIN consignacoes_produtos cp ON cp.consignacao_id = c.id " + DEVOLVIDO
                + " WHERE a.confirmado = true AND c.cancelada = false AND " + DATA_ACERTO + " BETWEEN :ini AND :fim");
        filtroCliente("c.cliente_id", f, sql, p);
        sql.append(" GROUP BY a.id, c.cliente_id, c.data_acerto_consignacao, a.created_at, a.desconto)");
        return sql.toString();
    }

    // todos os títulos não cancelados de consignação, de acerto OU de pré-acerto: dívida em aberto é dívida mesmo depois que o acerto é criado.
    // Não duplica: o sistema impede título de pré-acerto quando já há título do acerto e o saldo a titular do acerto desconta os títulos do pré-acerto (cada título é uma linha só).
    static String cteTitulos(Filtro f, MapSqlParameterSource p) {
        StringBuilder sql = new StringBuilder("WITH tv AS (SELECT t.id AS id, COALESCE(t.cliente_id, c.cliente_id) AS cliente_id, t.valor AS valor, t.valor_recebido AS valor_recebido, "
                + "t.vencimento AS vencimento, t.situacao AS situacao FROM conta_receber t LEFT JOIN acertos_consignacao a ON a.id = t.acerto_id "
                + "LEFT JOIN pre_acertos_consignacao pa ON pa.id = t.pre_acerto_id JOIN consignacoes c ON c.id = COALESCE(t.consignacao_id, a.consignacao_id, pa.consignacao_id) "
                + "WHERE t.situacao <> 'CANCELADO' AND c.cancelada = false AND (t.acerto_id IS NOT NULL OR t.pre_acerto_id IS NOT NULL)");
        filtroCliente("COALESCE(t.cliente_id, c.cliente_id)", f, sql, p);
        sql.append(")");
        return sql.toString();
    }

    private <T> List<T> lista(String sql, MapSqlParameterSource p, org.springframework.jdbc.core.RowMapper<T> m) {
        return jdbc.query(sql, p, m);
    }

    // ---------- vendas / giro ----------

    public VendasLinha vendas(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = cteAcertos(f, p) + " SELECT COUNT(*) AS n, COALESCE(SUM(vendido), 0) AS vendido, COALESCE(SUM(comissao), 0) AS comissao FROM ac WHERE pago";
        return jdbc.queryForObject(sql, p, (rs, n) -> new VendasLinha(rs.getLong("n"), rs.getBigDecimal("vendido"), rs.getBigDecimal("comissao")));
    }

    public GiroLinha giro(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = cteAcertos(f, p) + " SELECT COUNT(*) AS n, COALESCE(SUM(vendido), 0) AS vendido, COALESCE(SUM(devolvido), 0) AS devolvido, "
                + "COALESCE(SUM(qtd_vendida), 0) AS qv, COALESCE(SUM(qtd_devolvida), 0) AS qd FROM ac";
        return jdbc.queryForObject(sql, p, (rs, n) -> new GiroLinha(rs.getLong("n"), rs.getBigDecimal("vendido"), rs.getBigDecimal("devolvido"), rs.getLong("qv"), rs.getLong("qd")));
    }

    public List<ClienteVendas> vendasPorCliente(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        return lista(cteAcertos(f, p) + " SELECT cliente_id, COUNT(*) AS n, COALESCE(SUM(vendido), 0) AS vendido FROM ac WHERE pago AND cliente_id IS NOT NULL GROUP BY cliente_id", p,
                (rs, n) -> new ClienteVendas(rs.getObject("cliente_id", UUID.class), rs.getLong("n"), rs.getBigDecimal("vendido")));
    }

    public List<MesVendas> vendasPorMes(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        return lista(cteAcertos(f, p) + " SELECT to_char(data_acerto, 'YYYY-MM') AS mes, COUNT(*) AS n, COALESCE(SUM(vendido), 0) AS vendido, COALESCE(SUM(comissao), 0) AS comissao "
                        + "FROM ac WHERE pago GROUP BY 1", p,
                (rs, n) -> new MesVendas(rs.getString("mes"), rs.getLong("n"), rs.getBigDecimal("vendido"), rs.getBigDecimal("comissao")));
    }

    // ---------- inadimplência (posição de hoje: vencido e com saldo em aberto) ----------

    public Map<String, BigDecimal> inadimplenciaValorPorFaixa(Filtro f, Map<String, Long> quantidades) {
        MapSqlParameterSource p = new MapSqlParameterSource("hoje", f.hoje());
        String sql = cteTitulos(f, p) + " SELECT faixa, SUM(saldo) AS valor, COUNT(*) AS n FROM (SELECT CASE WHEN :hoje - vencimento <= 30 THEN '1-30' WHEN :hoje - vencimento <= 60 THEN '31-60' "
                + "ELSE '>60' END AS faixa, (valor - valor_recebido) AS saldo FROM tv WHERE vencimento < :hoje AND valor > valor_recebido) q GROUP BY faixa";
        Map<String, BigDecimal> valores = new LinkedHashMap<>();
        jdbc.query(sql, p, rs -> {
            valores.put(rs.getString("faixa"), rs.getBigDecimal("valor"));
            quantidades.put(rs.getString("faixa"), rs.getLong("n"));
        });
        return valores;
    }

    public List<ClienteInad> inadimplenciaPorCliente(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource("hoje", f.hoje());
        return lista(cteTitulos(f, p) + " SELECT cliente_id, COUNT(*) AS n, SUM(valor - valor_recebido) AS valor FROM tv WHERE vencimento < :hoje AND valor > valor_recebido "
                + "AND cliente_id IS NOT NULL GROUP BY cliente_id", p, (rs, n) -> new ClienteInad(rs.getObject("cliente_id", UUID.class), rs.getLong("n"), rs.getBigDecimal("valor")));
    }

    // inadimplência por mês de vencimento (títulos vencidos, com saldo, cujo vencimento cai no período)
    public List<MesInad> inadimplenciaPorMes(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource("hoje", f.hoje()).addValue("ini", f.inicio()).addValue("fim", f.fim());
        return lista(cteTitulos(f, p) + " SELECT to_char(vencimento, 'YYYY-MM') AS mes, COUNT(*) AS n, SUM(valor - valor_recebido) AS valor FROM tv "
                + "WHERE vencimento < :hoje AND valor > valor_recebido AND vencimento BETWEEN :ini AND :fim GROUP BY 1", p,
                (rs, n) -> new MesInad(rs.getString("mes"), rs.getLong("n"), rs.getBigDecimal("valor")));
    }

    // ---------- prazo de recebimento (títulos quitados cujo último recebimento cai no período) ----------

    private static final String CTE_PAGOS = ", pg AS (SELECT tv.id AS id, tv.cliente_id AS cliente_id, MAX(r.data_recebimento) - tv.vencimento AS dias FROM tv "
            + "JOIN conta_receber_recebimento r ON r.conta_receber_id = tv.id AND r.estornada = false WHERE tv.situacao = 'RECEBIDO' "
            + "GROUP BY tv.id, tv.cliente_id, tv.vencimento HAVING MAX(r.data_recebimento) BETWEEN :ini AND :fim)";

    private static final String AGREGADO_PRAZO = "COUNT(*) AS pagos, COALESCE(SUM(dias), 0) AS soma, COUNT(*) FILTER (WHERE dias > 0) AS com_atraso, "
            + "COALESCE(SUM(dias) FILTER (WHERE dias > 0), 0) AS soma_atraso, COUNT(*) FILTER (WHERE dias <= 0) AS no_prazo";

    private static PrazoLinha prazoDe(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PrazoLinha(rs.getLong("pagos"), rs.getLong("soma"), rs.getLong("com_atraso"), rs.getLong("soma_atraso"), rs.getLong("no_prazo"));
    }

    public PrazoLinha prazo(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource("ini", f.inicio()).addValue("fim", f.fim());
        return jdbc.queryForObject(cteTitulos(f, p) + CTE_PAGOS + " SELECT " + AGREGADO_PRAZO + " FROM pg", p, (rs, n) -> prazoDe(rs));
    }

    public List<ClientePrazo> prazoPorCliente(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource("ini", f.inicio()).addValue("fim", f.fim());
        return lista(cteTitulos(f, p) + CTE_PAGOS + " SELECT cliente_id, " + AGREGADO_PRAZO + " FROM pg WHERE cliente_id IS NOT NULL GROUP BY cliente_id", p,
                (rs, n) -> new ClientePrazo(rs.getObject("cliente_id", UUID.class), prazoDe(rs)));
    }

    // ---------- meios de pagamento ----------

    public List<MeioLinha> meiosPagamento(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource("ini", f.inicio()).addValue("fim", f.fim());
        return lista(cteTitulos(f, p) + " SELECT fp.id AS forma_id, fp.nome AS nome, COUNT(*) AS n, COALESCE(SUM(r.valor_total), 0) AS valor, COALESCE(SUM(r.valor_taxa_cliente), 0) AS taxa "
                + "FROM tv JOIN conta_receber_recebimento r ON r.conta_receber_id = tv.id AND r.estornada = false LEFT JOIN forma_pagamento fp ON fp.id = r.forma_pagamento_id "
                + "WHERE r.data_recebimento BETWEEN :ini AND :fim GROUP BY fp.id, fp.nome", p,
                (rs, n) -> new MeioLinha(rs.getObject("forma_id") == null ? null : rs.getLong("forma_id"), rs.getString("nome"), rs.getLong("n"), rs.getBigDecimal("valor"), rs.getBigDecimal("taxa")));
    }

    // ---------- produtos e categorias (acertos feitos e pagos do período) ----------

    private static String fromItens(Filtro f, MapSqlParameterSource p) {
        p.addValue("ini", f.inicio()).addValue("fim", f.fim());
        StringBuilder sql = new StringBuilder(" FROM acertos_consignacao a JOIN consignacoes c ON c.id = a.consignacao_id JOIN consignacoes_produtos cp ON cp.consignacao_id = c.id " + DEVOLVIDO
                + " LEFT JOIN produtos pr ON pr.id = cp.produto_id LEFT JOIN joias jo ON jo.id = cp.joia_id LEFT JOIN categorias cat ON cat.id = COALESCE(pr.categoria_id, jo.categoria_id) "
                + "WHERE a.confirmado = true AND c.cancelada = false AND " + ACERTO_PAGO + " AND " + DATA_ACERTO + " BETWEEN :ini AND :fim");
        filtroCliente("c.cliente_id", f, sql, p);
        return sql.toString();
    }

    private static final String QTD_VENDIDA = "SUM(cp.quantidade - COALESCE(d.qtd, 0))";
    private static final String VALOR_VENDIDO = "SUM(cp.preco * (cp.quantidade - COALESCE(d.qtd, 0)))";

    public List<ItemLinha> itensVendidos(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = "SELECT CASE WHEN pr.id IS NOT NULL THEN 'PRODUTO' ELSE 'JOIA' END AS tipo, CAST(COALESCE(pr.id, jo.id) AS VARCHAR) AS item_id, COALESCE(pr.nome, jo.nome) AS nome, "
                + QTD_VENDIDA + " AS qtd, " + VALOR_VENDIDO + " AS valor" + fromItens(f, p)
                + " AND COALESCE(pr.id, jo.id) IS NOT NULL GROUP BY 1, 2, 3 HAVING " + QTD_VENDIDA + " > 0";
        return lista(sql, p, (rs, n) -> new ItemLinha(rs.getString("tipo"), rs.getString("item_id"), rs.getString("nome"), rs.getLong("qtd"), rs.getBigDecimal("valor")));
    }

    public List<CategoriaLinha> categoriasVendidas(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = "SELECT cat.id AS cat_id, COALESCE(cat.nome, 'Sem categoria') AS nome, " + QTD_VENDIDA + " AS qtd, " + VALOR_VENDIDO + " AS valor" + fromItens(f, p)
                + " GROUP BY cat.id, cat.nome HAVING " + QTD_VENDIDA + " > 0";
        return lista(sql, p, (rs, n) -> new CategoriaLinha(rs.getObject("cat_id") == null ? null : rs.getLong("cat_id"), rs.getString("nome"), rs.getLong("qtd"), rs.getBigDecimal("valor")));
    }

    // ---------- apoio ----------

    public List<ClienteNome> clientes(Collection<UUID> ids) {
        if (ids.isEmpty()) return new ArrayList<>();
        MapSqlParameterSource p = new MapSqlParameterSource("ids", ids);
        return lista("SELECT c.id AS id, c.nome AS nome, (SELECT e.municipio FROM enderecos e WHERE e.cliente_id = c.id AND e.principal = true LIMIT 1) AS cidade FROM clientes c WHERE c.id IN (:ids)",
                p, (rs, n) -> new ClienteNome(rs.getObject("id", UUID.class), rs.getString("nome"), rs.getString("cidade")));
    }

    public boolean clienteExiste(UUID id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM clientes WHERE id = :id", new MapSqlParameterSource("id", id), Integer.class);
        return n != null && n > 0;
    }

    // IBGE das cidades (nulo quando a cidade não existe); vazio quando existe sem código
    public List<String> ibgesDaCidade(Long cidadeId) {
        List<String> r = lista("SELECT ibge FROM cidades WHERE id = :id", new MapSqlParameterSource("id", cidadeId), (rs, n) -> rs.getString("ibge"));
        if (r.isEmpty()) return null;
        return r.stream().filter(s -> s != null && !s.isBlank()).toList();
    }

    public List<String> ibgesDaRota(Long rotaId) {
        Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM agenda_rotas WHERE id = :id", new MapSqlParameterSource("id", rotaId), Integer.class);
        if (existe == null || existe == 0) return null;
        return lista("SELECT DISTINCT c.ibge AS ibge FROM agenda_rota_cidades rc JOIN cidades c ON c.id = rc.cidade_id WHERE rc.rota_id = :id AND c.ibge IS NOT NULL",
                new MapSqlParameterSource("id", rotaId), (rs, n) -> rs.getString("ibge"));
    }
}
