package com.manager.app.infinitepay.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// contrato da fila de revisão da baixa automática (docs/financeiro-infinitepay-fase3b-integracao-frontend.md, seções 7 a 11)
public final class RevisaoDTOs {

    private RevisaoDTOs() {
    }

    public record Resumo(Long id, LocalDateTime criadoEm, String status, String motivoCodigo, String motivoRotulo, String motivo, String evento, String orderNsu,
                         String transactionNsu, Long tituloId, Long tituloNumero, String clienteNome, BigDecimal valorLink, Long amountCentavos,
                         Long paidAmountCentavos, Long diferencaCentavos, String captureMethod, String meio, int tentativas, Long recebimentoId,
                         LocalDateTime resolvidoEm, String resolvidoPorNome, String resolucao) {
    }

    public record TituloResumo(Long id, Long numero, String situacao, BigDecimal valor, BigDecimal valorRecebido, BigDecimal saldoEmAberto, java.util.UUID clienteId,
                               String clienteNome) {
    }

    public record LinkResumo(Long id, String modo, String status, BigDecimal valor, Long bancoId, String bancoNome, String transactionNsu) {
    }

    // detalhe = todos os campos do resumo (achatados) + payload bruto, ações, exigência de banco/forma, título e link
    public record Detalhe(Long id, LocalDateTime criadoEm, String status, String motivoCodigo, String motivoRotulo, String motivo, String evento, String orderNsu,
                          String transactionNsu, Long tituloId, Long tituloNumero, String clienteNome, BigDecimal valorLink, Long amountCentavos,
                          Long paidAmountCentavos, Long diferencaCentavos, String captureMethod, String meio, int tentativas, Long recebimentoId,
                          LocalDateTime resolvidoEm, String resolvidoPorNome, String resolucao,
                          String payloadBruto, List<String> acoesPermitidas, boolean exigeBancoForma, TituloResumo titulo, LinkResumo link, String aviso, boolean baixado) {
        public static Detalhe de(Resumo r, String payloadBruto, List<String> acoes, boolean exigeBancoForma, TituloResumo titulo, LinkResumo link) {
            return new Detalhe(r.id(), r.criadoEm(), r.status(), r.motivoCodigo(), r.motivoRotulo(), r.motivo(), r.evento(), r.orderNsu(), r.transactionNsu(), r.tituloId(),
                    r.tituloNumero(), r.clienteNome(), r.valorLink(), r.amountCentavos(), r.paidAmountCentavos(), r.diferencaCentavos(), r.captureMethod(), r.meio(),
                    r.tentativas(), r.recebimentoId(), r.resolvidoEm(), r.resolvidoPorNome(), r.resolucao(), payloadBruto, acoes, exigeBancoForma, titulo, link, null, r.recebimentoId() != null);
        }

        // aviso formal opcional (hoje só no reprocessamento que não pôde confirmar o pagamento)
        public Detalhe comAviso(String novoAviso) {
            return new Detalhe(id, criadoEm, status, motivoCodigo, motivoRotulo, motivo, evento, orderNsu, transactionNsu, tituloId, tituloNumero, clienteNome, valorLink,
                    amountCentavos, paidAmountCentavos, diferencaCentavos, captureMethod, meio, tentativas, recebimentoId, resolvidoEm, resolvidoPorNome, resolucao,
                    payloadBruto, acoesPermitidas, exigeBancoForma, titulo, link, novoAviso, baixado);
        }
    }

    // todos opcionais: sem corpo usa banco do link, forma do meio de captura e a data do próprio pagamento
    public record AplicarRequest(Long bancoId, Long formaPagamentoId, Boolean confirmarValorMenor) {
    }

    public record IgnorarRequest(String motivo) {
    }

    // reprocessar exige confirmação explícita
    public record ReprocessarRequest(Boolean confirmar) {
    }

    public record VerificarRequest(Long tituloId) {
    }

    public record VerificarResposta(String resultado, String mensagem, Object titulo, Long recebimentoId) {
    }

    public record Alerta(String codigo, String gravidade, String mensagem, long quantidade, String rota) {
    }

    public record Alertas(int total, List<Alerta> alertas) {
    }
}
