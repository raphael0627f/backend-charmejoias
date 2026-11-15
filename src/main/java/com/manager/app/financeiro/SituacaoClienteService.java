package com.manager.app.financeiro;

import com.manager.app.cliente.Cliente;
import com.manager.app.cliente.ClienteRepository;
import com.manager.app.endereco.Endereco;
import com.manager.app.financeiro.dto.SituacaoClienteDTOs;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// situação financeira do cliente (Contas a Receber): consultas agregadas no banco (sem N+1) e cálculo das faixas em SituacaoClienteCalculo
@Service
@RequiredArgsConstructor
public class SituacaoClienteService {

    static final int TAMANHO_MAXIMO = 200;
    private static final Map<String, String> ORDENACOES = Map.of("emaberto", "a.aberto", "vencido", "a.vencido",
            "diasmaioratraso", "a.atraso", "nome", "lower(a.nome)");

    private final ClienteRepository clienteRepository;
    private final JdbcClient jdbc;

    // clientes com saldo em aberto: um registro por cliente com os agregados dos títulos ABERTO/RECEBIDO_PARCIAL
    private static final String AGREGADO = """
            select c.cliente_id, cl.nome, cl.cpf, cl.documento,
                   sum(c.valor - c.valor_recebido) as aberto,
                   coalesce(sum(case when c.vencimento < cast(:hoje as date) then c.valor - c.valor_recebido end), 0) as vencido,
                   count(*) as qtd,
                   coalesce(max(case when c.vencimento < cast(:hoje as date) then cast(:hoje as date) - c.vencimento end), 0) as atraso
            from conta_receber c
            join clientes cl on cl.id = c.cliente_id
            where c.situacao in ('ABERTO', 'RECEBIDO_PARCIAL') and c.valor - c.valor_recebido > 0
              and (cast(:busca as text) is null or lower(cl.nome) like cast(:busca as text) escape '!'
                   or lower(cl.cpf) like cast(:busca as text) escape '!' or lower(cl.documento) like cast(:busca as text) escape '!'
                   or cl.cpf like cast(:buscaDigitos as text) or cl.documento like cast(:buscaDigitos as text))
            group by c.cliente_id, cl.nome, cl.cpf, cl.documento
            having (cast(:somenteVencidos as boolean) = false
                    or sum(case when c.vencimento < cast(:hoje as date) then c.valor - c.valor_recebido else 0 end) > 0)
            """;

    // ---------- individual ----------

    @Transactional(readOnly = true)
    public SituacaoClienteDTOs.Situacao situacao(UUID clienteId, LocalDate recebimentoDe, LocalDate recebimentoAte) {
        Cliente cliente = clienteRepository.findById(clienteId).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado."));
        LocalDate hoje = LocalDate.now();
        LocalDate[] periodo = SituacaoClienteCalculo.periodo(recebimentoDe, recebimentoAte, hoje);

        List<SituacaoClienteCalculo.TituloAberto> abertos = jdbc.sql("""
                        select id, numero, vencimento, valor - valor_recebido as aberto from conta_receber
                        where cliente_id = :id and situacao in ('ABERTO', 'RECEBIDO_PARCIAL') and valor - valor_recebido > 0
                        """).param("id", clienteId)
                .query((rs, n) -> new SituacaoClienteCalculo.TituloAberto(rs.getLong("id"), rs.getLong("numero"),
                        rs.getObject("vencimento", LocalDate.class), rs.getBigDecimal("aberto"))).list();
        SituacaoClienteCalculo.Resumo r = SituacaoClienteCalculo.resumir(abertos, hoje);

        int[] contagem = new int[3]; // recebidos, cancelados, total
        jdbc.sql("select situacao, count(*) as n from conta_receber where cliente_id = :id group by situacao").param("id", clienteId)
                .query((rs, n) -> {
                    int q = rs.getInt("n");
                    contagem[2] += q;
                    if ("RECEBIDO".equals(rs.getString("situacao"))) contagem[0] += q;
                    if ("CANCELADO".equals(rs.getString("situacao"))) contagem[1] += q;
                    return q;
                }).list();

        SituacaoClienteDTOs.Periodo recebido = jdbc.sql("""
                        select coalesce(sum(r.valor_total), 0) as valor, count(*) as n
                        from conta_receber_recebimento r join conta_receber c on c.id = r.conta_receber_id
                        where c.cliente_id = :id and not r.estornada and r.data_recebimento between :de and :ate
                        """).param("id", clienteId).param("de", periodo[0]).param("ate", periodo[1])
                .query((rs, n) -> new SituacaoClienteDTOs.Periodo(periodo[0], periodo[1], CalculoFinanceiro.arredondar(rs.getBigDecimal("valor")), rs.getInt("n")))
                .single();
        SituacaoClienteDTOs.UltimoRecebimento ultimo = jdbc.sql("""
                        select r.data_recebimento, r.valor_total from conta_receber_recebimento r join conta_receber c on c.id = r.conta_receber_id
                        where c.cliente_id = :id and not r.estornada order by r.data_recebimento desc, r.id desc limit 1
                        """).param("id", clienteId)
                .query((rs, n) -> new SituacaoClienteDTOs.UltimoRecebimento(rs.getObject("data_recebimento", LocalDate.class),
                        CalculoFinanceiro.arredondar(rs.getBigDecimal("valor_total")))).optional().orElse(null);

        SituacaoClienteCalculo.TituloAberto antigo = r.maisAntigoVencido();
        return new SituacaoClienteDTOs.Situacao(identificacao(cliente), r.emAberto(), r.vencido(), r.vencemEm7Dias(), r.vencemEm30Dias(), r.diasMaiorAtraso(),
                antigo == null ? null : new SituacaoClienteDTOs.TituloVencido(antigo.id(), antigo.numero(), antigo.vencimento(), CalculoFinanceiro.arredondar(antigo.valorAberto())),
                recebido, ultimo, new SituacaoClienteDTOs.Titulos(contagem[2], r.emAberto().quantidade(), contagem[0], contagem[1]));
    }

    private static SituacaoClienteDTOs.Cliente identificacao(Cliente c) {
        Endereco e = c.getEnderecoPrincipal();
        if (e == null && !c.getEnderecos().isEmpty()) e = c.getEnderecos().get(0);
        return new SituacaoClienteDTOs.Cliente(c.getId(), c.getNome(), ContaReceberMapper.documentoDe(c), e == null ? null : e.getMunicipio());
    }

    // ---------- visão geral ----------

    @Transactional(readOnly = true)
    public SituacaoClienteDTOs.Geral geral(String busca, boolean somenteVencidos, String ordenar, String direcao, int pagina, int tamanho) {
        if (pagina < 0) throw new ItemInvalidoException("A página deve ser maior ou igual a zero.");
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) throw new ItemInvalidoException("O tamanho da página deve estar entre 1 e " + TAMANHO_MAXIMO + ".");
        String chave = ordenar == null || ordenar.isBlank() ? "emaberto" : ordenar.trim().toLowerCase();
        String coluna = ORDENACOES.get(chave);
        if (coluna == null) throw new ItemInvalidoException("Ordenação inválida. Use: emAberto, vencido, diasMaiorAtraso ou nome.");
        boolean padraoAsc = chave.equals("nome");
        boolean asc = padraoAsc;
        if (direcao != null && !direcao.isBlank()) {
            if (direcao.trim().equalsIgnoreCase("asc")) asc = true;
            else if (direcao.trim().equalsIgnoreCase("desc")) asc = false;
            else throw new ItemInvalidoException("Direção inválida. Use asc ou desc.");
        }
        String padrao = padraoBusca(busca);
        String padraoDigitos = BuscaDocumento.padrao(busca);
        LocalDate hoje = LocalDate.now();

        String lista = "with a as (" + AGREGADO + ") select a.*, "
                + "(select e.municipio from enderecos e where e.cliente_id = a.cliente_id order by e.principal desc, e.id limit 1) as cidade, "
                + "ur.data_recebimento as ur_data, ur.valor_total as ur_valor from a "
                + "left join lateral (select r.data_recebimento, r.valor_total from conta_receber_recebimento r "
                + "join conta_receber x on x.id = r.conta_receber_id where x.cliente_id = a.cliente_id and not r.estornada "
                + "order by r.data_recebimento desc, r.id desc limit 1) ur on true "
                + "order by " + coluna + (asc ? " asc" : " desc") + ", lower(a.nome) asc, a.cliente_id asc limit :limite offset :deslocamento";
        List<SituacaoClienteDTOs.Linha> itens = jdbc.sql(lista)
                .param("hoje", hoje).param("busca", padrao).param("buscaDigitos", padraoDigitos).param("somenteVencidos", somenteVencidos)
                .param("limite", tamanho).param("deslocamento", (long) pagina * tamanho)
                .query(SituacaoClienteService::linha).list();

        SituacaoClienteDTOs.Totais totais = jdbc.sql("with a as (" + AGREGADO + ") select coalesce(sum(aberto), 0) as total_aberto, "
                        + "coalesce(sum(vencido), 0) as total_vencido, count(*) filter (where vencido > 0) as com_atraso, count(*) as clientes from a")
                .param("hoje", hoje).param("busca", padrao).param("buscaDigitos", padraoDigitos).param("somenteVencidos", somenteVencidos)
                .query((rs, n) -> new SituacaoClienteDTOs.Totais(CalculoFinanceiro.arredondar(rs.getBigDecimal("total_aberto")),
                        CalculoFinanceiro.arredondar(rs.getBigDecimal("total_vencido")), rs.getLong("com_atraso"), rs.getLong("clientes"))).single();
        int paginas = (int) ((totais.quantidadeClientes() + tamanho - 1) / tamanho);
        return new SituacaoClienteDTOs.Geral(itens, pagina, tamanho, totais.quantidadeClientes(), paginas, totais);
    }

    private static SituacaoClienteDTOs.Linha linha(ResultSet rs, int n) throws SQLException {
        String cpf = rs.getString("cpf");
        String documento = cpf != null && !cpf.isBlank() ? cpf : rs.getString("documento");
        if (documento != null && documento.isBlank()) documento = null;
        LocalDate data = rs.getObject("ur_data", LocalDate.class);
        BigDecimal valor = rs.getBigDecimal("ur_valor");
        return new SituacaoClienteDTOs.Linha(new SituacaoClienteDTOs.Cliente(rs.getObject("cliente_id", UUID.class), rs.getString("nome"), documento, rs.getString("cidade")),
                CalculoFinanceiro.arredondar(rs.getBigDecimal("aberto")), CalculoFinanceiro.arredondar(rs.getBigDecimal("vencido")), rs.getInt("qtd"), rs.getInt("atraso"),
                data == null ? null : new SituacaoClienteDTOs.UltimoRecebimento(data, CalculoFinanceiro.arredondar(valor)));
    }

    // % e _ digitados valem como texto; "!" é o caractere de escape do like
    private static String padraoBusca(String busca) {
        if (busca == null || busca.isBlank()) return null;
        String termo = busca.trim().toLowerCase().replace("\u0000", "").replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + termo + "%";
    }
}
