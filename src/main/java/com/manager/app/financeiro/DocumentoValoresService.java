package com.manager.app.financeiro;

import com.manager.app.acertoconsignacao.AcertoConsignacao;
import com.manager.app.acertoconsignacao.AcertoConsignacaoRepository;
import com.manager.app.financeiro.dto.ContaReceberDetalheDTO;
import com.manager.app.preacerto.PreAcertoConsignacao;
import com.manager.app.preacerto.PreAcertoConsignacaoProdutoRepository;
import com.manager.app.preacerto.PreAcertoConsignacaoRepository;
import com.manager.app.preacerto.PreAcertoConsignacaoTotaisProjecao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

// valores do documento de consignação (pré-acerto ou acerto): vendido (produtos), comissão (desconto) e valor a pagar (vendido - comissão, nunca negativo).
// Única fonte desses números para o resumo financeiro, o detalhe do título e a aba Financeiro do cliente.
@Service
@RequiredArgsConstructor
public class DocumentoValoresService {

    public record Valores(BigDecimal valorVendido, BigDecimal valorComissao, BigDecimal valorAPagar) {
        public static Valores de(BigDecimal vendido, BigDecimal comissao) {
            BigDecimal v = CalculoFinanceiro.arredondar(vendido == null ? BigDecimal.ZERO : vendido);
            BigDecimal c = CalculoFinanceiro.arredondar(comissao == null ? BigDecimal.ZERO : comissao);
            BigDecimal pagar = v.subtract(c);
            return new Valores(v, c, pagar.signum() < 0 ? CalculoFinanceiro.arredondar(BigDecimal.ZERO) : pagar);
        }
    }

    private final PreAcertoConsignacaoRepository preAcertoRepository;
    private final PreAcertoConsignacaoProdutoRepository preAcertoProdutoRepository;
    private final AcertoConsignacaoRepository acertoRepository;

    public Valores doPreAcerto(PreAcertoConsignacao p) {
        BigDecimal soma = preAcertoProdutoRepository.buscarTotais(List.of(p.getId())).stream().findFirst()
                .map(PreAcertoConsignacaoTotaisProjecao::getValorTotal).orElse(BigDecimal.ZERO);
        return Valores.de(soma, p.getDesconto());
    }

    public Valores doAcerto(AcertoConsignacao a) {
        return Valores.de(a.getValorTotalProdutosAcertados(), a.getDesconto());
    }

    // bloco "consignacao" do detalhe do título; nulo quando o título não tem origem em pré-acerto/acerto
    public ContaReceberDetalheDTO.ConsignacaoOrigem origemDoTitulo(ContaReceber c) {
        if (c.getAcerto() != null) {
            Optional<AcertoConsignacao> a = acertoRepository.findById(c.getAcerto().getId());
            if (a.isPresent()) return montar("ACERTO", a.get().getId(), a.get().getConsignacao().getId(), doAcerto(a.get()), c);
        }
        if (c.getPreAcerto() != null) {
            Optional<PreAcertoConsignacao> p = preAcertoRepository.findById(c.getPreAcerto().getId());
            if (p.isPresent()) {
                Long consignacaoId = p.get().getConsignacao() == null ? null : p.get().getConsignacao().getId();
                return montar("PRE_ACERTO", p.get().getId(), consignacaoId, doPreAcerto(p.get()), c);
            }
        }
        return null;
    }

    private static ContaReceberDetalheDTO.ConsignacaoOrigem montar(String tipo, Long documentoId, Long consignacaoId, Valores v, ContaReceber c) {
        return new ContaReceberDetalheDTO.ConsignacaoOrigem(tipo, documentoId, consignacaoId, v.valorVendido(), v.valorComissao(), v.valorAPagar(),
                CalculoFinanceiro.arredondar(c.getValor()), c.getParcelaNumero(), c.getParcelaTotal());
    }
}
