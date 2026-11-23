package com.manager.app.infinitepay;

import com.manager.app.infinitepay.dto.InfinitePayDTOs;
import jakarta.validation.Valid;
import com.manager.app.infinitepay.dto.RevisaoDTOs;
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
    private static final String REVISAO_VER = "hasAuthority('INFINITEPAY_REVISAO_VISUALIZAR')";
    private static final String REVISAO_RESOLVER = "hasAuthority('INFINITEPAY_REVISAO_RESOLVER')";
    private static final String ALERTAS = "hasAnyAuthority('INFINITEPAY_REVISAO_VISUALIZAR', 'INFINITEPAY_GERENCIAR')";
    private static final String RECEBER = "hasAuthority('CONTA_RECEBER_RECEBER')";
    private static final String GERENCIA = "hasAuthority('CONTA_RECEBER_GERENCIAR')";

    private final InfinitePayConfiguracaoService configuracaoService;
    private final LinkPagamentoService linkService;
    private final InfinitePayAutomaticoService automaticoService;
    private final ReceptorClient receptorClient;
    private final BaixaAutomaticaService baixaService;
    private final RevisaoInfinitePayService revisaoService;
    private final VerificarPagamentoService verificarService;

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

    // busca a fila do receptor agora e processa o que chegou (mesma rotina do ciclo agendado; serializada com ele)
    @PostMapping("/infinitepay/fila/sincronizar")
    @PreAuthorize("hasAnyAuthority('INFINITEPAY_GERENCIAR', 'INFINITEPAY_REVISAO_RESOLVER')")
    public ResponseEntity<BaixaAutomaticaService.Resumo> sincronizar() {
        return ResponseEntity.ok(baixaService.sincronizar());
    }

    @GetMapping("/infinitepay/fila/estado")
    @PreAuthorize("hasAnyAuthority('INFINITEPAY_GERENCIAR', 'INFINITEPAY_REVISAO_VISUALIZAR')")
    public ResponseEntity<BaixaAutomaticaService.Estado> estadoDaFila() {
        return ResponseEntity.ok(baixaService.estado());
    }

    // ---------- fila de revisão da baixa automática ----------

    @GetMapping("/infinitepay/revisoes")
    @PreAuthorize(REVISAO_VER)
    public ResponseEntity<org.springframework.data.domain.Page<RevisaoDTOs.Resumo>> revisoes(
            @RequestParam(required = false) String status, @RequestParam(required = false) String motivo,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate de,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate ate,
            @RequestParam(required = false) Long tituloId, @RequestParam(required = false) java.util.UUID clienteId,
            @org.springframework.data.web.PageableDefault(size = 20, sort = "criadoEm", direction = org.springframework.data.domain.Sort.Direction.DESC)
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(revisaoService.listar(status, motivo, de, ate, tituloId, clienteId, pageable));
    }

    @GetMapping("/infinitepay/revisoes/{id}")
    @PreAuthorize(REVISAO_VER)
    public ResponseEntity<RevisaoDTOs.Detalhe> revisao(@PathVariable Long id) {
        return ResponseEntity.ok(revisaoService.detalhe(id));
    }

    @PostMapping("/infinitepay/revisoes/{id}/aplicar")
    @PreAuthorize(REVISAO_RESOLVER)
    public ResponseEntity<RevisaoDTOs.Detalhe> aplicar(@PathVariable Long id, @RequestBody(required = false) RevisaoDTOs.AplicarRequest request) {
        return ResponseEntity.ok(revisaoService.aplicar(id, request));
    }

    @PostMapping("/infinitepay/revisoes/{id}/ignorar")
    @PreAuthorize(REVISAO_RESOLVER)
    public ResponseEntity<RevisaoDTOs.Detalhe> ignorar(@PathVariable Long id, @RequestBody(required = false) RevisaoDTOs.IgnorarRequest request) {
        return ResponseEntity.ok(revisaoService.ignorar(id, request));
    }

    @PostMapping("/infinitepay/revisoes/{id}/reprocessar")
    @PreAuthorize(REVISAO_RESOLVER)
    public ResponseEntity<RevisaoDTOs.Detalhe> reprocessar(@PathVariable Long id) {
        return ResponseEntity.ok(revisaoService.reprocessar(id));
    }

    @GetMapping("/infinitepay/alertas")
    @PreAuthorize(ALERTAS)
    public ResponseEntity<RevisaoDTOs.Alertas> alertas() {
        return ResponseEntity.ok(verificarService.alertas());
    }

    @PostMapping("/infinitepay/verificar-pagamento")
    @PreAuthorize(RECEBER)
    public ResponseEntity<RevisaoDTOs.VerificarResposta> verificarPagamento(@RequestBody RevisaoDTOs.VerificarRequest request) {
        return ResponseEntity.ok(verificarService.verificar(request == null ? null : request.tituloId()));
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
