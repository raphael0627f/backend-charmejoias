package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.MovimentacaoResponseDTO;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// extrato com o saldo após cada linha, calculado no banco por window function (soma acumulada por banco, em data e id).
// O saldo considera todo o histórico do banco; os filtros de período/origem/busca só escondem linhas.
@Repository
@RequiredArgsConstructor
public class ExtratoBancoRepository {

    private static final String COLUNAS = """
            select * from (
              select m.id, m.banco_id, b.nome as banco_nome, m.data, m.tipo, m.valor, m.origem, m.descricao, m.transferencia_id,
                     m.estornada, m.estorno_de_id, m.motivo_estorno, m.estornada_em, m.pagamento_id, cp.id as titulo_id, cp.numero as titulo_numero,
                     ep.id as ep_id, ep.usuario as ep_usuario, ep.primeiro_nome as ep_nome, ep.sobrenome as ep_sobrenome,
                     u.id as u_id, u.usuario as u_usuario, u.primeiro_nome as u_nome, u.sobrenome as u_sobrenome, m.criado_em,
                     sum(case when m.tipo = 'ENTRADA' then m.valor else -m.valor end)
                         over (partition by m.banco_id order by m.data, m.id) as saldo_apos
              from movimentacao_banco m
              join banco b on b.id = m.banco_id
              left join usuarios u on u.id = m.usuario_id
              left join usuarios ep on ep.id = m.estornada_por_id
              left join conta_pagar_pagamento cb on cb.id = m.pagamento_id
              left join conta_pagar cp on cp.id = cb.conta_pagar_id
              where (cast(:bancoId as bigint) is null or m.banco_id = cast(:bancoId as bigint))
            ) x
            where (cast(:inicio as date) is null or x.data >= cast(:inicio as date))
              and (cast(:fim as date) is null or x.data <= cast(:fim as date))
              and (cast(:origem as text) is null or x.origem = cast(:origem as text))
              and (cast(:busca as text) is null or lower(x.descricao) like cast(:busca as text) escape '!'
                   or lower(x.banco_nome) like cast(:busca as text) escape '!')
            """;

    private static final String CONTAGEM = """
            select count(*) from movimentacao_banco m
            join banco b on b.id = m.banco_id
            where (cast(:bancoId as bigint) is null or m.banco_id = cast(:bancoId as bigint))
              and (cast(:inicio as date) is null or m.data >= cast(:inicio as date))
              and (cast(:fim as date) is null or m.data <= cast(:fim as date))
              and (cast(:origem as text) is null or m.origem = cast(:origem as text))
              and (cast(:busca as text) is null or lower(m.descricao) like cast(:busca as text) escape '!'
                   or lower(b.nome) like cast(:busca as text) escape '!')
            """;

    private final JdbcClient jdbc;

    public Page<MovimentacaoResponseDTO> buscar(Long bancoId, LocalDate inicio, LocalDate fim, OrigemMovimentacao origem,
                                                String busca, int pagina, int tamanho) {
        String padrao = padraoBusca(busca);
        String origemTexto = origem == null ? null : origem.name();
        long total = jdbc.sql(CONTAGEM)
                .param("bancoId", bancoId).param("inicio", inicio).param("fim", fim).param("origem", origemTexto).param("busca", padrao)
                .query(Long.class).single();
        List<MovimentacaoResponseDTO> itens = jdbc.sql(COLUNAS + " order by x.data desc, x.id desc limit :limite offset :deslocamento")
                .param("bancoId", bancoId).param("inicio", inicio).param("fim", fim).param("origem", origemTexto).param("busca", padrao)
                .param("limite", tamanho).param("deslocamento", (long) pagina * tamanho)
                .query((rs, n) -> linha(rs, bancoId != null))
                .list();
        return new PageImpl<>(itens, PageRequest.of(pagina, tamanho), total);
    }

    // % e _ digitados valem como texto; "!" é o caractere de escape do like
    private static String padraoBusca(String busca) {
        if (busca == null || busca.isBlank()) return null;
        String termo = busca.trim().toLowerCase().replace("\u0000", "")
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + termo + "%";
    }

    private static MovimentacaoResponseDTO linha(ResultSet rs, boolean comSaldoApos) throws SQLException {
        long transferencia = rs.getLong("transferencia_id");
        boolean semTransferencia = rs.wasNull();
        long estornoDe = rs.getLong("estorno_de_id");
        boolean semEstornoDe = rs.wasNull();
        java.sql.Timestamp estornadaEm = rs.getTimestamp("estornada_em");
        java.sql.Timestamp criadoEm = rs.getTimestamp("criado_em");
        BigDecimal saldoApos = comSaldoApos ? rs.getBigDecimal("saldo_apos") : null;
        long pagamento = rs.getLong("pagamento_id");
        boolean semPagamento = rs.wasNull();
        long titulo = rs.getLong("titulo_id");
        boolean semTitulo = rs.wasNull();
        long tituloNumero = rs.getLong("titulo_numero");
        return new MovimentacaoResponseDTO(rs.getLong("id"), rs.getLong("banco_id"), rs.getString("banco_nome"),
                rs.getObject("data", LocalDate.class), TipoMovimentacao.valueOf(rs.getString("tipo")),
                CalculoFinanceiro.arredondar(rs.getBigDecimal("valor")), OrigemMovimentacao.valueOf(rs.getString("origem")),
                rs.getString("descricao"), semTransferencia ? null : transferencia, rs.getBoolean("estornada"),
                semEstornoDe ? null : estornoDe, rs.getString("motivo_estorno"),
                estornadaEm == null ? null : estornadaEm.toLocalDateTime(),
                resumo(rs, "ep"), resumo(rs, "u"), criadoEm == null ? null : criadoEm.toLocalDateTime(),
                saldoApos == null ? null : CalculoFinanceiro.arredondar(saldoApos), semTitulo ? null : titulo,
                semTitulo ? null : tituloNumero, semPagamento ? null : pagamento);
    }

    private static UsuarioResumoDTO resumo(ResultSet rs, String prefixo) throws SQLException {
        UUID id = rs.getObject(prefixo + "_id", UUID.class);
        if (id == null) return null;
        UsuarioResumoDTO dto = new UsuarioResumoDTO();
        dto.setId(id);
        dto.setUsuario(rs.getString(prefixo + "_usuario"));
        dto.setPrimeiroNome(rs.getString(prefixo + "_nome"));
        dto.setSobrenome(rs.getString(prefixo + "_sobrenome"));
        return dto;
    }
}
