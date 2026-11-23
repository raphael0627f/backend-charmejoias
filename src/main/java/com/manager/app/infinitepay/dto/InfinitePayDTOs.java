package com.manager.app.infinitepay.dto;

import com.manager.app.usuario.dto.UsuarioResumoDTO;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// corpos e respostas da integração com a InfinitePay (link de pagamento manual); a chave da API NUNCA aparece em resposta
public final class InfinitePayDTOs {

    private InfinitePayDTOs() {
    }

    public record ConfiguracaoResponse(String handle, boolean chaveConfigurada, String chaveFinal, int validadeDias, boolean pagamentoUnico,
                                       int taxaParcelamentoAssumida, boolean pixHabilitado, BigDecimal descontoPix, BigDecimal descontoCartao,
                                       boolean exigeEnderecoEntrega, String textoWhatsapp, String textoEmail, boolean envioEmailHabilitado,
                                       LocalDateTime atualizadoEm, UsuarioResumoDTO atualizadoPor) {
    }

    // padrões do diálogo de geração (acessível a quem gera links); sem InfiniteTag, chave nem textos
    public record PadroesResponse(boolean configurada, int validadeDias, boolean pagamentoUnico, int taxaParcelamentoAssumida, boolean pixHabilitado,
                                  BigDecimal descontoPix, BigDecimal descontoCartao, boolean exigeEnderecoEntrega, boolean envioEmailHabilitado,
                                  boolean baixaAutomaticaAtiva, boolean prontoParaAutomatico, boolean modoAutomaticoPadrao, Ref bancoPadrao) {
    }

    // todos os campos opcionais: ausente mantém o valor atual; chave vazia/ausente mantém a chave salva
    public record ConfiguracaoRequest(String handle, String chave, Boolean removerChave, Integer validadeDias, Boolean pagamentoUnico,
                                      Integer taxaParcelamentoAssumida, Boolean pixHabilitado, BigDecimal descontoPix, BigDecimal descontoCartao,
                                      Boolean exigeEnderecoEntrega, String textoWhatsapp, String textoEmail) {
    }

    public record ClienteRequest(String nome, String email, String telefone) {
    }

    // validade ausente/nula = sem validade; os demais campos ausentes usam o padrão da configuração
    public record LinkRequest(String descricao, BigDecimal valor, LocalDate validade, Boolean pagamentoUnico, Integer taxaParcelamentoAssumida,
                              Boolean pixHabilitado, BigDecimal descontoPix, BigDecimal descontoCartao, Boolean exigeEnderecoEntrega,
                              ClienteRequest cliente, String modo, Long bancoId) {
        // compatibilidade: pedido sem modo (MANUAL ou o padrao da configuracao) e sem banco
        public LinkRequest(String descricao, BigDecimal valor, LocalDate validade, Boolean pagamentoUnico, Integer taxaParcelamentoAssumida,
                           Boolean pixHabilitado, BigDecimal descontoPix, BigDecimal descontoCartao, Boolean exigeEnderecoEntrega, ClienteRequest cliente) {
            this(descricao, valor, validade, pagamentoUnico, taxaParcelamentoAssumida, pixHabilitado, descontoPix, descontoCartao, exigeEnderecoEntrega, cliente, null, null);
        }
    }

    public record ClienteResponse(String nome, String email, String telefone) {
    }

    public record LinkResponse(Long id, Long contaReceberId, Long tituloNumero, String orderNsu, String url, BigDecimal valor, String descricao,
                               LocalDate validade, boolean expirado, boolean pagamentoUnico, int taxaParcelamentoAssumida, boolean pixHabilitado,
                               BigDecimal descontoPix, BigDecimal descontoCartao, boolean exigeEnderecoEntrega, ClienteResponse cliente,
                               String status, Long recebimentoId, LocalDateTime criadoEm, UsuarioResumoDTO criadoPor, boolean reaproveitado,
                               String textoWhatsapp, String textoEmail,
                               String modo, Long bancoId, String bancoNome, String transactionNsu) {
    }

    public record MarcarPagoRequest(Long recebimentoId,
                                    @Size(max = 500, message = "A observação deve ter no máximo 500 caracteres.") String observacao) {
    }

    public record EnviarEmailRequest(List<String> para,
                                     @Size(max = 200, message = "O assunto deve ter no máximo 200 caracteres.") String assunto,
                                     @Size(max = 2000, message = "A mensagem deve ter no máximo 2000 caracteres.") String mensagem) {
    }

    public record EnviarEmailResponse(List<String> destinatarios) {
    }

    public record Ref(Long id, String nome) {
    }

    // configuracao da baixa automatica (receptor na nuvem). Os segredos NUNCA sao devolvidos: apenas se existem e os 4 ultimos caracteres.
    public record AutomaticoResponse(boolean baixaAutomaticaAtiva, boolean modoAutomaticoPadrao, String receptorUrl, boolean tokenEntradaConfigurado,
                                     String tokenEntradaFinal, boolean segredoErpConfigurado, String segredoErpFinal, int intervaloFilaMinutos,
                                     Ref bancoPadrao, Ref formaPix, Ref formaCartao, boolean prontoParaAutomatico, List<String> pendencias,
                                     LocalDateTime atualizadoEm, UsuarioResumoDTO atualizadoPor) {
    }

    // todos opcionais: ausente mantem o valor atual; segredo vazio/ausente mantem o salvo. Para limpar banco/forma use o id 0.
    public record AutomaticoRequest(Boolean baixaAutomaticaAtiva, Boolean modoAutomaticoPadrao, String receptorUrl, String tokenEntrada, String segredoErp,
                                    Integer intervaloFilaMinutos, Long bancoPadraoId, Long formaPixId, Long formaCartaoId) {
    }

    public record ReceptorSaude(boolean ok, Long pendentes, String maisAntigoEm, String versao) {
    }
}
