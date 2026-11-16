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
                                  BigDecimal descontoPix, BigDecimal descontoCartao, boolean exigeEnderecoEntrega, boolean envioEmailHabilitado) {
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
                              ClienteRequest cliente) {
    }

    public record ClienteResponse(String nome, String email, String telefone) {
    }

    public record LinkResponse(Long id, Long contaReceberId, Long tituloNumero, String orderNsu, String url, BigDecimal valor, String descricao,
                               LocalDate validade, boolean expirado, boolean pagamentoUnico, int taxaParcelamentoAssumida, boolean pixHabilitado,
                               BigDecimal descontoPix, BigDecimal descontoCartao, boolean exigeEnderecoEntrega, ClienteResponse cliente,
                               String status, Long recebimentoId, LocalDateTime criadoEm, UsuarioResumoDTO criadoPor, boolean reaproveitado,
                               String textoWhatsapp, String textoEmail) {
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
}
