package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.AnexoDTO;
import com.manager.app.financeiro.dto.ContaPagarDetalheDTO;
import com.manager.app.financeiro.dto.ContaPagarResumoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ContaPagarMapper {

    private final FinanceiroMapper mapper;

    public ContaPagarResumoDTO resumo(ContaPagar c, LocalDate hoje) {
        return resumo(c, hoje, 0);
    }

    public ContaPagarResumoDTO resumo(ContaPagar c, LocalDate hoje, int quantidadeAnexos) {
        return new ContaPagarResumoDTO(c.getId(), c.getNumero(), c.getDescricao(), c.getFavorecido(), categoria(c.getCategoria()),
                c.getCentroCusto() == null ? null : new ContaPagarResumoDTO.Ref(c.getCentroCusto().getId(), c.getCentroCusto().getNome()),
                c.getFormaPrevista() == null ? null : new ContaPagarResumoDTO.Ref(c.getFormaPrevista().getId(), c.getFormaPrevista().getNome()),
                CalculoFinanceiro.arredondar(c.getValor()), CalculoFinanceiro.arredondar(c.getValorPago()),
                ContaPagarCalculo.valorAberto(c.getSituacao(), c.getValor(), c.getValorPago()), c.getEmissao(), c.getCompetencia(),
                c.getVencimento(), c.getDocumento(), c.getSituacao(),
                ContaPagarCalculo.situacaoExibicao(c.getSituacao(), c.getVencimento(), hoje), c.getGrupoParcelamentoId(),
                c.getParcelaNumero(), c.getParcelaTotal(), c.getCancelamentoMotivo(), c.getCancelamentoEm(), quantidadeAnexos);
    }

    public ContaPagarDetalheDTO detalhe(ContaPagar c, LocalDate hoje, List<ContaPagarBaixa> baixas, List<ContaPagar> parcelas,
                                        List<ContaPagarEvento> eventos, java.util.Map<Long, Long> movimentacoes, java.util.Map<Long, Integer> anexosPorTitulo,
                                        List<ContaPagarAnexo> anexos, ContaPagarDetalheDTO.RecorrenciaResumo recorrencia) {
        ContaPagarResumoDTO r = resumo(c, hoje, anexosPorTitulo.getOrDefault(c.getId(), 0));
        ContaPagarDetalheDTO.Cancelamento cancelamento = c.getCancelamentoEm() == null ? null
                : new ContaPagarDetalheDTO.Cancelamento(c.getCancelamentoMotivo(), c.getCancelamentoEm(), mapper.usuario(c.getCanceladoPor()));
        return new ContaPagarDetalheDTO(r.id(), r.numero(), r.descricao(), r.favorecido(), r.categoria(), r.centroCusto(), r.formaPrevista(),
                r.valor(), r.valorPago(), r.valorAberto(), r.emissao(), r.competencia(), r.vencimento(), r.documento(), c.getLinhaDigitavel(),
                c.getObservacao(), r.situacao(), r.situacaoExibicao(), r.grupoParcelamentoId(), r.parcelaNumero(), r.parcelaTotal(),
                c.getRecorrenciaId(), cancelamento, baixas.stream().map(b -> baixa(b, movimentacoes.get(b.getId()))).toList(),
                parcelas.stream().map(p -> resumo(p, hoje, anexosPorTitulo.getOrDefault(p.getId(), 0))).toList(),
                eventos.stream().map(e -> new ContaPagarDetalheDTO.Evento(e.getId(), e.getEvento(), e.getDetalhe(),
                        mapper.usuario(e.getUsuario()), e.getDataHora())).toList(),
                c.getCreatedAt(), c.getUpdatedAt(), mapper.usuario(c.getCreatedBy()), mapper.usuario(c.getUpdatedBy()), recorrencia, anexos.stream().map(this::anexo).toList());
    }

    public AnexoDTO anexo(ContaPagarAnexo a) {
        return new AnexoDTO(a.getId(), a.getTipo(), a.getNomeOriginal(), a.getTipoConteudo(), a.getTamanho(), a.getBaixa() == null ? null : a.getBaixa().getId(),
                a.getCreatedAt(), mapper.usuario(a.getCreatedBy()));
    }

    public ContaPagarDetalheDTO.Baixa baixa(ContaPagarBaixa b, Long movimentacaoId) {
        return new ContaPagarDetalheDTO.Baixa(b.getId(), b.getDataPagamento(), b.getValorPrincipal(), b.getJuros(), b.getMulta(), b.getDesconto(),
                b.getValorTotal(), new ContaPagarResumoDTO.Ref(b.getBanco().getId(), b.getBanco().getNome()),
                new ContaPagarResumoDTO.Ref(b.getFormaPagamento().getId(), b.getFormaPagamento().getNome()), b.getObservacao(),
                b.isEstornada(), b.getEstornoMotivo(), b.getEstornadaEm(), mapper.usuario(b.getEstornadaPor()), b.getCreatedAt(),
                mapper.usuario(b.getCreatedBy()), movimentacaoId);
    }

    private ContaPagarResumoDTO.CategoriaRef categoria(CategoriaDespesa c) {
        CategoriaDespesa pai = c.getPai();
        return new ContaPagarResumoDTO.CategoriaRef(c.getId(), c.getNome(), pai == null ? null : pai.getId(), pai == null ? null : pai.getNome());
    }
}
