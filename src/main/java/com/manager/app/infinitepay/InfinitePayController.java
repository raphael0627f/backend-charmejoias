package com.manager.app.infinitepay;

import com.manager.app.infinitepay.dto.InfinitePayDTOs;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// configuração (só INFINITEPAY_GERENCIAR, perfil ADMIN: guarda a chave), padrões de geração e links de pagamento dos títulos a receber.
// Sem @Valid nos corpos que o interceptor do Financeiro precisa deixar passar: a autorização (403) é avaliada antes da leitura do corpo.
@RestController
@RequiredArgsConstructor
public class InfinitePayController {

    private static final String CONFIGURACAO = "hasAuthority('INFINITEPAY_GERENCIAR')";
    private static final String PADROES = "hasAnyAuthority('INFINITEPAY_GERENCIAR', 'CONTA_RECEBER_VISUALIZAR', 'CONTA_RECEBER_GERENCIAR', 'CONTA_RECEBER_RECEBER')";
    private static final String CONSULTA = "hasAnyAuthority('CONTA_RECEBER_VISUALIZAR', 'CONTA_RECEBER_GERENCIAR', 'CONTA_RECEBER_RECEBER')";
    private static final String GERENCIA = "hasAuthority('CONTA_RECEBER_GERENCIAR')";

    private final InfinitePayConfiguracaoService configuracaoService;
    private final LinkPagamentoService linkService;
    private final InfinitePayAutomaticoService automaticoService;
    private final ReceptorClient receptorClient;

    @GetMapping("/infinitepay/configuracao")
    @PreAuthorize(CONFIGURACAO)
    public ResponseEntity<InfinitePayDTOs.ConfiguracaoResponse> obterConfiguracao() {
        return ResponseEntity.ok(configuracaoService.obter());
    }

    @PutMapping("/infinitepay/configuracao")
    @PreAuthorize(CONFIGURACAO)
    public ResponseEntity<InfinitePayDTOs.ConfiguracaoResponse> salvarConfiguracao(@RequestBody(required = false) InfinitePayDTOs.ConfiguracaoRequest request) {
        return ResponseEntity.ok(configuracaoService.salvar(request));
    }

    // baixa automática (fase 3B): receptor na nuvem, interruptor, intervalo e mapeamento banco/forma. Segredos nunca são devolvidos.
    @GetMapping("/infinitepay/automatico")
    @PreAuthorize(CONFIGURACAO)
    public ResponseEntity<InfinitePayDTOs.AutomaticoResponse> obterAutomatico() {
        return ResponseEntity.ok(automaticoService.obter());
    }

    @PutMapping("/infinitepay/automatico")
    @PreAuthorize(CONFIGURACAO)
    public ResponseEntity<InfinitePayDTOs.AutomaticoResponse> salvarAutomatico(@RequestBody(required = false) InfinitePayDTOs.AutomaticoRequest request) {
        return ResponseEntity.ok(automaticoService.salvar(request));
    }

    // consulta a saúde do receptor (botão "Testar receptor")
    @PostMapping("/infinitepay/receptor/testar")
    @PreAuthorize(CONFIGURACAO)
    public ResponseEntity<InfinitePayDTOs.ReceptorSaude> testarReceptor() {
        return ResponseEntity.ok(receptorClient.saude());
    }

    @GetMapping("/infinitepay/padroes")
    @PreAuthorize(PADROES)
    public ResponseEntity<InfinitePayDTOs.PadroesResponse> padroes() {
        return ResponseEntity.ok(configuracaoService.padroes());
    }

    @GetMapping("/contas-receber/{id}/links-pagamento")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<List<InfinitePayDTOs.LinkResponse>> listar(@PathVariable Long id) {
        return ResponseEntity.ok(linkService.listar(id));
    }

    @PostMapping("/contas-receber/{id}/links-pagamento")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<InfinitePayDTOs.LinkResponse> gerar(@PathVariable Long id, @RequestBody(required = false) InfinitePayDTOs.LinkRequest request) {
        LinkPagamentoService.Resultado resultado = linkService.gerar(id, request);
        return ResponseEntity.status(resultado.reaproveitado() ? HttpStatus.OK : HttpStatus.CREATED).body(resultado.link());
    }

    @PostMapping("/links-pagamento/{id}/marcar-pago")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<InfinitePayDTOs.LinkResponse> marcarPago(@PathVariable Long id, @RequestBody(required = false) @Valid InfinitePayDTOs.MarcarPagoRequest request) {
        return ResponseEntity.ok(linkService.marcarPago(id, request));
    }

    @PostMapping("/links-pagamento/{id}/enviar-email")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<InfinitePayDTOs.EnviarEmailResponse> enviarEmail(@PathVariable Long id, @RequestBody(required = false) @Valid InfinitePayDTOs.EnviarEmailRequest request) {
        return ResponseEntity.ok(linkService.enviarEmail(id, request));
    }
}
