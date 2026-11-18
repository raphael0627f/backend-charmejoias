package com.manager.app.financeiro;

import com.manager.app.cliente.Cliente;
import com.manager.app.financeiro.dto.ContaReceberAnexoDTO;
import com.manager.app.financeiro.dto.ContaReceberDetalheDTO;
import com.manager.app.financeiro.dto.ContaReceberResumoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ContaReceberMapper {

    private final FinanceiroMapper mapper;

    public ContaReceberResumoDTO resumo(ContaReceber c, LocalDate hoje) {
        return resumo(c, hoje, 0);
    }

    public ContaReceberResumoDTO resumo(ContaReceber c, LocalDate hoje, int quantidadeAnexos) {
        return new ContaReceberResumoDTO(c.getId(), c.getNumero(), c.getDescricao(), cliente(c.getCliente()), c.getPagadorNome(),
                c.getPagadorDocumento(), pagadorExibicao(c), c.getCliente() == null ? "AVULSO" : "CLIENTE", origem(c), categoria(c.getCategoria()),
                c.getCentroCusto() == null ? null : new ContaReceberResumoDTO.Ref(c.getCentroCusto().getId(), c.getCentroCusto().getNome()),
                c.getFormaPrevista() == null ? null : new ContaReceberResumoDTO.Ref(c.getFormaPrevista().getId(), c.getFormaPrevista().getNome()),
                CalculoFinanceiro.arredondar(c.getValor()), CalculoFinanceiro.arredondar(c.getValorRecebido()),
                ContaReceberCalculo.valorAberto(c.getSituacao(), c.getValor(), c.getValorRecebido()), c.getEmissao(), c.getCompetencia(),
                c.getVencimento(), c.getDocumento(), c.getSituacao(),
                ContaReceberCalculo.situacaoExibicao(c.getSituacao(), c.getVencimento(), hoje), c.getGrupoParcelamentoId(),
                c.getParcelaNumero(), c.getParcelaTotal(), c.getCancelamentoMotivo(), c.getCancelamentoEm(), quantidadeAnexos);
    }

    public ContaReceberDetalheDTO detalhe(ContaReceber c, LocalDate hoje, List<ContaReceber> parcelas, List<ContaReceberEvento> eventos,
                                          List<ContaReceberRecebimento> recebimentos, java.util.Map<Long, Long> movimentacoes,
                                          java.util.Map<Long, Integer> anexosPorTitulo, List<ContaReceberAnexo> anexos) {
        ContaReceberResumoDTO r = resumo(c, hoje, anexosPorTitulo.getOrDefault(c.getId(), 0));
        ContaReceberDetalheDTO.Cancelamento cancelamento = c.getCancelamentoEm() == null ? null
                : new ContaReceberDetalheDTO.Cancelamento(c.getCancelamentoMotivo(), c.getCancelamentoEm(), mapper.usuario(c.getCanceladoPor()));
        return new ContaReceberDetalheDTO(r.id(), r.numero(), r.descricao(), r.cliente(), r.pagadorNome(), r.pagadorDocumento(), r.pagadorExibicao(), r.tipoPagador(), r.origem(), r.categoria(), r.centroCusto(), r.formaPrevista(),
                r.valor(), r.valorRecebido(), r.valorAberto(), r.emissao(), r.competencia(), r.vencimento(), r.documento(), c.getObservacao(),
                r.situacao(), r.situacaoExibicao(), r.grupoParcelamentoId(), r.parcelaNumero(), r.parcelaTotal(), cancelamento,
                c.getCancelamentoMotivo(), c.getCancelamentoEm(), cancelamento == null ? null : cancelamento.por(),
                recebimentos.stream().map(b -> recebimento(b, movimentacoes.get(b.getId()))).toList(),
                parcelas.stream().map(p -> resumo(p, hoje, anexosPorTitulo.getOrDefault(p.getId(), 0))).toList(),
                eventos.stream().map(e -> new ContaReceberDetalheDTO.Evento(e.getId(), e.getEvento(), e.getDetalhe(),
                        mapper.usuario(e.getUsuario()), e.getDataHora())).toList(),
                c.getCreatedAt(), c.getUpdatedAt(), mapper.usuario(c.getCreatedBy()), mapper.usuario(c.getUpdatedBy()), anexos.stream().map(this::anexo).toList(), null);
    }

    public ContaReceberAnexoDTO anexo(ContaReceberAnexo a) {
        return new ContaReceberAnexoDTO(a.getId(), a.getTipo(), a.getNomeOriginal(), a.getTipoConteudo(), a.getTamanho(),
                a.getRecebimento() == null ? null : a.getRecebimento().getId(), a.getCreatedAt(), mapper.usuario(a.getCreatedBy()));
    }

    public ContaReceberDetalheDTO.Recebimento recebimento(ContaReceberRecebimento b, Long movimentacaoId) {
        return new ContaReceberDetalheDTO.Recebimento(b.getId(), b.getDataRecebimento(), b.getValorPrincipal(), b.getJuros(), b.getMulta(), b.getDesconto(),
                b.getValorTotal(), b.isCredito() ? null : new ContaReceberResumoDTO.Ref(b.getBanco().getId(), b.getBanco().getNome()),
                b.isCredito() ? new ContaReceberResumoDTO.Ref(null, "Crédito do cliente") : new ContaReceberResumoDTO.Ref(b.getFormaPagamento().getId(), b.getFormaPagamento().getNome()), b.getObservacao(),
                b.isEstornada() ? "ESTORNADO" : "ATIVO", b.isEstornada(), b.getEstornoMotivo(), b.getEstornadaEm(), mapper.usuario(b.getEstornadaPor()),
                b.getCreatedAt(), mapper.usuario(b.getCreatedBy()), movimentacaoId, b.getOrigem().name());
    }

    // documento do cliente: CPF do cadastro ou, na falta dele, o campo documento
    public static String documentoDe(Cliente c) {
        if (c.getCpf() != null && !c.getCpf().isBlank()) return c.getCpf();
        return c.getDocumento() == null || c.getDocumento().isBlank() ? null : c.getDocumento();
    }

    // origem de consignação (só os ids; as relações são preguiçosas e não são carregadas)
    public static ContaReceberResumoDTO.Origem origem(ContaReceber c) {
        Long consignacaoId = c.getConsignacao() == null ? null : c.getConsignacao().getId();
        if (c.getPreAcerto() != null) return new ContaReceberResumoDTO.Origem("PRE_ACERTO", c.getPreAcerto().getId(), consignacaoId);
        if (c.getAcerto() != null && consignacaoId != null) return new ContaReceberResumoDTO.Origem("ACERTO", c.getAcerto().getId(), consignacaoId);
        return null;
    }

    // nome de exibição: cliente ou pagador avulso
    public static String pagadorExibicao(ContaReceber c) {
        return ContaReceberCalculo.pagadorExibicao(c.getCliente() == null ? null : c.getCliente().getNome(), c.getPagadorNome());
    }

    private ContaReceberResumoDTO.ClienteRef cliente(Cliente c) {
        if (c == null) return null;
        return new ContaReceberResumoDTO.ClienteRef(c.getId(), c.getCodigo(), c.getNome(), documentoDe(c));
    }

    private ContaReceberResumoDTO.CategoriaRef categoria(CategoriaReceita c) {
        CategoriaReceita pai = c.getPai();
        return new ContaReceberResumoDTO.CategoriaRef(c.getId(), c.getNome(), pai == null ? null : pai.getId(), pai == null ? null : pai.getNome());
    }
}
