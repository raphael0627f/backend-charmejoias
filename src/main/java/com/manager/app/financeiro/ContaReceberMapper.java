package com.manager.app.financeiro;

import com.manager.app.cliente.Cliente;
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
        return new ContaReceberResumoDTO(c.getId(), c.getNumero(), c.getDescricao(), cliente(c.getCliente()), categoria(c.getCategoria()),
                c.getCentroCusto() == null ? null : new ContaReceberResumoDTO.Ref(c.getCentroCusto().getId(), c.getCentroCusto().getNome()),
                c.getFormaPrevista() == null ? null : new ContaReceberResumoDTO.Ref(c.getFormaPrevista().getId(), c.getFormaPrevista().getNome()),
                CalculoFinanceiro.arredondar(c.getValor()), CalculoFinanceiro.arredondar(c.getValorRecebido()),
                ContaReceberCalculo.valorAberto(c.getSituacao(), c.getValor(), c.getValorRecebido()), c.getEmissao(), c.getCompetencia(),
                c.getVencimento(), c.getDocumento(), c.getSituacao(),
                ContaReceberCalculo.situacaoExibicao(c.getSituacao(), c.getVencimento(), hoje), c.getGrupoParcelamentoId(),
                c.getParcelaNumero(), c.getParcelaTotal(), c.getCancelamentoMotivo(), c.getCancelamentoEm(), 0);
    }

    public ContaReceberDetalheDTO detalhe(ContaReceber c, LocalDate hoje, List<ContaReceber> parcelas, List<ContaReceberEvento> eventos) {
        ContaReceberResumoDTO r = resumo(c, hoje);
        ContaReceberDetalheDTO.Cancelamento cancelamento = c.getCancelamentoEm() == null ? null
                : new ContaReceberDetalheDTO.Cancelamento(c.getCancelamentoMotivo(), c.getCancelamentoEm(), mapper.usuario(c.getCanceladoPor()));
        return new ContaReceberDetalheDTO(r.id(), r.numero(), r.descricao(), r.cliente(), r.categoria(), r.centroCusto(), r.formaPrevista(),
                r.valor(), r.valorRecebido(), r.valorAberto(), r.emissao(), r.competencia(), r.vencimento(), r.documento(), c.getObservacao(),
                r.situacao(), r.situacaoExibicao(), r.grupoParcelamentoId(), r.parcelaNumero(), r.parcelaTotal(), cancelamento, List.of(),
                parcelas.stream().map(p -> resumo(p, hoje)).toList(),
                eventos.stream().map(e -> new ContaReceberDetalheDTO.Evento(e.getId(), e.getEvento(), e.getDetalhe(),
                        mapper.usuario(e.getUsuario()), e.getDataHora())).toList(),
                c.getCreatedAt(), c.getUpdatedAt(), mapper.usuario(c.getCreatedBy()), mapper.usuario(c.getUpdatedBy()), List.of());
    }

    // documento do cliente: CPF do cadastro ou, na falta dele, o campo documento
    public static String documentoDe(Cliente c) {
        if (c.getCpf() != null && !c.getCpf().isBlank()) return c.getCpf();
        return c.getDocumento() == null || c.getDocumento().isBlank() ? null : c.getDocumento();
    }

    private ContaReceberResumoDTO.ClienteRef cliente(Cliente c) {
        return new ContaReceberResumoDTO.ClienteRef(c.getId(), c.getNome(), documentoDe(c));
    }

    private ContaReceberResumoDTO.CategoriaRef categoria(CategoriaReceita c) {
        CategoriaReceita pai = c.getPai();
        return new ContaReceberResumoDTO.CategoriaRef(c.getId(), c.getNome(), pai == null ? null : pai.getId(), pai == null ? null : pai.getNome());
    }
}
