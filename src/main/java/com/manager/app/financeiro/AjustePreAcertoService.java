package com.manager.app.financeiro;

import com.manager.app.acertoconsignacao.AcertoConsignacao;
import com.manager.app.financeiro.dto.ExcedenteDTOs;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

// opção B (decisão do usuário): na confirmação do acerto, título(s) ativo(s) do pré-acerto acima do acerto são reduzidos para max(acerto, recebido),
// somente com aceite explícito. A regra de cálculo está em ExcedenteAcertoCalculo; o excedente (recebido - acerto) é criado depois por ExcedenteAcertoService.
@Service
@RequiredArgsConstructor
public class AjustePreAcertoService {

    private final ContaReceberRepository contaReceberRepository;
    private final ContaReceberService contaReceberService;

    private static List<ContaReceber> doPreAcerto(List<ContaReceber> ativos) {
        return ativos.stream().filter(t -> t.getPreAcerto() != null).sorted(Comparator.comparing(ContaReceber::getId)).toList();
    }

    private static BigDecimal somar(List<ContaReceber> titulos, boolean recebido) {
        BigDecimal total = BigDecimal.ZERO;
        for (ContaReceber t : titulos) total = total.add(CalculoFinanceiro.arredondar(recebido ? t.getValorRecebido() : t.getValor()));
        return total;
    }

    // proposta (somente leitura) para o GET do acerto ainda não confirmado; null quando não há ajuste a propor
    public ExcedenteDTOs.AjusteProposto ajusteProposto(List<ContaReceber> ativosDaConsignacao, BigDecimal valorDoAcerto) {
        List<ContaReceber> titulos = doPreAcerto(ativosDaConsignacao);
        ExcedenteAcertoCalculo.Ajuste ajuste = ExcedenteAcertoCalculo.ajuste(somar(titulos, false), somar(titulos, true), valorDoAcerto);
        if (ajuste == null) return null;
        return new ExcedenteDTOs.AjusteProposto(ajuste.titulado(), ajuste.valorAcerto(), ajuste.recebido(), ajuste.valorNovo(), ajuste.reducao(), true,
                ExcedenteAcertoCalculo.perguntaDoAjuste(ajuste));
    }

    // chamada na confirmação do acerto (consignação e acerto já bloqueados): sem aceite a confirmação não segue (409 com a pergunta formal)
    @Transactional
    public String ajustarNaConfirmacao(AcertoConsignacao acerto, boolean aceite) {
        Long consignacaoId = acerto.getConsignacao().getId();
        List<ContaReceber> titulos = doPreAcerto(contaReceberRepository.findAtivosDaConsignacaoForUpdate(consignacaoId));
        ExcedenteAcertoCalculo.Ajuste ajuste = ExcedenteAcertoCalculo.ajuste(somar(titulos, false), somar(titulos, true), acerto.getValorTotalComDesconto());
        if (ajuste == null) return null;
        if (!aceite) throw new ObjetoEmUsoException(ExcedenteAcertoCalculo.perguntaDoAjuste(ajuste));
        List<BigDecimal> novos = ExcedenteAcertoCalculo.novosValores(titulos.stream().map(ContaReceber::getValor).toList(),
                titulos.stream().map(ContaReceber::getValorRecebido).toList(), ajuste.reducao());
        String contexto = "Acerto nº " + acerto.getId() + ": valor do acerto " + MovimentacaoBancoService.moeda(ajuste.valorAcerto())
                + ", recebido do pré-acerto " + MovimentacaoBancoService.moeda(ajuste.recebido()) + ".";
        int comLink = 0;
        for (int i = 0; i < titulos.size(); i++) {
            if (CalculoFinanceiro.arredondar(titulos.get(i).getValor()).compareTo(CalculoFinanceiro.arredondar(novos.get(i))) != 0) {
                if (contaReceberService.ajustarTituloDoPreAcerto(titulos.get(i), novos.get(i), contexto)) comLink++;
            }
        }
        return com.manager.app.infinitepay.LinkPagamentoCicloVida.avisoDoLote(comLink);
    }
}
